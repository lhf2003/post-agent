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


def measure_height_for_paragraphs(page, paragraphs):
    """基于段落列表构建HTML并测量高度（减少重复代码）"""
    test_html = build_html_from_paragraphs(paragraphs)
    return measure_content_height(page, test_html)


def split_content_to_fit(page, paragraphs, max_height=1240):
    """将段落列表分割，使内容适合一张图片（使用已存在的page对象）
    返回: (当前页段落列表, 剩余段落列表)
    """
    if not paragraphs:
        return [], []

    # 使用二分查找，找出在不超过 max_height 的情况下最多能容纳的段落数
    left, right = 0, len(paragraphs)  # 容纳段落数量区间 [left, right]
    best = 0
    while left <= right:
        mid = (left + right) // 2
        test = paragraphs[:mid]
        if not test:
            left = mid + 1
            continue
        h = measure_height_for_paragraphs(page, test)
        if h <= max_height:
            best = mid
            left = mid + 1
        else:
            right = mid - 1

    if best > 0:
        current_paragraphs = paragraphs[:best]
        remaining_paragraphs = paragraphs[best:]
        # 过滤空段落
        current_paragraphs = [p for p in current_paragraphs if p.strip()]
        remaining_paragraphs = [p for p in remaining_paragraphs if p.strip()]
        return current_paragraphs, remaining_paragraphs

    # 如果一个段落都放不下，处理首段过长的情况：对首段进行“按行”二分拆分
    first = paragraphs[0]
    if '<br>' in first:
        parts = [p for p in first.split('<br>') if p.strip()]
        l, r, best_lines = 1, len(parts), 0
        while l <= r:
            m = (l + r) // 2
            test_para = '<br>'.join(parts[:m])
            h = measure_height_for_paragraphs(page, [test_para])
            if h <= max_height:
                best_lines = m
                l = m + 1
            else:
                r = m - 1
        if best_lines > 0:
            current_paragraphs = ['<br>'.join(parts[:best_lines])]
            remaining_first = '<br>'.join(parts[best_lines:])
            remaining_paragraphs = ([remaining_first] if remaining_first.strip() else []) + paragraphs[1:]
            return current_paragraphs, remaining_paragraphs

    # 否则对首段做粗略字符二分拆分，至少拿出一半，避免死循环
    text = first
    l, r, best_chars = 1, max(1, len(text) // 2), 0
    while l <= r:
        m = (l + r) // 2
        h = measure_height_for_paragraphs(page, [text[:m]])
        if h <= max_height:
            best_chars = m
            l = m + 1
        else:
            r = m - 1
    if best_chars == 0:
        # 兜底：至少截取部分字符，避免卡死
        best_chars = max(1, len(text) // 3)
    current_paragraphs = [text[:best_chars]]
    remaining_first = text[best_chars:]
    remaining_paragraphs = ([remaining_first] if remaining_first.strip() else []) + paragraphs[1:]
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
