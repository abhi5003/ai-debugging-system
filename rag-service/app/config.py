from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    anthropic_api_key:       str = "dummy"
    openai_api_key:          str = "dummy"
    tavily_api_key:          str = "dummy"
    tavily_url:              str = "https://api.tavily.com/search"
    database_url:            str = "postgresql://postgres:password@localhost:5432/incidents"
    rag_service_port:        int = 8000
    confidence_threshold:    float = 0.70
    max_retrieval_attempts:  int = 3
    embedding_model:         str = "text-embedding-3-small"
    llm_model:               str = "claude-sonnet-4-5"

    class Config:
        env_file = ".env"
        extra = "ignore"


settings = Settings()
