from pydantic import BaseModel
from typing import Optional
from datetime import datetime


class MetricsData(BaseModel):
    cpu_usage_percent:    float = 0.0
    memory_usage_percent: float = 0.0
    error_rate_percent:   float = 0.0
    response_time_ms:     float = 0.0


class TraceData(BaseModel):
    recent_problem_ids:   list[str] = []
    slow_span_operations: list[str] = []
    error_count:          int = 0


class TopologyData(BaseModel):
    upstream_services:   list[str] = []
    downstream_services: list[str] = []
    host_group:          Optional[str] = None


class EnrichedIncident(BaseModel):
    sys_id:             str
    number:             str
    short_description:  str
    priority:           str
    state:              str
    assigned_to:        Optional[str] = None
    configuration_item: Optional[str] = None
    updated_at:         Optional[datetime] = None
    metrics:            Optional[MetricsData] = None
    traces:             Optional[TraceData] = None
    topology:           Optional[TopologyData] = None
    enriched_at:        Optional[datetime] = None
