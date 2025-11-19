# script/gen_long_pic.py
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


def build_html(title="小红书封面"):
    """构建HTML，预处理标题字符以支持换行"""
    # 预处理：过滤掉仅包含特殊符号的独立行
    special_line_chars = set("!?？！。，、；;:|~…—-")
    filtered_lines = []
    for line in title.split('\n'):
        stripped = line.strip()
        if stripped and all(ch in special_line_chars for ch in stripped):
            continue
        filtered_lines.append(line)
    title = '\n'.join(filtered_lines)

    # 定义需要换行的符号
    linebreak_symbols = "！？|"

    # 将标题转换为字符列表，标记需要下划线的字符和需要换行的位置
    title_chars = []
    title_len = len(title)

    # 用于跟踪实际字符索引（排除换行符）
    actual_char_index = 0

    for i, char in enumerate(title):
        # 如果遇到换行符 \n，在前一个字符后标记换行，但不添加换行符本身
        if char == '\n':
            # 如果已经有字符，在前一个字符后标记换行
            if title_chars:
                title_chars[-1]["linebreak"] = True
            # 跳过换行符，不添加到字符列表
            continue

        # 判断是否需要换行：
        # 1. 字符是换行符号
        # 2. 不是最后一个字符
        # 3. 如果是连续符号，只在最后一个连续符号后换行
        need_linebreak = False
        if char in linebreak_symbols and i < title_len - 1:
            # 检查下一个字符是否也是换行符号
            next_char = title[i + 1] if i + 1 < title_len else None
            # 如果下一个字符不是换行符号，或者不存在，则当前字符后换行
            if next_char is None or next_char not in linebreak_symbols:
                need_linebreak = True

        title_chars.append({
            "char": char,
            "linebreak": need_linebreak
        })

        # 只有非换行符才增加实际字符索引
        actual_char_index += 1

    if title_chars:
        lines = []
        current_line = []
        for info in title_chars:
            current_line.append(info)
            if info["linebreak"]:
                lines.append(current_line)
                current_line = []
        if current_line:
            lines.append(current_line)

        filtered_chars = []
        for line_infos in lines:
            content = ''.join(item["char"] for item in line_infos).strip()
            content_compact = ''.join(ch for ch in content if not ch.isspace())
            if content_compact and all(ch in special_line_chars for ch in content_compact):
                continue
            filtered_chars.extend(line_infos)

        title_chars = filtered_chars

    env = Environment(loader=FileSystemLoader(SCRIPT_DIR))
    tpl = env.get_template("cover_template.html")
    cfg = {
        "font_b": to_b64(FONT_CONFIG["font_b"]),
        "font_r": to_b64(FONT_CONFIG["font_r"]),
        "title_chars": title_chars
    }
    return tpl.render(**cfg)


def html_to_pic(html, save_path, width=1080, height=1440):
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": width, "height": height})
        page.set_content(html)
        page.wait_for_load_state("networkidle")
        # 固定尺寸截图，保持小红书封面格式 3:4 宽高比（1080 × 1440）
        page.screenshot(path=save_path)
        browser.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="生成小红书封面图工具")
    parser.add_argument("--name", required=True,
                        help="输出文件名（不包含扩展名）")
    parser.add_argument("--title", default="小红书封面",
                        help="标题内容（默认：小红书封面）")
    parser.add_argument("--out", default=str(OUT_DIR),
                        help="输出目录，支持相对路径与绝对路径（默认：项目 out 目录）")

    args = parser.parse_args()

    # 处理转义字符：将字符串中的 \n 转换为真正的换行符
    title = args.title.replace('\\n', '\n')

    # 解析输出目录
    out_dir = Path(args.out)
    if not out_dir.is_absolute():
        out_dir = ROOT_DIR / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    # 生成输出文件路径
    out_file = out_dir / f"{args.name}.png"

    # 生成HTML并截图
    html = build_html(title=title)
    html_to_pic(html, out_file)
    print(f"✅ 小红书封面图已生成：{out_file} (1080 × 1440)")