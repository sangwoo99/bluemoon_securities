# 백업 — Python RAG 서비스

이 디렉터리는 원래 `/rag-service`(리포지토리 루트)에 있던 FastAPI + LangChain + Chroma 기반 AI 인사이트 파이프라인입니다.

## 왜 백업했는가

오라클 클라우드 Always Free VM(Ampere A1, 4 OCPU/24GB 공유)에 백엔드+RAG 서비스를 함께 배포하려 했으나,
Python(FastAPI+LangChain+Chroma) 프로세스가 상시로 점유하는 메모리가 실제 배포 시 예상보다 커서 전체 스택이
같은 VM에서 안정적으로 뜨지 않는 문제가 있었습니다.

AI 인사이트 기능 자체가 "뉴스 검색 → LLM 요약" 정도로, 벡터 검색/RAG 파이프라인이 반드시 필요할 만큼 AI 의존도가
높은 기능이 아니라고 판단해, 같은 로직을 Spring Boot 내부(`NaverNewsClient` + `OpenAiClient` +
`InsightGenerationService`, `backend/src/main/java/com/bluemoon/backend/`)로 이식하고 이 Python 서비스는
사용을 중단했습니다. 코드는 그대로 보존해두었고, 나중에 컴퓨팅 자원이 확보되면(별도 VM, 유료 인스턴스 등)
벡터 검색 기반 RAG 파이프라인으로 다시 전환할 수 있습니다.

## 되살리는 방법

1. `docker-compose.yml`에 `rag-service` 서비스 블록을 복원(git 히스토리에서 이 변경 전 커밋 참고)하고, 이 디렉터리를
   `build:` 컨텍스트로 지정
2. `.env`에 `OPENAI_EMBEDDING_MODEL`, `CHROMA_PERSIST_DIR`, `RAG_SERVICE_URL` 등 다시 추가
3. Spring 쪽 `InsightBatchService`가 `InsightGenerationService`가 아닌, 이 서비스를 호출하는 HTTP 클라이언트를
   다시 쓰도록 전환 (과거 `RagServiceClient` 참고 — git 히스토리에 남아 있음)
4. `docs/api-spec.md` 6장(내부 전용 API), `docs/PRD.md`/`README.md`의 아키텍처 다이어그램을 RAG 구조로 되돌리기

이 디렉터리 자체의 코드/구조는 이전과 동일하며, `main.py`/`app/` 이하 내용을 그대로 실행할 수 있습니다.
