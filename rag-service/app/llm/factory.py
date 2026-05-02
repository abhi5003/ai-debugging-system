"""LLM factory with Strategy pattern for provider extensibility.

Uses a provider registry so new LLM or embedding providers can be
added without modifying this file (Open-Closed Principle).  Each
provider is a small strategy class that knows how to instantiate
its own chat model or embedding model.
"""

from typing import Protocol
from langchain_core.language_models import BaseChatModel
from langchain_core.embeddings import Embeddings
from app.config import settings


# ── Chat provider strategy ───────────────────────────────────────────────────

class ChatProvider(Protocol):
    """Strategy interface for chat-model providers."""

    def create(self, model: str, api_key: str, max_tokens: int) -> BaseChatModel: ...


class AnthropicChatProvider:
    def create(self, model: str, api_key: str, max_tokens: int) -> BaseChatModel:
        from langchain_anthropic import ChatAnthropic
        return ChatAnthropic(
            model=model,
            api_key=api_key,
            max_tokens=max_tokens,
        )


class OpenAIChatProvider:
    def create(self, model: str, api_key: str, max_tokens: int) -> BaseChatModel:
        from langchain_openai import ChatOpenAI
        return ChatOpenAI(
            model=model,
            api_key=api_key,
            max_tokens=max_tokens,
        )


# ── Embedding provider strategy ──────────────────────────────────────────────

class EmbeddingProvider(Protocol):
    """Strategy interface for embedding-model providers."""

    def create(self, model: str, api_key: str) -> Embeddings: ...


class OpenAIEmbeddingProvider:
    def create(self, model: str, api_key: str) -> Embeddings:
        from langchain_openai import OpenAIEmbeddings
        return OpenAIEmbeddings(
            model=model,
            api_key=api_key,
        )


# ── Registry ─────────────────────────────────────────────────────────────────

_chat_registry: dict[str, ChatProvider] = {}
_embedding_registry: dict[str, EmbeddingProvider] = {}


def register_chat_provider(name: str, provider: ChatProvider) -> None:
    """Register a chat-model provider under a human-readable name."""
    _chat_registry[name.lower()] = provider


def register_embedding_provider(name: str, provider: EmbeddingProvider) -> None:
    """Register an embedding provider under a human-readable name."""
    _embedding_registry[name.lower()] = provider


# Auto-register built-in providers at import time
register_chat_provider("anthropic", AnthropicChatProvider())
register_chat_provider("openai", OpenAIChatProvider())
register_embedding_provider("openai", OpenAIEmbeddingProvider())


# ── Factory ──────────────────────────────────────────────────────────────────

class LLMFactory:
    """Central factory that delegates to registered provider strategies.

    Agents call ``LLMFactory.create_chat_llm()`` or
    ``LLMFactory.create_embeddings()`` without knowing which
    concrete provider is active.  Switching providers only requires
    changing ``settings.llm_provider`` / ``settings.embedding_provider``.
    """

    @staticmethod
    def create_chat_llm(max_tokens: int = 1024) -> BaseChatModel:
        provider_name = settings.llm_provider.lower()
        provider = _chat_registry.get(provider_name)
        if provider is None:
            raise ValueError(
                f"Unsupported LLM provider '{provider_name}'. "
                f"Available: {list(_chat_registry.keys())}"
            )
        return provider.create(
            model=settings.llm_model,
            api_key=settings.anthropic_api_key if provider_name == "anthropic" else settings.openai_api_key,
            max_tokens=max_tokens,
        )

    @staticmethod
    def create_embeddings() -> Embeddings:
        provider_name = settings.embedding_provider.lower()
        provider = _embedding_registry.get(provider_name)
        if provider is None:
            raise ValueError(
                f"Unsupported embedding provider '{provider_name}'. "
                f"Available: {list(_embedding_registry.keys())}"
            )
        return provider.create(
            model=settings.embedding_model,
            api_key=settings.openai_api_key,
        )
