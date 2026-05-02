import logging
from langchain_core.messages import SystemMessage, HumanMessage
from app.graph.state import AgentState
from app.llm.factory import LLMFactory
from app.graph.agent_config import RESOLUTION_AGENT
from app.utils import parse_llm_json_response

log = logging.getLogger(__name__)


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

    response = await LLMFactory.create_chat_llm(
        max_tokens=RESOLUTION_AGENT.max_tokens
    ).ainvoke([
        SystemMessage(content=RESOLUTION_AGENT.system_prompt),
        HumanMessage(content=prompt),
    ])

    raw = response.content.strip()
    data = parse_llm_json_response(raw, {
        "resolution": raw,
        "immediate_actions": ["Review incident manually", "Check service logs"],
    })

    log.info("[resolution] generated %d immediate actions",
             len(data.get("immediate_actions", [])))

    return {
        "resolution":        data.get("resolution", ""),
        "immediate_actions": data.get("immediate_actions", []),
        "reasoning_trace": [
            f"[resolution] {len(data.get('immediate_actions', []))} immediate actions generated"
        ],
    }
