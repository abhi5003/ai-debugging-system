import asyncio
from app.graph.state import AgentState
from app.mcp.client import MCPClient
from app.mcp.filters import KeywordFilter
from app.config import settings

mcp = MCPClient.get_instance()
_filter = KeywordFilter()


async def web_search_agent(state: AgentState) -> dict:
    inc = state["incident"]

    query = f"{inc.short_description} error root cause fix troubleshooting"

    try:
        results = await asyncio.wait_for(
            mcp.web_search(query),
            timeout=settings.web_search_timeout,
        )

        filtered = _filter.filter(results, inc.short_description)

        if not filtered:
            return {
                "web_results": [],
                "web_search_done": True,
                "reasoning_trace": [
                    f"[web_search] no relevant results for query: {query}"
                ]
            }

        return {
            "web_results": filtered,
            "web_search_done": True,
            "reasoning_trace": [
                f"[web_search] raw={len(results)} filtered={len(filtered)} query='{query}'"
            ]
        }

    except Exception as e:
        return {
            "web_results": [],
            "web_search_done": True,
            "reasoning_trace": [
                f"[web_search] failed: {str(e)}"
            ]
        }
