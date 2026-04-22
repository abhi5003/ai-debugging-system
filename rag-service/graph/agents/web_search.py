from graph.state import AgentState
from mcp.client import MCPClient
import asyncio

mcp = MCPClient()

def filter_results(results, incident_text):
    filtered = []

    keywords = incident_text.lower().split()

    for r in results:
        title = r.get("title", "").lower()

        # simple relevance check
        if any(k in title for k in keywords):
            filtered.append(r)

    return filtered[:3]  # keep top 3 only



async def web_search_agent(state: AgentState) -> dict:
    inc = state["incident"]

    # 🔥 Better query (more structured)
    query = f"{inc.short_description} error root cause fix troubleshooting"

    try:
        # ✅ timeout protection
        results = await asyncio.wait_for(
            mcp.web_search(query),
            timeout=8
        )

        # ✅ filtering
        filtered = filter_results(results, inc.short_description)

        # ⚠️ fallback if nothing useful
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
        # ✅ never break pipeline
        return {
            "web_results": [],
            "web_search_done": True,
            "reasoning_trace": [
                f"[web_search] failed: {str(e)}"
            ]
        }