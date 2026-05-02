import logging
from langchain_core.messages import SystemMessage, HumanMessage
from app.graph.state import AgentState
from app.llm.factory import LLMFactory
from app.config import settings
from app.utils import parse_llm_json_response
from app.graph.agent_config import CONFIDENCE_AGENT

log = logging.getLogger(__name__)


async def confidence_agent(state: AgentState) -> dict:
    inc = state["incident"]
    similar = state["similar_incidents"]
    attempts = state["retrieval_attempts"]

    error_rate = inc.metrics.error_rate_percent if inc.metrics else 0.0
    open_probs = len(inc.traces.recent_problem_ids) if inc.traces else 0

    prompt = f"""ROOT CAUSE:    {state['root_cause']}
RESOLUTION:    {state['resolution']}
ACTIONS:       {state['immediate_actions']}

EVIDENCE:
- Similar incidents found: {len(similar)}
- Retrieval attempts:      {attempts}
- Incident priority:       {inc.priority}
- Error rate signal:       {error_rate:.1f}% (Dynatrace)
- Open Dynatrace problems: {open_probs}

Score the confidence of this analysis."""

    response = await LLMFactory.create_chat_llm(
        max_tokens=CONFIDENCE_AGENT.max_tokens
    ).ainvoke([
        SystemMessage(content=CONFIDENCE_AGENT.system_prompt),
        HumanMessage(content=prompt),
    ])

    raw = response.content.strip()
    data = parse_llm_json_response(raw, {"confidence": 0.5, "reason": "parse error"})

    confidence = float(data.get("confidence", 0.5))
    reason = data.get("reason", "")

    similar_count = len(similar)

    adjustment = 0.0

    if similar_count >= settings.confidence_boost_similar_count:
        adjustment += settings.confidence_boost_amount

    if similar_count == 0:
        adjustment -= settings.confidence_penalty_none

    if error_rate > settings.confidence_error_rate_threshold:
        adjustment += settings.confidence_boost_error_amount

    confidence = max(0.0, min(1.0, confidence + adjustment))

    needs_more = (
        confidence < settings.confidence_threshold
        and attempts < settings.max_retrieval_attempts
    )

    log.info("[confidence] score=%.2f needs_reretrieval=%s reason=%s",
             confidence, needs_more, reason)

    return {
        "confidence":        confidence,
        "needs_reretrieval": needs_more,
        "reasoning_trace": [
            f"[confidence] score={confidence:.2f}  reason={reason}  "
            f"{'→ re-retrieving' if needs_more else '→ accepted'}"
        ],
    }
