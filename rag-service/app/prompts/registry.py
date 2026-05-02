from app.graph.state import AgentState
from app.models.incident import EnrichedIncident


class PromptRegistry:
    # ── SYSTEM PROMPTS ────────────────────────────────────────────
    ANALYSIS_SYSTEM = """You are a senior SRE performing incident root cause analysis.

You receive:
1. A current incident with live observability data (metrics, traces, topology)
2. Similar past incidents (some HUMAN-verified, some AI-generated)

STRICT RULES:
- Always prioritize HUMAN-verified incidents over AI-generated ones
- Use AI-generated incidents only if HUMAN data is insufficient
- Higher confidence incidents are more reliable
- Observability signals (metrics, traces, topology) must be the primary evidence
- Do NOT blindly copy past incidents — validate against current signals

Your task:
Identify the single most likely ROOT CAUSE.

Be precise:
- Name the component
- Describe the failure mode
- Mention the key signal confirming it

Respond with only the root cause in 1–2 sentences.
No JSON. No explanation. No preamble."""

    RESOLUTION_SYSTEM = """You are a senior SRE generating incident resolution plans.
Given a confirmed root cause, observability context, and past resolutions,
produce a concrete remediation plan.

Respond ONLY with valid JSON — no preamble, no markdown fences:
{
  "resolution": "clear numbered step-by-step resolution plan",
  "immediate_actions": ["action 1", "action 2", "action 3"]
}"""

    DEEP_ANALYSIS_SYSTEM = """You are a senior SRE performing SECOND-PASS deep analysis.

The first attempt had LOW confidence.

You must:
- Re-evaluate metrics, traces, topology
- Generate 2–3 hypotheses
- Choose the MOST LIKELY root cause
- Explain why others are less likely

Avoid repeating previous reasoning.
Be precise and technical."""

    CONFIDENCE_SYSTEM = """You are a quality evaluator for SRE incident analyses.
Score the confidence that the root cause and resolution are correct (0.0 to 1.0).

Consider:
- Specificity of the root cause (vague = lower score)
- Number and similarity of matching past incidents
- Alignment between Dynatrace signals and the stated root cause
- Actionability of the resolution steps

Respond ONLY with valid JSON:
{"confidence": 0.85, "reason": "one sentence explanation"}"""

    # ── REUSABLE SNIPPETS ─────────────────────────────────────────
    METRICS_SNIPPET = """── Dynatrace metrics ──
CPU usage:     {cpu_usage_percent:.1f}%
Memory usage:  {memory_usage_percent:.1f}%
Error rate:    {error_rate_percent:.1f}%
Response time: {response_time_ms:.0f} ms"""

    TRACES_SNIPPET = """── Dynatrace traces ──
Open problems: {recent_problem_ids}
Error count:   {error_count}
Slow spans:    {slow_span_operations}"""

    TOPOLOGY_SNIPPET = """── Topology ──
Upstream:   {upstream_services}
Downstream: {downstream_services}
Host group: {host_group}"""

    # ── USER PROMPTS ───────────────────────────────────────────────
    ANALYSIS_USER = """=== CURRENT INCIDENT ===
Number:      {number}
Description: {short_description}
Priority:    {priority}
State:       {state}
CI:          {configuration_item}
Assigned to: {assigned_to}

{metrics}

{traces}

{topology}

=== SIMILAR RESOLVED INCIDENTS ===
{similar_incidents}

=== WEB SEARCH RESULTS ===
{web_results}

What is the root cause of this incident?"""

    CONFIDENCE_USER = """ROOT CAUSE:    {root_cause}
RESOLUTION:    {resolution}
ACTIONS:       {immediate_actions}

EVIDENCE:
- Similar incidents found: {similar_count}
- Retrieval attempts:      {retrieval_attempts}
- Incident priority:       {priority}
- Error rate signal:       {error_rate:.1f}% (Dynatrace)
- Open Dynatrace problems: {open_problems}

Score the confidence of this analysis."""

    RESOLUTION_USER = """ROOT CAUSE:
{root_cause}

INCIDENT:
Number:      {number}
CI:          {configuration_item}
Priority:    {priority}
Error rate:  {error_rate:.1f}% (Dynatrace)
Downstream services at risk: {downstream_services}

PAST RESOLUTIONS FOR SIMILAR INCIDENTS:
{past_resolutions}

Generate the resolution plan and immediate actions."""

    DEEP_ANALYSIS_USER = """Incident: {short_description}

Metrics:
CPU={cpu_usage}
ErrorRate={error_rate}

Traces:
Problems={recent_problem_ids}

Previous root cause:
{root_cause}

Re-analyze deeply."""


# ── RENDERING HELPERS ────────────────────────────────────────────
def _format_metrics(incident: EnrichedIncident) -> str:
    if not incident.metrics:
        return ""
    return PromptRegistry.METRICS_SNIPPET.format(
        cpu_usage_percent=incident.metrics.cpu_usage_percent,
        memory_usage_percent=incident.metrics.memory_usage_percent,
        error_rate_percent=incident.metrics.error_rate_percent,
        response_time_ms=incident.metrics.response_time_ms,
    )


def _format_traces(incident: EnrichedIncident) -> str:
    if not incident.traces:
        return ""
    return PromptRegistry.TRACES_SNIPPET.format(
        recent_problem_ids=incident.traces.recent_problem_ids or "none",
        error_count=incident.traces.error_count,
        slow_span_operations=incident.traces.slow_span_operations or "none",
    )


def _format_topology(incident: EnrichedIncident) -> str:
    if not incident.topology:
        return ""
    return PromptRegistry.TOPOLOGY_SNIPPET.format(
        upstream_services=incident.topology.upstream_services or "none",
        downstream_services=incident.topology.downstream_services or "none",
        host_group=incident.topology.host_group or "N/A",
    )


def _format_similar_incidents(similar: list[dict]) -> str:
    if not similar:
        return "No similar incidents found."
    lines = []
    for i, s in enumerate(similar, 1):
        sim_pct = float(s.get("similarity", 0)) * 100
        source = s.get("source", "AI")
        confidence = s.get("confidence", 0.0)
        lines.append(f"[{i}] {s['number']} (similarity {sim_pct:.0f}%)")
        lines.append(f"    Source: {source} (confidence={confidence:.2f})")
        lines.append(f"    Root cause: {s.get('root_cause')}")
        lines.append(f"    Resolution: {s.get('resolution')}")
        lines.append("")
    return "\n".join(lines)


def _format_web_results(web_results: list[dict]) -> str:
    if not web_results:
        return "No web results available."
    lines = []
    for r in web_results:
        lines.append(f"- {r.get('title')}")
        if r.get("content"):
            lines.append(f"  {r.get('content')[:150]}")
    return "\n".join(lines)


def build_analysis_prompt(state: AgentState) -> str:
    inc = state["incident"]
    return PromptRegistry.ANALYSIS_USER.format(
        number=inc.number,
        short_description=inc.short_description,
        priority=inc.priority,
        state=inc.state,
        configuration_item=inc.configuration_item or "N/A",
        assigned_to=inc.assigned_to or "unassigned",
        metrics=_format_metrics(inc),
        traces=_format_traces(inc),
        topology=_format_topology(inc),
        similar_incidents=_format_similar_incidents(state["similar_incidents"]),
        web_results=_format_web_results(state.get("web_results", [])),
    )


def build_confidence_prompt(state: AgentState) -> str:
    inc = state["incident"]
    error_rate = inc.metrics.error_rate_percent if inc.metrics else 0.0
    open_probs = len(inc.traces.recent_problem_ids) if inc.traces else 0
    return PromptRegistry.CONFIDENCE_USER.format(
        root_cause=state["root_cause"],
        resolution=state["resolution"],
        immediate_actions=state["immediate_actions"],
        similar_count=len(state["similar_incidents"]),
        retrieval_attempts=state["retrieval_attempts"],
        priority=inc.priority,
        error_rate=error_rate,
        open_problems=open_probs,
    )


def build_resolution_prompt(state: AgentState) -> str:
    inc = state["incident"]
    similar = state["similar_incidents"]
    past = "\n".join([
        f"- {s['number']}: {s.get('resolution', 'no resolution recorded')}"
        for s in similar if s.get("resolution")
    ]) or "No past resolutions available."
    downstream = (
        inc.topology.downstream_services if inc.topology else []
    )
    return PromptRegistry.RESOLUTION_USER.format(
        root_cause=state["root_cause"],
        number=inc.number,
        configuration_item=inc.configuration_item or "N/A",
        priority=inc.priority,
        error_rate=inc.metrics.error_rate_percent if inc.metrics else 0.0,
        downstream_services=downstream or "none",
        past_resolutions=past,
    )


def build_deep_analysis_prompt(state: AgentState) -> str:
    inc = state["incident"]
    return PromptRegistry.DEEP_ANALYSIS_USER.format(
        short_description=inc.short_description,
        cpu_usage=inc.metrics.cpu_usage_percent if inc.metrics else 0,
        error_rate=inc.metrics.error_rate_percent if inc.metrics else 0,
        recent_problem_ids=inc.traces.recent_problem_ids if inc.traces else [],
        root_cause=state.get("root_cause"),
    )


# Aliases for backward compatibility with agent_config.py
ANALYSIS_SYSTEM = PromptRegistry.ANALYSIS_SYSTEM
RESOLUTION_SYSTEM = PromptRegistry.RESOLUTION_SYSTEM
DEEP_ANALYSIS_SYSTEM = PromptRegistry.DEEP_ANALYSIS_SYSTEM
CONFIDENCE_SYSTEM = PromptRegistry.CONFIDENCE_SYSTEM
