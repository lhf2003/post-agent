# script/content_transform.py
# -*- coding: utf-8 -*-
from pathlib import Path
from playwright.sync_api import sync_playwright
from jinja2 import Environment, FileSystemLoader
import base64, sys, argparse

# 确保控制台输出支持 UTF-8（Windows 兼容）
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

# ① 定位目录
SCRIPT_DIR = Path(__file__).resolve().parent  # .../script
ROOT_DIR = SCRIPT_DIR.parent  # .../project
FONT_DIR = ROOT_DIR / "fonts"
OUT_DIR = ROOT_DIR / "out"
OUT_DIR.mkdir(exist_ok=True)

# ② 字体配置
FONT_CONFIG = {
    "font_b": FONT_DIR / "HarmonyOS_Sans_SC_Bold.ttf",
    "font_r": FONT_DIR / "HarmonyOS_Sans_SC_Regular.ttf",
}

# ③ 校验字体是否存在
for f in (FONT_CONFIG["font_b"], FONT_CONFIG["font_r"]):
    if not f.exists():
        print(f"❌ 字体不存在：{f}\n请把字体文件放进 {FONT_DIR}")
        sys.exit(1)


# ④ 把字体转 base64
def to_b64(path):
    return "data:font/truetype;base64," + base64.b64encode(path.read_bytes()).decode()


def build_html(content="内容文本", decor_emoji=None, decor_position="bottom-right"):
    """构建HTML，用于显示内容文本，处理\n\n作为段落分隔"""
    # 将\n\n分割成段落，过滤空段落
    # 段落内的单个\n需要转换为<br>标签以便在HTML中换行
    paragraphs = []
    for p in content.split('\n\n'):
        p = p.strip()
        if p:
            # 将段落内的单个\n转换为<br>标签
            paragraphs.append(p.replace('\n', '<br>'))
    
    env = Environment(loader=FileSystemLoader(SCRIPT_DIR))
    tpl = env.get_template("content_template.html")
    cfg = {
        "font_b": to_b64(FONT_CONFIG["font_b"]),
        "font_r": to_b64(FONT_CONFIG["font_r"]),
        "title": "内容图片",
        "paragraphs": paragraphs,
        "decor_emoji": decor_emoji,
        "decor_position": decor_position
    }
    return tpl.render(**cfg)


def measure_content_height(page, html, width=1080, height=1440):
    """测量内容区域的实际高度（使用已存在的page对象）"""
    page.set_content(html)
    page.wait_for_load_state("networkidle")
    # 测量 .main-content 的实际高度
    content_height = page.evaluate("""
        () => {
            const content = document.querySelector('.main-content');
            if (!content) return 0;
            return content.offsetHeight;
        }
    """)
    return content_height


def split_content_to_fit(page, paragraphs, max_height=1240):
    """将段落列表分割，使内容适合一张图片（使用已存在的page对象）
    返回: (当前页段落列表, 剩余段落列表)
    """
    if not paragraphs:
        return [], []
    
    # 逐步添加段落，直到超出高度
    current_paragraphs = []
    
    for i, para in enumerate(paragraphs):
        # 先检查单个段落是否超出高度
        single_para_html = build_html_from_paragraphs([para])
        single_para_height = measure_content_height(page, single_para_html)
        
        if single_para_height > max_height:
            # 单个段落就超出高度，需要分割段落
            # 尝试按 <br> 标签分割
            if '<br>' in para:
                parts = para.split('<br>')
                # 尝试逐步添加行，直到超出
                current_part = []
                for j, part in enumerate(parts):
                    test_para = '<br>'.join(current_part + [part])
                    test_html = build_html_from_paragraphs([test_para])
                    test_height = measure_content_height(page, test_html)
                    
                    if test_height <= max_height:
                        current_part.append(part)
                    else:
                        # 超出高度，停止
                        if current_part:
                            # 有部分内容可以放入
                            current_paragraphs.append('<br>'.join(current_part))
                            # 剩余部分重新组合成一个段落（过滤空字符串）
                            remaining_parts = [p for p in parts[j:] if p.strip()]
                            if remaining_parts:
                                remaining_para = '<br>'.join(remaining_parts)
                                remaining_paragraphs = [remaining_para] + paragraphs[i+1:]
                            else:
                                remaining_paragraphs = paragraphs[i+1:]
                        else:
                            # 连一行都放不下，强制放入（避免死循环）
                            current_paragraphs.append(part)
                            # 剩余部分（过滤空字符串）
                            remaining_parts = [p for p in parts[j+1:] if p.strip()]
                            if remaining_parts:
                                remaining_para = '<br>'.join(remaining_parts)
                                remaining_paragraphs = [remaining_para] + paragraphs[i+1:]
                            else:
                                remaining_paragraphs = paragraphs[i+1:]
                        break
                else:
                    # 所有行都能放入
                    current_paragraphs.append(para)
                    remaining_paragraphs = paragraphs[i+1:]
            else:
                # 没有 <br> 标签，按字符数粗略分割（不精确，但能避免死循环）
                # 估算：每个字符大约占用 45px 宽度，每行约 20 个字符，每行高度约 81px
                # 可用高度约 1240px，约可放 15 行，约 300 个字符
                char_per_line = 20
                lines_per_page = max_height // 81  # 81 = 45 * 1.8 (line-height)
                max_chars = char_per_line * lines_per_page
                
                if len(para) > max_chars:
                    # 段落太长，分割（确保至少分割一部分，避免死循环）
                    split_pos = max(max_chars, len(para) // 2)  # 至少分割一半
                    current_para = para[:split_pos]
                    remaining_para = para[split_pos:].strip()
                    if current_para.strip():
                        current_paragraphs.append(current_para)
                    if remaining_para:
                        remaining_paragraphs = [remaining_para] + paragraphs[i+1:]
                    else:
                        remaining_paragraphs = paragraphs[i+1:]
                else:
                    # 段落可以放入，但可能与其他段落一起会超出
                    test_paragraphs = current_paragraphs + [para]
                    test_html = build_html_from_paragraphs(test_paragraphs)
                    test_height = measure_content_height(page, test_html)
                    
                    if test_height <= max_height:
                        current_paragraphs.append(para)
                        remaining_paragraphs = paragraphs[i+1:]
                    else:
                        # 超出高度，当前段落放到下一页
                        remaining_paragraphs = paragraphs[i:]
            break
        else:
            # 单个段落不超出，尝试添加到当前页
            test_paragraphs = current_paragraphs + [para]
            test_html = build_html_from_paragraphs(test_paragraphs)
            test_height = measure_content_height(page, test_html)
            
            if test_height <= max_height:
                # 可以添加，继续
                current_paragraphs.append(para)
            else:
                # 超出高度，停止添加，当前段落放到下一页
                remaining_paragraphs = paragraphs[i:]
                break
    else:
        # 所有段落都能放入
        remaining_paragraphs = []
    
    # 过滤空段落
    current_paragraphs = [p for p in current_paragraphs if p.strip()]
    remaining_paragraphs = [p for p in remaining_paragraphs if p.strip()]
    
    return current_paragraphs, remaining_paragraphs


def build_html_from_paragraphs(paragraphs, decor_emoji=None, decor_position="bottom-right"):
    """从段落列表构建HTML"""
    env = Environment(loader=FileSystemLoader(SCRIPT_DIR))
    tpl = env.get_template("content_template.html")
    cfg = {
        "font_b": to_b64(FONT_CONFIG["font_b"]),
        "font_r": to_b64(FONT_CONFIG["font_r"]),
        "title": "内容图片",
        "paragraphs": paragraphs,
        "decor_emoji": decor_emoji,
        "decor_position": decor_position
    }
    return tpl.render(**cfg)


def html_to_pic(page, html, save_path, width=1080, height=1440):
    """使用已存在的page对象生成图片"""
    page.set_content(html)
    page.wait_for_load_state("networkidle")
    # 固定尺寸截图，保持小红书封面格式 3:4 宽高比（1080 × 1440）
    page.screenshot(path=save_path)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="生成内容图片工具")
    parser.add_argument("--content", required=True,
                        help="内容文本")
    parser.add_argument("--name", required=True,
                        help="输出文件名（不包含扩展名）")
    parser.add_argument("--out", default=str(OUT_DIR),
                        help="输出目录，支持相对路径与绝对路径（默认：项目 out 目录）")

    args = parser.parse_args()

    # 处理转义字符：将字符串中的 \n 转换为真正的换行符
    # 将字面字符串 \n 替换为真正的换行符
    content = args.content.replace('\\n', '\n')

    # 解析输出目录
    out_dir = Path(args.out)
    if not out_dir.is_absolute():
        out_dir = ROOT_DIR / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    # 将内容转换为段落列表
    paragraphs = []
    for p in content.split('\n\n'):
        p = p.strip()
        if p:
            # 将段落内的单个\n转换为<br>标签
            paragraphs.append(p.replace('\n', '<br>'))

    # 可用高度：图片高度1440 - 上下padding 200 = 1240
    MAX_CONTENT_HEIGHT = 1240
    
    # 使用同一个浏览器实例来处理所有操作
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1080, "height": 1440})
        
        # 生成多张图片
        page_num = 1
        remaining_paragraphs = paragraphs
        max_pages = 100  # 防止死循环的最大页数
        prev_remaining_count = len(remaining_paragraphs) + 1  # 记录上一次剩余段落数
        
        while remaining_paragraphs and page_num <= max_pages:
            # 检查是否陷入死循环（剩余段落数没有减少）
            current_remaining_count = len(remaining_paragraphs)
            if current_remaining_count >= prev_remaining_count and page_num > 1:
                # 可能陷入死循环，强制处理剩余内容
                print(f"⚠️  检测到可能的死循环，强制处理剩余 {current_remaining_count} 个段落")
                # 强制将所有剩余段落放入当前页
                current_paragraphs = remaining_paragraphs
                remaining_paragraphs = []
            else:
                # 分割内容，获取当前页的段落和剩余段落
                current_paragraphs, remaining_paragraphs = split_content_to_fit(
                    page, remaining_paragraphs, MAX_CONTENT_HEIGHT
                )
            
            if not current_paragraphs:
                # 如果连一个段落都放不下，强制放入（避免死循环）
                if remaining_paragraphs:
                    current_paragraphs = [remaining_paragraphs[0]]
                    remaining_paragraphs = remaining_paragraphs[1:]
                else:
                    # 没有剩余内容了，退出循环
                    break
            
            # 生成当前页的HTML
            html = build_html_from_paragraphs(current_paragraphs)
            
            # 生成文件名
            if page_num == 1:
                out_file = out_dir / f"{args.name}.png"
            else:
                out_file = out_dir / f"{args.name}_{page_num}.png"
            
            # 生成图片
            html_to_pic(page, html, out_file)
            print(f"✅ 内容图片已生成：{out_file} (1080 × 1440) - 第 {page_num} 页")
            
            prev_remaining_count = len(remaining_paragraphs)
            page_num += 1
        
        browser.close()
    
    print(f"✅ 共生成 {page_num - 1} 张图片")
