from langgraph.graph import StateGraph, END
from app.graph.state import AgentState
from app.graph.agents.retrieval import retrieval_agent
from app.graph.agents.analysis import analysis_agent
from app.graph.agents.resolution import resolution_agent
from app.graph.agents.confidence import confidence_agent
from app.graph.agents.deep_analysis import deep_analysis_agent
from app.graph.agents.web_search import web_search_agent


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

    # Nodes (renamed all to *_agent)
    graph.add_node("retrieval_agent", retrieval_agent)
    graph.add_node("analysis_agent", analysis_agent)
    graph.add_node("resolution_agent", resolution_agent)
    graph.add_node("confidence_agent", confidence_agent)
    graph.add_node("deep_analysis_agent", deep_analysis_agent)
    graph.add_node("web_search_agent", web_search_agent)

    # Entry
    graph.set_entry_point("retrieval_agent")

    # Main flow
    graph.add_edge("retrieval_agent", "analysis_agent")
    graph.add_edge("analysis_agent", "resolution_agent")
    graph.add_edge("resolution_agent", "confidence_agent")

    # Fallback paths
    graph.add_edge("deep_analysis_agent", "resolution_agent")
    graph.add_edge("web_search_agent", "analysis_agent")

    # Conditional routing
    graph.add_conditional_edges(
        "confidence_agent",
        _route_after_confidence,
        {
            "retrieval": "retrieval_agent",
            "deep_analysis": "deep_analysis_agent",
            "web_search": "web_search_agent",
            END: END,
        },
    )

    return graph.compile()


rag_graph = build_graph()