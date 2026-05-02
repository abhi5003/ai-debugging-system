"""Reranker service with proper singleton lifecycle.

Replaces the module-level ``_model = None`` / ``global _model`` pattern
with a class-based singleton that is testable (``reset()``) and accepts
the model name as a configurable parameter from ``settings``.
"""

import logging
from sentence_transformers import CrossEncoder
from app.config import settings

log = logging.getLogger(__name__)


class RerankerService:
    _instance: "RerankerService | None" = None

    def __init__(self, model_name: str = "BAAI/bge-reranker-base"):
        self._model_name = model_name
        self._model: CrossEncoder | None = None

    @classmethod
    def get_instance(cls) -> "RerankerService":
        if cls._instance is None:
            cls._instance = cls(model_name=settings.embedding_model)
        return cls._instance

    @classmethod
    def reset(cls):
        if cls._instance is not None:
            cls._instance._model = None
            cls._instance = None

    @property
    def model(self) -> CrossEncoder:
        if self._model is None:
            log.info("Loading cross-encoder reranker model: %s", self._model_name)
            self._model = CrossEncoder(self._model_name)
            log.info("Reranker model loaded")
        return self._model

    def rerank(self, query_text: str, incidents: list[dict], top_k: int = 5) -> list[dict]:
        if not incidents or not query_text:
            return incidents

        pairs = []
        for inc in incidents:
            doc_text = (
                f"root cause: {inc.get('root_cause', '')} "
                f"resolution: {inc.get('resolution', '')}"
            )
            pairs.append((query_text, doc_text.strip()))

        scores = self.model.predict(pairs)

        for inc, score in zip(incidents, scores):
            inc["rerank_score"] = float(score)

        ranked = sorted(incidents, key=lambda x: x["rerank_score"], reverse=True)

        log.info(
            "[reranker] scored %d incidents, returning top %d",
            len(ranked), top_k
        )

        return ranked[:top_k]
