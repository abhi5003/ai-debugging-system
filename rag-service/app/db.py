import logging
import asyncpg
from app.config import settings

log = logging.getLogger(__name__)

_pool = None


async def create_pool() -> asyncpg.Pool:
    """Create the shared asyncpg connection pool.

    Uses the Factory pattern to centralise pool creation.
    Called once during application startup (FastAPI lifespan).
    """
    global _pool
    if _pool is not None:
        return _pool

    log.info("Creating asyncpg connection pool: %s", settings.database_url)
    _pool = await asyncpg.create_pool(
        dsn=settings.database_url,
        min_size=2,
        max_size=10,
    )
    return _pool


async def get_pool() -> asyncpg.Pool:
    """Return the active pool, creating one if needed."""
    if _pool is None:
        return await create_pool()
    return _pool


async def close_pool():
    """Gracefully close all connections in the pool."""
    global _pool
    if _pool is not None:
        log.info("Closing asyncpg connection pool")
        await _pool.close()
        _pool = None
