from langchain_core.messages import SystemMessage, HumanMessage
from app.graph.state import AgentState
from app.llm.factory import LLMFactory
from app.graph.agent_config import DEEP_ANALYSIS_AGENT
from app.prompts.registry import build_deep_analysis_prompt


async def deep_analysis_agent(state: AgentState) -> dict:
    prompt = build_deep_analysis_prompt(state)

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
