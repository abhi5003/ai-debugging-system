from langgraph.graph import StateGraph, END
from app.graph.state import AgentState
from app.graph.agents.retrieval import retrieval_agent
from app.graph.agents.analysis import analysis_agent
from app.graph.agents.resolution import resolution_agent
from app.graph.agents.confidence import confidence_agent
from app.graph.agents.deep_analysis import deep_analysis_agent
from app.graph.agents.web_search import web_search_agent
from app.graph.routing import DefaultFallbackRouting

# Strategy instance for conditional routing.
# Swapping this for a different RoutingStrategy changes the
# fallback behaviour without touching the rest of the graph.
_routing = DefaultFallbackRouting()


def _route_after_confidence(state: AgentState) -> str:
    return _routing.route(state)


def build_graph() -> StateGraph:
    graph = StateGraph(AgentState)

    graph.add_node("retrieval_agent", retrieval_agent)
    graph.add_node("analysis_agent", analysis_agent)
    graph.add_node("resolution_agent", resolution_agent)
    graph.add_node("confidence_agent", confidence_agent)
    graph.add_node("deep_analysis_agent", deep_analysis_agent)
    graph.add_node("web_search_agent", web_search_agent)

    graph.set_entry_point("retrieval_agent")

    graph.add_edge("retrieval_agent", "analysis_agent")
    graph.add_edge("analysis_agent", "resolution_agent")
    graph.add_edge("resolution_agent", "confidence_agent")

    graph.add_edge("deep_analysis_agent", "resolution_agent")
    graph.add_edge("web_search_agent", "analysis_agent")

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
