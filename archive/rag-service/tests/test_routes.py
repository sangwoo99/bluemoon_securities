from unittest.mock import patch

from fastapi.testclient import TestClient

from main import app

client = TestClient(app)


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_generate_insight_returns_404_without_news():
    with patch("app.api.routes.search_news", return_value=[]):
        response = client.post("/generate-insight", json={"stockCode": "005930", "stockName": "삼성전자"})
    assert response.status_code == 404
