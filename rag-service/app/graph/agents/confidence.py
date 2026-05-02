import logging
from langchain_core.messages import SystemMessage, HumanMessage
from app.graph.state import AgentState
from app.llm.factory import LLMFactory
from app.config import settings
from app.utils import parse_llm_json_response
from app.graph.agent_config import CONFIDENCE_AGENT
from app.prompts.registry import build_confidence_prompt

log = logging.getLogger(__name__)


async def confidence_agent(state: AgentState) -> dict:
    inc = state["incident"]
    similar = state["similar_incidents"]
    attempts = state["retrieval_attempts"]

    prompt = build_confidence_prompt(state)

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

    error_rate = inc.metrics.error_rate_percent if inc.metrics else 0.0
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
