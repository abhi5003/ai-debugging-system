import logging
from langchain_core.messages import SystemMessage, HumanMessage
from app.graph.state import AgentState
from app.llm.factory import LLMFactory
from app.graph.agent_config import ANALYSIS_AGENT
from app.prompts.registry import build_analysis_prompt

log = logging.getLogger(__name__)


async def analysis_agent(state: AgentState) -> dict:
    prompt = build_analysis_prompt(state)

    response = await LLMFactory.create_chat_llm(
        max_tokens=ANALYSIS_AGENT.max_tokens
    ).ainvoke([
        SystemMessage(content=ANALYSIS_AGENT.system_prompt),
        HumanMessage(content=prompt),
    ])

    root_cause = response.content.strip()

    log.info("[analysis] root_cause=%s", root_cause[:120])

    return {
        "root_cause": root_cause,
        "reasoning_trace": [f"[analysis] {root_cause[:120]}"],
    }
