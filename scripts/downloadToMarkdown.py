import argparse
import os
import re
import sys
from datetime import datetime
from urllib.parse import urlparse

import requests
import trafilatura
from bs4 import BeautifulSoup
from dateutil import tz


def sanitize_filename(text: str) -> str:
    text = re.sub(r"[\\/:*?\"<>|]", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text[:120] if len(text) > 120 else text


def build_front_matter(title: str, url: str, dt: datetime) -> str:
    iso = dt.astimezone(tz.tzlocal()).isoformat(timespec="seconds")
    return f"""---
title: "{title}"
source: "{url}"
saved_at: "{iso}"
---

"""


def parse_title_and_date_from_html(html: str, url: str) -> tuple[str, datetime]:
    soup = BeautifulSoup(html, "html.parser")

    # 标题：优先 <title>，其次 og:title，最后域名
    raw_title = None
    if soup.title and soup.title.string:
        raw_title = soup.title.string.strip()
    if not raw_title:
        og_title = soup.find("meta", property="og:title")
        if og_title and og_title.get("content"):
            raw_title = og_title["content"].strip()
    title = raw_title or urlparse(url).netloc

    # 日期尝试从常见 meta 中提取，失败则用当前时间（UTC）
    date_str = None
    candidates = [
        ("meta", {"property": "article:published_time"}),
        ("meta", {"name": "pubdate"}),
        ("meta", {"name": "publishdate"}),
        ("meta", {"name": "date"}),
        ("meta", {"itemprop": "datePublished"}),
        ("time", {"datetime": True}),
    ]
    for name, attrs in candidates:
        tag = soup.find(name, attrs)
        if tag:
            if name == "time" and tag.get("datetime"):
                date_str = tag["datetime"].strip()
                break
            if tag.get("content"):
                date_str = tag["content"].strip()
                break

    date_obj = None
    if date_str:
        for fmt in [
            "%Y-%m-%dT%H:%M:%S%z",
            "%Y-%m-%dT%H:%M:%S.%f%z",
            "%Y-%m-%d %H:%M:%S%z",
            "%Y-%m-%dT%H:%M:%S",
            "%Y-%m-%d %H:%M:%S",
            "%Y-%m-%d",
        ]:
            try:
                ds = date_str.replace("Z", "+00:00")
                date_obj = datetime.strptime(ds, fmt)
                if date_obj.tzinfo is None:
                    date_obj = date_obj.replace(tzinfo=tz.UTC)
                break
            except Exception:
                continue
    if not date_obj:
        date_obj = datetime.now(tz=tz.UTC)

    return title, date_obj


def url_to_markdown(url: str, timeout: int = 20) -> dict:
    # 使用 requests 下载 HTML（可控超时与 UA）
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/124.0.0.0 Safari/537.36"
        )
    }
    try:
        resp = requests.get(url, headers=headers, timeout=timeout)
        resp.raise_for_status()
    except Exception as e:
        raise RuntimeError(f"下载失败：{e}")

    html = resp.text
    if not html:
        raise RuntimeError("下载失败：空响应")

    # 抽取正文为 Markdown
    md = trafilatura.extract(
        html,
        output_format="markdown",
        include_links=True,
        include_images=True,
        include_tables=True,
        with_metadata=True,
        favor_recall=True,
        url=url,
        deduplicate=True,
        no_fallback=False,
    )
    if not md:
        raise RuntimeError("正文抽取失败：未识别到可用正文")

    # 标题与日期使用 BeautifulSoup 自行解析，避免版本兼容问题
    title, date_obj = parse_title_and_date_from_html(html, url)

    # 统一样式：Front Matter + 一级标题
    front = build_front_matter(title, url, date_obj)
    if not md.lstrip().startswith("# "):
        md = f"# {title}\n\n{md}"

    final_md = f"{front}{md.strip()}\n"
    return {"title": title, "markdown": final_md}


def ensure_dir(path: str) -> None:
    if path and not os.path.exists(path):
        os.makedirs(path, exist_ok=True)


def save_markdown(title: str, markdown: str, outdir: str) -> str:
    ensure_dir(outdir)
    filename = sanitize_filename(title) or "untitled"
    path = os.path.join(outdir, f"{filename}.md")
    i = 2
    base = path[:-3]
    while os.path.exists(path):
        path = f"{base}-{i}.md"
        i += 1
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(markdown)
    return path


def main():
    parser = argparse.ArgumentParser(description="将网页正文下载为统一样式 Markdown")
    parser.add_argument("url", nargs="+", help="要下载的 URL（可多个）")
    # 修改默认输出目录为当前时间格式
    default_outdir = datetime.now().strftime("%Y%m%d_%H%M%S")
    parser.add_argument("-o", "--outdir", default=default_outdir, help=f"输出目录（默认：{default_outdir}）")
    parser.add_argument("-t", "--timeout", type=int, default=20, help="下载超时秒数（默认：20）")
    args = parser.parse_args()

    exit_code = 0
    for u in args.url:
        try:
            result = url_to_markdown(u, timeout=args.timeout)
            outpath = save_markdown(result["title"], result["markdown"], args.outdir)
            print(f"OK  -> {u}\nsaved: {outpath}\n")
        except Exception as e:
            print(f"FAIL -> {u}\nreason: {e}\n", file=sys.stderr)
            exit_code = 1
    sys.exit(exit_code)


if __name__ == "__main__":
    main()