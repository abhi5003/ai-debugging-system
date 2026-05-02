import asyncpg
import logging
from app.config import settings
from app.db import get_pool

log = logging.getLogger(__name__)

PRIORITY_ADJACENT = {
    'CRITICAL': ['CRITICAL', 'HIGH'],
    'HIGH': ['CRITICAL', 'HIGH', 'MEDIUM'],
    'MEDIUM': ['HIGH', 'MEDIUM', 'LOW'],
    'LOW': ['MEDIUM', 'LOW'],
}


async def vector_search(
    query_embedding: list[float],
    top_k: int = 20,
    min_similarity: float = 0.60,
    configuration_item: str | None = None,
    priority: str | None = None,
    max_age_days: int = 180,
) -> list[dict]:
    """Search pgvector for resolved incidents similar to the query embedding.
    Uses weighted ranking (HUMAN > AI, high confidence boosted).
    Supports metadata filtering by CI, priority, and recency."""

    if not query_embedding:
        log.warning('vector_search: empty embedding')
        return []

    try:
        pool = await get_pool()

        async with pool.acquire() as conn:
            vec_str = '[' + ','.join(
                str(float(v) if v is not None else 0.0)
                for v in query_embedding
            ) + ']'

            params = [vec_str, min_similarity]
            param_idx = 3

            where_clauses = [
                'resolution IS NOT NULL',
                'confidence IS NOT NULL',
                'confidence >= 0.5',
                '1 - (embedding <=> $1::vector) >= $2',
                f"resolved_at >= NOW() - INTERVAL '{max_age_days} days'",
            ]

            if configuration_item:
                where_clauses.append(
                    f"(configuration_item = ${param_idx} OR description ILIKE '%' || ${param_idx} || '%')"
                )
                params.append(configuration_item)
                param_idx += 1

            if priority:
                adjacent = PRIORITY_ADJACENT.get(priority, [priority])
                placeholders = ','.join(f'${param_idx + i}' for i in range(len(adjacent)))
                where_clauses.append(f'priority IN ({placeholders})')
                params.extend(adjacent)
                param_idx += len(adjacent)

            where_sql = ' AND '.join(where_clauses)

            query = f'''
                SELECT
                    sys_id,
                    number,
                    description,
                    root_cause,
                    resolution,
                    priority,
                    source,
                    confidence,
                    configuration_item,

                    ROUND(CAST(1 - (embedding <=> $1::vector) AS numeric), 4)
                        AS similarity,

                    (embedding <=> $1::vector) *
                    CASE
                        WHEN source = 'HUMAN' THEN 0.7
                        WHEN confidence >= 0.85 THEN 0.8
                        ELSE 1.0
                    END AS weighted_distance

                FROM incident_embeddings

                WHERE {where_sql}

                ORDER BY weighted_distance ASC

                LIMIT ${param_idx}
            '''

            params.append(top_k)

            rows = await conn.fetch(query, *params)

            results = [
                {
                    'sys_id': r['sys_id'],
                    'number': r['number'],
                    'root_cause': r['root_cause'],
                    'resolution': r['resolution'],
                    'similarity': float(r['similarity']),
                    'source': r['source'],
                    'confidence': r['confidence'],
                    'configuration_item': r.get('configuration_item'),
                }
                for r in rows
            ]

            log.info(
                'vector_search: found %d results (top_k=%d, min_sim=%.2f, ci=%s, priority=%s)',
                len(results), top_k, min_similarity, configuration_item, priority
            )

            return results

    except Exception as e:
        log.error('vector_search error: %s', e)
        return []
