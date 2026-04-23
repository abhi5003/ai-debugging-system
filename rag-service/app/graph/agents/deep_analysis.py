from langchain_anthropic import ChatAnthropic
from langchain_core.messages import SystemMessage, HumanMessage
from app.graph.state import AgentState
from app.config import settings

_llm = ChatAnthropic(
    model=settings.llm_model,
    api_key=settings.anthropic_api_key,
    max_tokens=1200
)

_SYSTEM = """You are a senior SRE performing SECOND-PASS deep analysis.

The first attempt had LOW confidence.

You must:
- Re-evaluate metrics, traces, topology
- Generate 2–3 hypotheses
- Choose the MOST LIKELY root cause
- Explain why others are less likely

Avoid repeating previous reasoning.
Be precise and technical.
"""


async def deep_analysis_agent(state: AgentState) -> dict:
    inc = state["incident"]

    prompt = f"""
Incident: {inc.short_description}

Metrics:
CPU={inc.metrics.cpu_usage_percent if inc.metrics else 0}
ErrorRate={inc.metrics.error_rate_percent if inc.metrics else 0}

Traces:
Problems={inc.traces.recent_problem_ids if inc.traces else []}

Previous root cause:
{state.get("root_cause")}

Re-analyze deeply.
"""

    resp = await _llm.ainvoke([
        SystemMessage(content=_SYSTEM),
        HumanMessage(content=prompt)
    ])

    return {
        "root_cause": resp.content.strip(),
        "deep_analysis_done": True,
        "reasoning_trace": ["[deep_analysis] second pass executed"]
    }
