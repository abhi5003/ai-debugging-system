from typing import Protocol


class ResultFilter(Protocol):
    """Strategy interface for filtering web search results.

    The Strategy pattern allows swapping filtering algorithms
    (keyword, TF-IDF, semantic, LLM-based) without modifying the
    web_search_agent.  Each concrete strategy encapsulates one
    ranking / selection approach.
    """

    def filter(self, results: list[dict], query: str, top_k: int = 3) -> list[dict]: ...


class KeywordFilter:
    """Default filter using simple keyword overlap on the title.

    Splits the query text into words and keeps results whose title
    contains at least one keyword.  Returns at most ``top_k`` items.
    """

    def filter(self, results: list[dict], query: str, top_k: int = 3) -> list[dict]:
        keywords = query.lower().split()
        filtered = []

        for r in results:
            title = r.get("title", "").lower()
            if any(k in title for k in keywords):
                filtered.append(r)

        return filtered[:top_k]
