from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_core.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI, OpenAIEmbeddings

from app.core.config import settings
from app.schemas import GenerateInsightResponse, InsightSource, NewsArticle

SYSTEM_PROMPT = """\
너는 증권 뉴스를 요약하는 리서치 보조원이다. 아래 뉴스 발췌를 근거로 종목에 대한 참고용 요약 정보를 작성하라.

절대 규칙:
- "매수하세요", "매도하세요", "지금이 매수 적기" 같은 단정적 투자 추천 문구를 쓰지 말 것.
- 사실과 뉴스의 논조를 3~4문장으로 중립적으로 요약할 것 (근거 있는 설명 위주).
- 제공된 뉴스에 없는 내용을 지어내지 말 것.
- 한국어로 작성할 것.
"""

USER_PROMPT = """\
종목: {stock_name} ({stock_code})

뉴스 발췌:
{context}

위 뉴스를 바탕으로 참고용 요약 정보를 작성하라.
"""


def _build_documents(articles: list[NewsArticle]) -> list[Document]:
    return [
        Document(
            page_content=f"{a.title}\n{a.description}",
            metadata={"source": a.source, "date": a.date},
        )
        for a in articles
    ]


def _retrieve_top_articles(stock_code: str, stock_name: str, articles: list[NewsArticle], top_k: int = 5) -> list[Document]:
    documents = _build_documents(articles)
    embeddings = OpenAIEmbeddings(model=settings.openai_embedding_model, api_key=settings.openai_api_key)
    vector_store = Chroma.from_documents(
        documents=documents,
        embedding=embeddings,
        collection_name=f"insight-{stock_code}",
    )
    return vector_store.similarity_search(f"{stock_name} 최근 주요 이슈와 수급 동향", k=min(top_k, len(documents)))


def generate_insight(stock_code: str, stock_name: str, articles: list[NewsArticle]) -> GenerateInsightResponse | None:
    if not articles:
        return None

    top_docs = _retrieve_top_articles(stock_code, stock_name, articles)
    context = "\n\n".join(f"- [{d.metadata['source']} · {d.metadata['date']}] {d.page_content}" for d in top_docs)

    llm = ChatOpenAI(model=settings.openai_model, api_key=settings.openai_api_key, temperature=0.3)
    prompt = ChatPromptTemplate.from_messages([("system", SYSTEM_PROMPT), ("user", USER_PROMPT)])
    chain = prompt | llm

    result = chain.invoke({"stock_name": stock_name, "stock_code": stock_code, "context": context})

    sources = list({(d.metadata["source"], d.metadata["date"]): None for d in top_docs}.keys())
    return GenerateInsightResponse(
        content=result.content,
        sources=[InsightSource(name=name, date=date) for name, date in sources],
    )
