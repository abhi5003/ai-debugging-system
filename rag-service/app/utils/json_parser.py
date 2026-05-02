import json
import logging

log = logging.getLogger(__name__)


def parse_llm_json_response(content: str, fallback: dict) -> dict:
    """Parse JSON from LLM responses, handling markdown fence blocks.

    Applies the Template Method pattern to centralize the repeated
    JSON-extraction logic that previously lived in confidence.py and
    resolution.py. This eliminates DRY violations and provides a single
    point to adjust parsing behaviour (e.g. add more fence patterns
    or JSON5 support) without touching every agent.
    """
    raw = content.strip()
    raw = raw.replace("```json", "").replace("```", "").strip()

    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        log.warning("LLM JSON parse failed, using fallback")
        return fallback
