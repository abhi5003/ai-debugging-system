import json
import logging
from langchain_anthropic import ChatAnthropic
from langchain_core.messages import SystemMessage, HumanMessage
from graph.state import AgentState
from config import settings

log = logging.getLogger(__name__)

_llm = ChatAnthropic(
    model=settings.llm_model,
    api_key=settings.anthropic_api_key,
    max_tokens=256,
)

_SYSTEM = """You are a quality evaluator for SRE incident analyses.
Score the confidence that the root cause and resolution are correct (0.0 to 1.0).

Consider:
- Specificity of the root cause (vague = lower score)
- Number and similarity of matching past incidents
- Alignment between Dynatrace signals and the stated root cause
- Actionability of the resolution steps

Respond ONLY with valid JSON:
{"confidence": 0.85, "reason": "one sentence explanation"}"""


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

    response = await _llm.ainvoke([
        SystemMessage(content=_SYSTEM),
        HumanMessage(content=prompt),
    ])

    raw = response.content.strip().replace("```json", "").replace("```", "").strip()
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        log.warning("[confidence] JSON parse failed, defaulting to 0.5")
        data = {"confidence": 0.5, "reason": "parse error"}

    confidence = float(data.get("confidence", 0.5))
    reason = data.get("reason", "")

    # ✅ SIGNAL-BASED CALIBRATION
    similar_count = len(similar)

    adjustment = 0.0

    # Boost if we have strong historical match
    if similar_count >= 3:
        adjustment += 0.10

    # Penalize if no similar incidents
    if similar_count == 0:
        adjustment -= 0.15

    # Boost if strong runtime signal
    if error_rate > 20:
        adjustment += 0.05

    # Apply and clamp
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
