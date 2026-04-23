from pydantic import BaseModel
from datetime import datetime


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
