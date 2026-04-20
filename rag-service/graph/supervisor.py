from langgraph.graph import StateGraph, END
from graph.state import AgentState
from graph.agents.retrieval   import retrieval_agent
from graph.agents.analysis    import analysis_agent
from graph.agents.resolution  import resolution_agent
from graph.agents.confidence  import confidence_agent


def _route_after_confidence(state: AgentState) -> str:
    """Loop back to retrieval if confidence is low and retries remain."""
    if state.get("needs_reretrieval", False):
        return "retrieval"
    return END


def build_graph() -> StateGraph:
    graph = StateGraph(AgentState)

    graph.add_node("retrieval",  retrieval_agent)
    graph.add_node("analysis",   analysis_agent)
    graph.add_node("resolution", resolution_agent)
    graph.add_node("confidence", confidence_agent)

    graph.set_entry_point("retrieval")

    graph.add_edge("retrieval",  "analysis")
    graph.add_edge("analysis",   "resolution")
    graph.add_edge("resolution", "confidence")

    graph.add_conditional_edges(
        "confidence",
        _route_after_confidence,
        {"retrieval": "retrieval", END: END},
    )

    return graph.compile()


# Compiled once at module load — shared across all requests
rag_graph = build_graph()
