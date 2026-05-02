import logging
from app.graph.state import AgentState
from app.mcp.client import MCPClient
from app.llm.factory import LLMFactory
from app.config import settings
from app.utils.text_builder import IncidentTextBuilder

log = logging.getLogger(__name__)

mcp_client = MCPClient.get_instance()


def _normalize_similar(similar: list[dict]) -> list[dict]:
    """Ensure all fields exist + normalize structure for downstream agents."""
    normalized = []

    for s in similar:
        normalized.append({
            "number": s.get("number"),
            "root_cause": s.get("root_cause"),
            "resolution": s.get("resolution"),
            "similarity": float(s.get("similarity", 0.0)),
            "source": s.get("source", "AI"),
            "confidence": float(s.get("confidence", 0.5)),
            "rerank_score": s.get("rerank_score"),
            "configuration_item": s.get("configuration_item"),
        })

    return normalized


async def retrieval_agent(state: AgentState) -> dict:
    attempts = state.get("retrieval_attempts", 0)

    top_k = settings.retrieval_top_k
    min_similarity = max(
        settings.retrieval_min_similarity_floor,
        settings.retrieval_initial_similarity - (attempts * settings.retrieval_decay_rate),
    )

    inc = state["incident"]
    builder = IncidentTextBuilder(inc)
    text = builder.for_embedding()
    context_text = builder.for_search_context()

    embedding = await LLMFactory.create_embeddings().aembed_query(text)

    try:
        similar_raw = await mcp_client.vector_search(
            query_embedding=embedding,
            top_k=top_k,
            min_similarity=min_similarity,
            context_text=context_text,
            configuration_item=inc.configuration_item,
            priority=inc.priority,
            max_age_days=settings.retrieval_max_age_days,
        )
    except Exception as e:
        log.exception("MCP vector search failed: %s", e)
        similar_raw = []

    similar = _normalize_similar(similar_raw)

    human_count = sum(1 for s in similar if s["source"] == "HUMAN")
    reranked_count = sum(1 for s in similar if s.get("rerank_score") is not None)

    log.info(
        "[retrieval] attempt=%d top_k=%d min_sim=%.2f found=%d (human=%d, reranked=%d)",
        attempts + 1,
        top_k,
        min_similarity,
        len(similar),
        human_count,
        reranked_count,
    )

    return {
        "embedding": embedding,
        "similar_incidents": similar,
        "retrieval_attempts": attempts + 1,
        "reasoning_trace": [
            f"[retrieval] attempt={attempts + 1} "
            f"top_k={top_k} min_sim={min_similarity:.2f} "
            f"found={len(similar)} human={human_count} reranked={reranked_count}"
        ],
    }
