import json
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

_SYSTEM = """You are a senior SRE generating incident resolution plans.
Given a confirmed root cause, observability context, and past resolutions,
produce a concrete remediation plan.

Respond ONLY with valid JSON — no preamble, no markdown fences:
{
  "resolution": "clear numbered step-by-step resolution plan",
  "immediate_actions": ["action 1", "action 2", "action 3"]
}"""


async def resolution_agent(state: AgentState) -> dict:
    inc = state["incident"]
    root_cause = state["root_cause"]
    similar = state["similar_incidents"]

    past = "\n".join([
        f"- {s['number']}: {s.get('resolution', 'no resolution recorded')}"
        for s in similar
        if s.get("resolution")
    ]) or "No past resolutions available."

    downstream = (
        inc.topology.downstream_services if inc.topology else []
    )

    prompt = f"""ROOT CAUSE:
{root_cause}

INCIDENT:
Number:      {inc.number}
CI:          {inc.configuration_item or 'N/A'}
Priority:    {inc.priority}
Error rate:  {inc.metrics.error_rate_percent:.1f}% (Dynatrace)
Downstream services at risk: {downstream or 'none'}

PAST RESOLUTIONS FOR SIMILAR INCIDENTS:
{past}

Generate the resolution plan and immediate actions."""

    response = await _llm.ainvoke([
        SystemMessage(content=_SYSTEM),
        HumanMessage(content=prompt),
    ])

    raw = response.content.strip().replace("```json", "").replace("```", "").strip()

    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        log.warning("[resolution] JSON parse failed, using fallback")
        data = {
            "resolution": raw,
            "immediate_actions": ["Review incident manually", "Check service logs"],
        }

    log.info("[resolution] generated %d immediate actions",
             len(data.get("immediate_actions", [])))

    return {
        "resolution":        data.get("resolution", ""),
        "immediate_actions": data.get("immediate_actions", []),
        "reasoning_trace": [
            f"[resolution] {len(data.get('immediate_actions', []))} immediate actions generated"
        ],
    }
