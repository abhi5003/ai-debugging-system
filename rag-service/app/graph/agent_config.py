"""Agent configuration dataclasses.

Each agent has a name, system prompt, and max_tokens.  System prompts
are now referenced from the central prompt registry to keep prompt
definitions in one place.
"""

from dataclasses import dataclass
from app.prompts.registry import (
    ANALYSIS_SYSTEM,
    RESOLUTION_SYSTEM,
    DEEP_ANALYSIS_SYSTEM,
    CONFIDENCE_SYSTEM,
)


@dataclass(frozen=True)
class AgentConfig:
    name: str
    system_prompt: str
    max_tokens: int = 1024
    temperature: float = 0.0


# Default configurations for every agent in the pipeline.
# System prompts are imported from the prompt registry.
ANALYSIS_AGENT = AgentConfig(
    name="analysis",
    system_prompt=ANALYSIS_SYSTEM,
    max_tokens=1024,
)

RESOLUTION_AGENT = AgentConfig(
    name="resolution",
    system_prompt=RESOLUTION_SYSTEM,
    max_tokens=1024,
)

DEEP_ANALYSIS_AGENT = AgentConfig(
    name="deep_analysis",
    system_prompt=DEEP_ANALYSIS_SYSTEM,
    max_tokens=1200,
)

CONFIDENCE_AGENT = AgentConfig(
    name="confidence",
    system_prompt=CONFIDENCE_SYSTEM,
    max_tokens=256,
)

AGENT_CONFIGS: dict[str, AgentConfig] = {
    "analysis": ANALYSIS_AGENT,
    "resolution": RESOLUTION_AGENT,
    "deep_analysis": DEEP_ANALYSIS_AGENT,
    "confidence": CONFIDENCE_AGENT,
}
