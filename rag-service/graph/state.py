from typing import TypedDict, Annotated
from langgraph.graph.message import add_messages
from models.incident import EnrichedIncident


class AgentState(TypedDict):
    # ── input ──────────────────────────────────────────────────────
    incident:             EnrichedIncident

    # ── retrieval agent ────────────────────────────────────────────
    embedding:            list[float]
    similar_incidents:    list[dict]
    retrieval_attempts:   int

    # ── analysis agent ─────────────────────────────────────────────
    root_cause:           str

    # ── resolution agent ───────────────────────────────────────────
    resolution:           str
    immediate_actions:    list[str]

    # ── confidence agent ───────────────────────────────────────────
    confidence:           float
    needs_reretrieval:    bool

    # ── shared append-only trace ───────────────────────────────────
    reasoning_trace: Annotated[list[str], add_messages]
