from fastapi import APIRouter, HTTPException

from app.schemas import GenerateInsightRequest, GenerateInsightResponse
from app.services.news_search import search_news
from app.services.rag_pipeline import generate_insight

router = APIRouter()


@router.post("/generate-insight", response_model=GenerateInsightResponse)
def generate_insight_endpoint(request: GenerateInsightRequest) -> GenerateInsightResponse:
    """Spring Boot 배치가 하루 1회 호출하는 내부 전용 엔드포인트 (docs/api-spec.md 6장). 외부에 노출하지 않는다."""
    articles = search_news(request.stockName)
    insight = generate_insight(request.stockCode, request.stockName, articles)

    if insight is None:
        raise HTTPException(status_code=404, detail="관련 뉴스를 찾을 수 없습니다.")

    return insight
