# script/content_transform.py
# -*- coding: utf-8 -*-
from pathlib import Path
from playwright.sync_api import sync_playwright
from jinja2 import Environment, FileSystemLoader
import base64, sys, argparse, re

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


def parse_markdown_to_html(text: str) -> str:
    """
    将 Markdown 语法转换为 HTML
    支持的语法：
    - **text** 或 __text__ -> <strong>text</strong> (加粗)
    - *text* 或 _text_ -> <em>text</em> (斜体)
    - `text` -> <code>text</code> (行内代码)
    """

    # 使用 Unicode 私有使用区字符作为占位符，避免与普通文本冲突
    # 这些字符不会被 HTML 转义影响
    PLACEHOLDER_STRONG_START = '\uE000'
    PLACEHOLDER_STRONG_END = '\uE001'
    PLACEHOLDER_EM_START = '\uE002'
    PLACEHOLDER_EM_END = '\uE003'
    PLACEHOLDER_CODE_START = '\uE004'
    PLACEHOLDER_CODE_END = '\uE005'

    # 处理行内代码：`text`（最先处理，避免代码内的 Markdown 被解析）
    text = re.sub(r'`([^`]+)`', PLACEHOLDER_CODE_START + r'\1' + PLACEHOLDER_CODE_END, text)

    # 处理加粗：**text** 或 __text__
    text = re.sub(r'\*\*([^*]+)\*\*', PLACEHOLDER_STRONG_START + r'\1' + PLACEHOLDER_STRONG_END, text)
    text = re.sub(r'__([^_]+)__', PLACEHOLDER_STRONG_START + r'\1' + PLACEHOLDER_STRONG_END, text)

    # 处理斜体：*text* 或 _text_（在加粗之后处理，避免冲突）
    # 确保不是加粗标记的一部分
    text = re.sub(r'(?<!\*)\*([^*\n]+?)\*(?!\*)', PLACEHOLDER_EM_START + r'\1' + PLACEHOLDER_EM_END, text)
    text = re.sub(r'(?<!_)_([^_\n]+?)_(?!_)', PLACEHOLDER_EM_START + r'\1' + PLACEHOLDER_EM_END, text)

    # 转义 HTML 特殊字符（占位符不会被转义）
    text = text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')

    # 将占位符转换为 HTML 标签
    text = text.replace(PLACEHOLDER_STRONG_START, '<strong>').replace(PLACEHOLDER_STRONG_END, '</strong>')
    text = text.replace(PLACEHOLDER_EM_START, '<em>').replace(PLACEHOLDER_EM_END, '</em>')
    text = text.replace(PLACEHOLDER_CODE_START, '<code>').replace(PLACEHOLDER_CODE_END, '</code>')

    return text


# 使用 Unicode 私有使用区字符作为换行占位符，避免与 Markdown 语法冲突
BR_PLACEHOLDER = '\uE006'


def paragraph_to_html(paragraph: str) -> str:
    """将段落文本转换为 HTML（支持单换行）"""
    # 先将段落内的换行符替换为占位符（在 Markdown 解析之前）
    paragraph = paragraph.replace('\n', BR_PLACEHOLDER)
    # 解析 Markdown 语法（占位符不会被 Markdown 解析影响）
    paragraph = parse_markdown_to_html(paragraph)
    # 最后将占位符转换为 <br> 标签
    paragraph = paragraph.replace(BR_PLACEHOLDER, '<br>')
    return paragraph


def find_safe_breakpoint(text: str, index: int) -> int:
    """寻找较安全的分割位置，尽量避免打断 Markdown 语法或单词"""
    if index >= len(text):
        return len(text)

    safe_index = index
    boundary_chars = set(' \t\n，。！？；：,.!?;:')

    # 优先尝试向前找到分隔符或空白字符
    while safe_index > 0 and text[safe_index - 1] not in boundary_chars:
        safe_index -= 1

    if safe_index == 0:
        safe_index = index

    # 避免切断加粗、斜体或代码标记
    for marker in ("**", "__", "```", "`"):
        marker_count = text[:safe_index].count(marker)
        if marker_count % 2 != 0:
            marker_pos = text.rfind(marker, 0, safe_index)
            if marker_pos > 0:
                safe_index = marker_pos

    return max(1, safe_index)


def build_html(content="内容文本", decor_emoji=None, decor_position="bottom-right"):
    """构建HTML，用于显示内容文本，处理\n\n作为段落分隔，支持 Markdown 语法"""
    # 改进段落分割逻辑：正确处理单个 \n 和双 \n\n 的情况
    paragraphs = []
    # 使用临时标记来区分段落分隔符和段落内的换行
    PARAGRAPH_SEPARATOR = '\uE007'  # Unicode 私有使用区字符
    # 将两个或更多连续换行符替换为段落分隔符
    content_normalized = re.sub(r'\n{2,}', PARAGRAPH_SEPARATOR, content)
    # 按段落分隔符分割
    for p in content_normalized.split(PARAGRAPH_SEPARATOR):
        p = p.strip()
        if p:
            paragraphs.append(paragraph_to_html(p))

    env = Environment(loader=FileSystemLoader(SCRIPT_DIR))
    tpl = env.get_template("content_template.html")
    cfg = {
        "font_b": to_b64(FONT_CONFIG["font_b"]),
        "font_r": to_b64(FONT_CONFIG["font_r"]),
        "title": "内容图片",
        "paragraphs": [paragraph_to_html(p) for p in paragraphs],
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
    if '\n' in first:
        parts = [part for part in first.split('\n') if part.strip()]
        l, r, best_lines = 1, len(parts), 0
        while l <= r:
            m = (l + r) // 2
            test_para = '\n'.join(parts[:m])
            h = measure_height_for_paragraphs(page, [test_para])
            if h <= max_height:
                best_lines = m
                l = m + 1
            else:
                r = m - 1
        if best_lines > 0:
            current_paragraphs = ['\n'.join(parts[:best_lines])]
            remaining_first = '\n'.join(parts[best_lines:])
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
    split_index = find_safe_breakpoint(text, best_chars)
    current_segment = text[:split_index]
    remaining_first = text[split_index:]
    current_paragraphs = [current_segment]
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
        "paragraphs": [paragraph_to_html(p) for p in paragraphs],
        "decor_emoji": decor_emoji,
        "decor_position": decor_position
    }
    return tpl.render(**cfg)


def sanitize_filename(filename: str) -> str:
    """清理文件名，移除 Windows 非法字符"""
    # Windows 文件名非法字符：< > : " / \ | ? *
    illegal_chars = '<>:"/\\|?*'
    for char in illegal_chars:
        filename = filename.replace(char, '_')
    # 移除首尾空格和点号（Windows 不允许）
    filename = filename.strip(' .')
    # 如果文件名为空，使用默认名称
    if not filename:
        filename = 'untitled'
    return filename


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
    parser.add_argument("--name", default="test",
                        help="输出文件名（不包含扩展名）")
    parser.add_argument("--out", default=str(OUT_DIR),
                        help="输出目录，支持相对路径与绝对路径（默认：项目 out 目录）")

    args = parser.parse_args()

    # 将字面字符串 \n 替换为真正的换行符
    content = args.content.replace('\\n', '\n')

    # 解析输出目录
    out_dir = Path(args.out)
    if not out_dir.is_absolute():
        out_dir = ROOT_DIR / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    paragraphs = []
    # 使用临时标记来区分段落分隔符和段落内的换行
    PARAGRAPH_SEPARATOR = '\uE007'  # Unicode 私有使用区字符
    content_normalized = re.sub(r'\n{2,}', PARAGRAPH_SEPARATOR, content)
    # 按段落分隔符分割
    for p in content_normalized.split(PARAGRAPH_SEPARATOR):
        p = p.strip()
        if p:
            paragraphs.append(p)

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

        while remaining_paragraphs and page_num <= max_pages:
            # 分割内容，获取当前页的段落和剩余段落
            current_paragraphs, next_paragraphs = split_content_to_fit(
                page, remaining_paragraphs, MAX_CONTENT_HEIGHT
            )

            if not current_paragraphs:
                # 如果连一个段落都放不下，强制放入（避免死循环）
                if remaining_paragraphs:
                    current_paragraphs = [remaining_paragraphs[0]]
                    next_paragraphs = remaining_paragraphs[1:]
                else:
                    # 没有剩余内容了，退出循环
                    break

            remaining_paragraphs = next_paragraphs

            # 生成当前页的HTML
            html = build_html_from_paragraphs(current_paragraphs)

            # 生成文件名（清理非法字符）
            safe_name = sanitize_filename(args.name)
            if page_num == 1:
                out_file = out_dir / f"{safe_name}.png"
            else:
                out_file = out_dir / f"{safe_name}_{page_num}.png"

            # 生成图片（添加异常处理）
            try:
                html_to_pic(page, html, out_file)
                # 验证文件是否真的被创建
                if out_file.exists():
                    print(f"✅ 内容图片已生成：{out_file} (1080 × 1440) - 第 {page_num} 页")
                else:
                    print(f"❌ 图片生成失败：文件未创建 - {out_file}")
            except Exception as e:
                print(f"❌ 图片生成失败：{e} - {out_file}")
                raise  # 重新抛出异常，让程序知道出错了

            page_num += 1

        browser.close()

    print(f"✅ 共生成 {page_num - 1} 张图片")