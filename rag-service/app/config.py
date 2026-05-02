from pydantic_settings import BaseSettings
from pydantic import model_validator
import logging

log = logging.getLogger(__name__)


class Settings(BaseSettings):
    anthropic_api_key:       str = "dummy"
    openai_api_key:          str = "dummy"
    tavily_api_key:          str = "dummy"
    tavily_url:              str = "https://api.tavily.com/search"
    database_url:            str = "postgresql://postgres:password@localhost:5432/incidents"
    rag_service_port:        int = 8000

    # LLM / embedding provider selection
    llm_provider:            str = "anthropic"
    embedding_provider:      str = "openai"
    embedding_model:         str = "text-embedding-3-small"
    llm_model:               str = "claude-sonnet-4-5"

    # Confidence calibration thresholds
    confidence_threshold:    float = 0.70
    confidence_boost_similar_count: int = 3
    confidence_boost_amount: float = 0.10
    confidence_penalty_none: float = 0.15
    confidence_error_rate_threshold: float = 20.0
    confidence_boost_error_amount: float = 0.05

    # Retrieval parameters
    retrieval_top_k:         int = 20
    retrieval_min_similarity_floor: float = 0.60
    retrieval_initial_similarity: float = 0.75
    retrieval_decay_rate:    float = 0.05
    retrieval_max_age_days:  int = 180
    max_retrieval_attempts:  int = 3

    # Loop / retry limits
    max_retry_loops:         int = 3

    # Timeouts
    mcp_tool_timeout:        int = 10
    web_search_timeout:      int = 8

    class Config:
        env_file = ".env"
        extra = "ignore"

    @model_validator(mode="after")
    def _warn_dummy_keys(self) -> "Settings":
        """Warn when dummy/default API keys are in use.

        Prevents silent failures where the app starts with placeholder
        credentials and then fails on the first real LLM call.
        """
        for key_name in ("anthropic_api_key", "openai_api_key", "tavily_api_key"):
            val = getattr(self, key_name)
            if not val or val == "dummy":
                log.warning(
                    "%s is set to '%s' — LLM calls will fail "
                    "until a real key is provided",
                    key_name, val,
                )
        return self


settings = Settings()
