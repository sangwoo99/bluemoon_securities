import re
from datetime import datetime
from urllib.parse import urlparse

import httpx

from app.core.config import settings
from app.schemas import NewsArticle

NAVER_NEWS_URL = "https://openapi.naver.com/v1/search/news.json"
_TAG_RE = re.compile(r"<[^>]+>")


def _strip_tags(text: str) -> str:
    return _TAG_RE.sub("", text).replace("&quot;", '"').replace("&amp;", "&")


def _parse_naver_date(pub_date: str) -> str:
    try:
        return datetime.strptime(pub_date, "%a, %d %b %Y %H:%M:%S %z").strftime("%Y-%m-%d")
    except ValueError:
        return pub_date


def search_news(stock_name: str, display: int = 8) -> list[NewsArticle]:
    """네이버 뉴스 검색 API로 종목 관련 최신 기사를 가져온다. 키 미설정/장애 시 빈 리스트를 반환한다."""
    if not settings.naver_client_id or not settings.naver_client_secret:
        return []

    headers = {
        "X-Naver-Client-Id": settings.naver_client_id,
        "X-Naver-Client-Secret": settings.naver_client_secret,
    }
    params = {"query": stock_name, "display": display, "sort": "date"}

    try:
        response = httpx.get(NAVER_NEWS_URL, headers=headers, params=params, timeout=10)
        response.raise_for_status()
    except httpx.HTTPError:
        return []

    items = response.json().get("items", [])
    return [
        NewsArticle(
            title=_strip_tags(item["title"]),
            description=_strip_tags(item["description"]),
            source=urlparse(item.get("originallink") or item["link"]).netloc or "네이버뉴스",
            date=_parse_naver_date(item["pubDate"]),
            url=item["link"],
        )
        for item in items
    ]
