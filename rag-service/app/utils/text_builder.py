"""Builder for incident text representations.

The Builder pattern centralises the logic for converting an
``EnrichedIncident`` into various string formats.  Both
``for_embedding()`` and ``for_search_context()`` share a common
``_extract_fields()`` step, eliminating the duplicated field-access
code that previously lived in two separate functions inside
``retrieval.py``.
"""

from app.models.incident import EnrichedIncident


class IncidentTextBuilder:
    """Extracts incident fields once and formats them for different uses."""

    def __init__(self, incident: EnrichedIncident):
        self.incident = incident

    # ── shared field extraction ──────────────────────────────────────────

    def _extract_fields(self) -> dict:
        inc = self.incident
        fields: dict = {}

        fields["description"] = inc.short_description
        fields["priority"] = inc.priority
        fields["ci"] = inc.configuration_item or "unknown"

        if inc.metrics:
            fields["error_rate"] = f"{inc.metrics.error_rate_percent:.1f}%"
            fields["response_time"] = f"{inc.metrics.response_time_ms:.0f}ms"
            fields["cpu"] = f"{inc.metrics.cpu_usage_percent:.1f}%"
            fields["error_rate_val"] = inc.metrics.error_rate_percent
            fields["response_time_val"] = inc.metrics.response_time_ms
            fields["cpu_val"] = inc.metrics.cpu_usage_percent

        if inc.traces:
            fields["open_problems"] = len(inc.traces.recent_problem_ids)
            fields["error_count"] = inc.traces.error_count
            if inc.traces.slow_span_operations:
                fields["slow_spans"] = ", ".join(inc.traces.slow_span_operations)
                fields["slow_spans_list"] = inc.traces.slow_span_operations

        if inc.topology:
            if inc.topology.upstream_services:
                fields["upstream"] = ", ".join(inc.topology.upstream_services)
            if inc.topology.downstream_services:
                fields["downstream"] = ", ".join(inc.topology.downstream_services)
            fields["host_group"] = inc.topology.host_group or "N/A"

        return fields

    # ── concrete representations ─────────────────────────────────────────

    def for_embedding(self) -> str:
        """Compact, colon-separated text optimised for vector embeddings."""
        f = self._extract_fields()
        parts = [
            f"description: {f['description']}",
            f"priority: {f['priority']}",
            f"ci: {f['ci']}",
        ]

        if "error_rate" in f:
            parts += [
                f"error_rate: {f['error_rate']}",
                f"response_time: {f['response_time']}",
                f"cpu: {f['cpu']}",
            ]

        if "open_problems" in f:
            parts.append(f"open_problems: {f['open_problems']}")
            if "slow_spans" in f:
                parts.append(f"slow_spans: {f['slow_spans']}")

        if "upstream" in f:
            parts.append(f"upstream: {f['upstream']}")
        if "downstream" in f:
            parts.append(f"downstream: {f['downstream']}")

        return "\n".join(parts)

    def for_search_context(self) -> str:
        """Human-readable, comma-separated text for MCP context search."""
        f = self._extract_fields()
        parts = [f["description"]]

        if self.incident.configuration_item:
            parts.append(f"service: {self.incident.configuration_item}")

        if "error_rate_val" in f:
            if f["error_rate_val"] > 0:
                parts.append(f"error rate {f['error_rate']}")
            if f["response_time_val"] > 0:
                parts.append(f"response time {f['response_time']}")
            if f["cpu_val"] > 80:
                parts.append(f"cpu usage {f['cpu']}")

        if "slow_spans" in f:
            parts.append(f"slow operations: {f['slow_spans']}")

        return ", ".join(parts)
