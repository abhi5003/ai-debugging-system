import logging
from langchain_anthropic import ChatAnthropic
from langchain_core.messages import SystemMessage, HumanMessage
from app.graph.state import AgentState
from app.config import settings

log = logging.getLogger(__name__)

_llm = ChatAnthropic(
    model=settings.llm_model,
    api_key=settings.anthropic_api_key,
    max_tokens=1024,
)

_SYSTEM = """You are a senior SRE performing incident root cause analysis.
You receive a current incident with live observability data already collected
(Dynatrace metrics, traces, topology) plus similar past resolved incidents.

Identify the single most likely ROOT CAUSE. Be specific — name the
component, the failure mode, and which signal confirms it.
Respond with only the root cause in one or two sentences. No JSON. No preamble."""


def _build_prompt(state: AgentState) -> str:
    inc = state["incident"]
    similar = state["similar_incidents"]

    lines = [
        "=== CURRENT INCIDENT ===",
        f"Number:      {inc.number}",
        f"Description: {inc.short_description}",
        f"Priority:    {inc.priority}",
        f"State:       {inc.state}",
        f"CI:          {inc.configuration_item or 'N/A'}",
        f"Assigned to: {inc.assigned_to or 'unassigned'}",
    ]

    if inc.metrics:
        lines += [
            "",
            "── Dynatrace metrics (from Java enrichment) ──",
            f"CPU usage:     {inc.metrics.cpu_usage_percent:.1f}%",
            f"Memory usage:  {inc.metrics.memory_usage_percent:.1f}%",
            f"Error rate:    {inc.metrics.error_rate_percent:.1f}%",
            f"Response time: {inc.metrics.response_time_ms:.0f} ms",
        ]

    if inc.traces:
        lines += [
            "",
            "── Dynatrace traces (from Java enrichment) ──",
            f"Open problems: {inc.traces.recent_problem_ids or 'none'}",
            f"Error count:   {inc.traces.error_count}",
            f"Slow spans:    {inc.traces.slow_span_operations or 'none'}",
        ]

    if inc.topology:
        lines += [
            "",
            "── Topology (from Java enrichment) ──",
            f"Upstream:   {inc.topology.upstream_services or 'none'}",
            f"Downstream: {inc.topology.downstream_services or 'none'}",
            f"Host group: {inc.topology.host_group or 'N/A'}",
        ]

    lines += ["", "=== SIMILAR RESOLVED INCIDENTS ==="]
    if similar:
        for i, s in enumerate(similar, 1):
            sim_pct = float(s.get("similarity", 0)) * 100
            lines += [
                f"[{i}] {s['number']}  (similarity {sim_pct:.0f}%)",
                f"    Description: {s['description']}",
                f"    Root cause:  {s['root_cause']}",
                "",

            ]

    if state.get("web_results"):
        lines += ["", "=== WEB SEARCH RESULTS ==="]
        for r in state["web_results"]:
            lines.append(f"- {r.get('title')}")
            if r.get("content"):
                lines.append(f"  {r.get('content')[:150]}")
    else:
        lines.append("None found — analyze from observability signals only.")

    lines += ["", "What is the root cause of this incident?"]
    return "\n".join(lines)


async def analysis_agent(state: AgentState) -> dict:
    prompt = _build_prompt(state)
    response = await _llm.ainvoke([
        SystemMessage(content=_SYSTEM),
        HumanMessage(content=prompt),
    ])
    root_cause = response.content.strip()
    log.info("[analysis] root_cause=%s", root_cause[:80])

    return {
        "root_cause":      root_cause,
        "reasoning_trace": [f"[analysis] {root_cause[:120]}"],
    }
