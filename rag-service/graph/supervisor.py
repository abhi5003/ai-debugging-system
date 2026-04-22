from asyncio import graph
from langgraph.graph import StateGraph, END
from graph.state import AgentState
from graph.agents.retrieval   import retrieval_agent
from graph.agents.analysis    import analysis_agent
from graph.agents.resolution  import resolution_agent
from graph.agents.confidence  import confidence_agent
from graph.agents.deep_analysis import deep_analysis_agent
from graph.agents.web_search import web_search_agent


def _route_after_confidence(state: AgentState) -> str:
    
    # ✅ HARD STOP (first check)
    if state["loop_count"] >= state.get("max_loops", 3):
        return END

    # increment loop count
    state["loop_count"] += 1

    # 1. Retry retrieval if allowed
    if state.get("needs_reretrieval", False):
        return "retrieval"

    # 2. Deep analysis (first fallback)
    if (
        state["confidence"] < 0.7 and
        not state.get("deep_analysis_done", False)
    ):
        return "deep_analysis"

    # 3. Web search (final fallback)
    if (
        state["confidence"] < 0.7 and
        state.get("deep_analysis_done", False) and
        not state.get("web_search_done", False)
    ):
        return "web_search"

    return END


def build_graph() -> StateGraph:
    graph = StateGraph(AgentState)

    # Nodes
    graph.add_node("retrieval", retrieval_agent)
    graph.add_node("analysis", analysis_agent)
    graph.add_node("resolution", resolution_agent)
    graph.add_node("confidence", confidence_agent)
    graph.add_node("deep_analysis", deep_analysis_agent)
    graph.add_node("web_search", web_search_agent)

    # Entry
    graph.set_entry_point("retrieval")

    # Main flow
    graph.add_edge("retrieval", "analysis")
    graph.add_edge("analysis", "resolution")
    graph.add_edge("resolution", "confidence")

    # ✅ Fallback paths (YOU WERE MISSING THESE)
    graph.add_edge("deep_analysis", "resolution")
    graph.add_edge("web_search", "analysis")

    # ✅ Conditional routing (FIXED)
    graph.add_conditional_edges(
        "confidence",
        _route_after_confidence,
        {
            "retrieval": "retrieval",
            "deep_analysis": "deep_analysis",
            "web_search": "web_search",
            END: END,
        },
    )

    return graph.compile()


# Compiled once at module load — shared across all requests
rag_graph = build_graph()
