from pydantic import BaseModel
from datetime import datetime, timezone


class IncidentAnalysis(BaseModel):
    sys_id:                   str
    number:                   str
    root_cause:               str
    resolution:               str
    immediate_actions:        list[str]
    confidence:               float
    similar_incident_numbers: list[str]
    agent_reasoning_trace:    list[str]
    retrieval_attempts:       int
    analyzed_at:              datetime

    @classmethod
    def from_state(cls, state: dict, incident) -> "IncidentAnalysis":
        """Factory method that builds an analysis response from the final agent state.

        Encapsulates the dict→model transformation so that changes to
        ``AgentState`` keys only need to be reflected here instead of
        every caller that builds the response.
        """
        return cls(
            sys_id=incident.sys_id,
            number=incident.number,
            root_cause=state["root_cause"],
            resolution=state["resolution"],
            immediate_actions=state["immediate_actions"],
            confidence=state["confidence"],
            similar_incident_numbers=[
                s["number"] for s in state["similar_incidents"]
            ],
            agent_reasoning_trace=state["reasoning_trace"],
            retrieval_attempts=state["retrieval_attempts"],
            analyzed_at=datetime.now(timezone.utc),
        )
