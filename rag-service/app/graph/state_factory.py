"""Factory functions for building AgentState and response models.

The Builder / Factory patterns encapsulate default values and
transformation logic so that callers don't need to know the internal
structure of ``AgentState`` or ``IncidentAnalysis``.  If either
schema changes, only this module needs updating.
"""

from app.config import settings
from app.graph.state import AgentState
from app.models.incident import EnrichedIncident
from app.models.analysis import IncidentAnalysis


def create_initial_state(incident: EnrichedIncident) -> AgentState:
    """Return a fresh ``AgentState`` with sensible defaults.

    Using a factory function instead of a raw dict literal prevents
    silent breakage when ``AgentState`` gains or loses fields, and
    centralises default values in one place.
    """
    return {
        "incident":           incident,
        "embedding":          [],
        "similar_incidents":  [],
        "retrieval_attempts": 0,
        "root_cause":         "",
        "resolution":         "",
        "immediate_actions":  [],
        "confidence":         0.0,
        "needs_reretrieval":  False,
        "reasoning_trace":    [],
        "deep_analysis_done": False,
        "web_search_done":    False,
        "web_results":        [],
        "loop_count":         0,
        "max_loops":          settings.max_retry_loops,
    }
