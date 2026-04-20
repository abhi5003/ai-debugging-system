import asyncpg
import logging
from config import settings

log = logging.getLogger(__name__)


async def vector_search(
        query_embedding: list[float],
        top_k: int = 5,
        min_similarity: float = 0.75) -> list[dict]:
    """
    Search pgvector for resolved incidents similar to the query embedding.
    Returns list of dicts with: sys_id, number, description,
    root_cause, resolution, priority, similarity.
    Falls back to empty list on any error so the pipeline never blocks.
    """
    try:
        conn = await asyncpg.connect(settings.database_url)
        try:
            vec_str = "[" + ",".join(str(float(v)) for v in query_embedding) + "]"

            rows = await conn.fetch("""
                SELECT
                    sys_id,
                    number,
                    description,
                    root_cause,
                    resolution,
                    priority,
                    ROUND(CAST(1 - (embedding <=> $1::vector) AS numeric), 4)
                        AS similarity
                FROM incident_embeddings
                WHERE resolution IS NOT NULL
                  AND 1 - (embedding <=> $1::vector) >= $2
                ORDER BY embedding <=> $1::vector
                LIMIT $3
            """, vec_str, min_similarity, top_k)

            results = [dict(r) for r in rows]
            log.info("vector_search: found %d results (top_k=%d min_sim=%.2f)",
                     len(results), top_k, min_similarity)
            return results

        finally:
            await conn.close()

    except Exception as e:
        log.error("vector_search error: %s", e)
        return []
