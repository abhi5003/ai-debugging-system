import asyncpg
import logging
from app.config import settings

log = logging.getLogger(__name__)


async def vector_search(
    query_embedding: list[float],
    top_k: int = 5,
    min_similarity: float = 0.75
) -> list[dict]:
    """
    Search pgvector for resolved incidents similar to the query embedding.
    Uses weighted ranking (HUMAN > AI, high confidence boosted).
    """

    if not query_embedding:
        log.warning("vector_search: empty embedding")
        return []

    try:
        conn = await asyncpg.connect(settings.database_url)

        try:
            # ✅ Safe vector conversion
            vec_str = "[" + ",".join(
                str(float(v) if v is not None else 0.0)
                for v in query_embedding
            ) + "]"

            rows = await conn.fetch("""
                SELECT
                    sys_id,
                    number,
                    description,
                    root_cause,
                    resolution,
                    priority,
                    source,
                    confidence,

                    -- similarity score
                    ROUND(CAST(1 - (embedding <=> $1::vector) AS numeric), 4)
                        AS similarity,

                    -- 🔥 weighted ranking
                    (embedding <=> $1::vector) *
                    CASE
                        WHEN source = 'HUMAN' THEN 0.7
                        WHEN confidence >= 0.85 THEN 0.8
                        ELSE 1.0
                    END AS weighted_distance

                FROM incident_embeddings

                WHERE resolution IS NOT NULL
                  AND confidence IS NOT NULL
                  AND confidence >= 0.5
                  AND 1 - (embedding <=> $1::vector) >= $2

                ORDER BY weighted_distance ASC

                LIMIT $3
            """, vec_str, min_similarity, top_k)

            results = [
                {
                    "sys_id": r["sys_id"],            # ✅ added
                    "number": r["number"],
                    "root_cause": r["root_cause"],
                    "resolution": r["resolution"],
                    "similarity": float(r["similarity"]),
                    "source": r["source"],
                    "confidence": r["confidence"]
                }
                for r in rows
            ]

            log.info(
                "vector_search: found %d results (top_k=%d, min_sim=%.2f)",
                len(results),
                top_k,
                min_similarity
            )

            return results

        finally:
            await conn.close()

    except Exception as e:
        log.error("vector_search error: %s", e)
        return []
