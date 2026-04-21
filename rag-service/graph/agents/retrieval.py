import logging
from langchain_openai import OpenAIEmbeddings
from graph.state import AgentState
from mcp.client import MCPClient
from config import settings

log = logging.getLogger(__name__)

_embeddings = OpenAIEmbeddings(
    model=settings.embedding_model,
    api_key=settings.openai_api_key
)

mcp_client = MCPClient()


def _build_embedding_text(state: AgentState) -> str:
    inc = state["incident"]
    parts = [
        f"description: {inc.short_description}",
        f"priority: {inc.priority}",
        f"ci: {inc.configuration_item or 'unknown'}",
    ]
    if inc.metrics:
        parts += [
            f"error_rate: {inc.metrics.error_rate_percent:.1f}%",
            f"response_time: {inc.metrics.response_time_ms:.0f}ms",
            f"cpu: {inc.metrics.cpu_usage_percent:.1f}%",
        ]
    if inc.traces:
        parts.append(f"open_problems: {len(inc.traces.recent_problem_ids)}")
        if inc.traces.slow_span_operations:
            parts.append(
                f"slow_spans: {', '.join(inc.traces.slow_span_operations)}"
            )
    if inc.topology:
        if inc.topology.upstream_services:
            parts.append(f"upstream: {', '.join(inc.topology.upstream_services)}")
        if inc.topology.downstream_services:
            parts.append(
                f"downstream: {', '.join(inc.topology.downstream_services)}"
            )
    return "\n".join(parts)


async def retrieval_agent(state: AgentState) -> dict:
    attempts = state.get("retrieval_attempts", 0)

    # Widen search on each retry attempt
    top_k          = 5 + (attempts * 5)
    min_similarity = max(0.60, 0.75 - (attempts * 0.05))

    text      = _build_embedding_text(state)
    embedding = await _embeddings.aembed_query(text)
    try:
        similar = await mcp_client.vector_search(
            query_embedding=embedding,
            top_k=top_k,
            min_similarity=min_similarity
        )
    except Exception as e:
        log.exception("MCP failed: %s", e)
        similar = []   # fallback

    log.info("[retrieval] attempt=%d top_k=%d min_sim=%.2f found=%d",
             attempts + 1, top_k, min_similarity, len(similar))

    return {
        "embedding":          embedding,
        "similar_incidents":  similar,
        "retrieval_attempts": attempts + 1,
        "reasoning_trace": [
            f"[retrieval] attempt={attempts + 1}  "
            f"top_k={top_k}  min_sim={min_similarity:.2f}  "
            f"found={len(similar)} incidents"
        ],
    }
