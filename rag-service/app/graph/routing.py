"""Routing strategies for the supervisor graph.

The Strategy pattern extracts the routing decision from the supervisor
so different fallback policies can be swapped without modifying
``supervisor.py``.  Each strategy receives the current agent state
and returns the name of the next node (or ``END``).
"""

from typing import Protocol
from langgraph.graph import END
from app.graph.state import AgentState
from app.config import settings


class RoutingStrategy(Protocol):
    def route(self, state: AgentState) -> str: ...


class DefaultFallbackRouting:
    """Current routing: retrieval → deep_analysis → web_search.

    Mirrors the original ``_route_after_confidence`` logic but
    uses configurable thresholds from ``settings``.
    """

    def route(self, state: AgentState) -> str:
        max_loops = state.get("max_loops", settings.max_retry_loops)
        threshold = settings.confidence_threshold

        if state["loop_count"] >= max_loops:
            return END

        state["loop_count"] += 1

        if state.get("needs_reretrieval", False):
            return "retrieval"

        if state["confidence"] < threshold and not state.get("deep_analysis_done", False):
            return "deep_analysis"

        if (
            state["confidence"] < threshold
            and state.get("deep_analysis_done", False)
            and not state.get("web_search_done", False)
        ):
            return "web_search"

        return END
