import httpx
from app.config import settings

TAVILY_URL = settings.tavily_url


async def web_search(query: str) -> list[dict]:
    payload = {
        "api_key": settings.tavily_api_key,
        "query": query,
        "search_depth": "advanced",   # better results
        "max_results": 5,
        "include_answer": False
    }

    async with httpx.AsyncClient() as client:
        resp = await client.post(TAVILY_URL, json=payload, timeout=8)
        resp.raise_for_status()
        data = resp.json()

    results = []

    for r in data.get("results", []):
        results.append({
            "title": r.get("title"),
            "url": r.get("url"),
            "content": r.get("content")[:300] if r.get("content") else ""
        })

    return results
