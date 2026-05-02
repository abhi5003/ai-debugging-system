from langchain_core.messages import SystemMessage, HumanMessage
from app.graph.state import AgentState
from app.llm.factory import LLMFactory
from app.graph.agent_config import DEEP_ANALYSIS_AGENT


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

    resp = await LLMFactory.create_chat_llm(
        max_tokens=DEEP_ANALYSIS_AGENT.max_tokens
    ).ainvoke([
        SystemMessage(content=DEEP_ANALYSIS_AGENT.system_prompt),
        HumanMessage(content=prompt)
    ])

    return {
        "root_cause": resp.content.strip(),
        "deep_analysis_done": True,
        "reasoning_trace": ["[deep_analysis] second pass executed"]
    }
