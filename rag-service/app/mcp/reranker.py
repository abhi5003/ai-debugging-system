"""Backward-compatible wrapper around RerankerService.

Existing callers (if any outside this package) can still import
``rerank_incidents`` from this module.  New code should prefer
``RerankerService.get_instance().rerank(...)`` directly.
"""

import logging
from app.mcp.reranker_service import RerankerService

log = logging.getLogger(__name__)


def rerank_incidents(
    query_text: str,
    incidents: list[dict],
    top_k: int = 5,
) -> list[dict]:
    _service = RerankerService.get_instance()
    return _service.rerank(query_text, incidents, top_k)
