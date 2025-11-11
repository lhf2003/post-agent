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


def parse_underline_range(range_str):
    """解析下划线范围，格式：[start,end] 或 start,end"""
    if not range_str:
        return None
    # 去掉方括号
    range_str = range_str.strip().strip('[]')
    try:
        parts = range_str.split(',')
        if len(parts) != 2:
            return None
        start = int(parts[0].strip())
        end = int(parts[1].strip())
        # 返回需要加下划线的所有下标
        return list(range(start, end + 1))
    except ValueError:
        return None


def build_html(title="小红书封面", underline_indices=None, decor_emoji=None, decor_position="bottom-right"):
    """构建HTML，预处理标题字符以支持波浪线下划线和换行"""
    # 定义需要换行的符号
    linebreak_symbols = "！？|"

    # 将标题转换为字符列表，标记需要下划线的字符和需要换行的位置
    title_chars = []
    underline_set = set(underline_indices or [])
    title_len = len(title)

    for i, char in enumerate(title):
        # 判断是否需要换行：字符是换行符号 且 不是最后一个字符
        need_linebreak = char in linebreak_symbols and i < title_len - 1

        title_chars.append({
            "char": char,
            "underline": i in underline_set,
            "linebreak": need_linebreak
        })

    env = Environment(loader=FileSystemLoader(SCRIPT_DIR))
    tpl = env.get_template("template.html")
    cfg = {
        "font_b": to_b64(FONT_CONFIG["font_b"]),
        "font_r": to_b64(FONT_CONFIG["font_r"]),
        "title_chars": title_chars,
        "decor_emoji": decor_emoji,
        "decor_position": decor_position
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
    parser.add_argument("--underline", default=None,
                        help="标题下划线范围，格式：[start,end]，如 [0,2] 表示下标0到2的字符")
    parser.add_argument("--decor-emoji", default=None,
                        help="装饰emoji表情（可选，如：🎉、✨、💡、🤨等）")
    parser.add_argument("--decor-position", choices=["bottom-left", "bottom-right"], default="bottom-left",
                        help="装饰emoji位置：bottom-left（左下角）或 bottom-right（右下角），默认右下角")
    parser.add_argument("--out", default=str(OUT_DIR),
                        help="输出目录，支持相对路径与绝对路径（默认：项目 out 目录）")

    args = parser.parse_args()

    # 解析下划线范围
    underline_indices = parse_underline_range(args.underline)

    # 解析输出目录
    out_dir = Path(args.out)
    if not out_dir.is_absolute():
        out_dir = ROOT_DIR / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    # 生成输出文件路径
    out_file = out_dir / f"{args.name}.png"

    # 生成HTML并截图
    html = build_html(title=args.title, underline_indices=underline_indices,
                     decor_emoji=args.decor_emoji, decor_position=args.decor_position)
    html_to_pic(html, out_file)
    print(f"✅ 小红书封面图已生成：{out_file} (1080 × 1440)")


