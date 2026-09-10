from pydantic import BaseModel


class GenerateInsightRequest(BaseModel):
    stockCode: str
    stockName: str


class InsightSource(BaseModel):
    name: str
    date: str


class GenerateInsightResponse(BaseModel):
    content: str
    sources: list[InsightSource]


class NewsArticle(BaseModel):
    title: str
    description: str
    source: str
    date: str
    url: str
