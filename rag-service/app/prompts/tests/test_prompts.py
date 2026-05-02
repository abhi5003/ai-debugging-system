import pytest
from app.prompts.registry import (
    build_analysis_prompt,
    build_confidence_prompt,
    build_resolution_prompt,
    build_deep_analysis_prompt,
    PromptRegistry,
)
from app.models.incident import (
    EnrichedIncident,
    MetricsData,
    TraceData,
    TopologyData,
)
from datetime import datetime


def create_sample_incident():
    return EnrichedIncident(
        sys_id="123",
        number="INC001",
        short_description="High error rate on payment service",
        priority="1",
        state="Active",
        assigned_to="jdoe",
        configuration_item="payment-service",
        metrics=MetricsData(
            cpu_usage_percent=85.0,
            memory_usage_percent=70.0,
            error_rate_percent=25.0,
            response_time_ms=1200,
        ),
        traces=TraceData(
            recent_problem_ids=["PROB001"],
            error_count=15,
            slow_span_operations=["process_payment"],
        ),
        topology=TopologyData(
            upstream_services=["auth-service"],
            downstream_services=["database"],
            host_group="payment-hg",
        ),
        updated_at=datetime.now(),
    )


def create_sample_state():
    return {
        "incident": create_sample_incident(),
        "similar_incidents": [
            {
                "number": "INC002",
                "similarity": 0.85,
                "source": "HUMAN",
                "confidence": 0.9,
                "root_cause": "DB connection leak",
                "resolution": "Restart connection pool",
            },
        ],
        "retrieval_attempts": 1,
        "root_cause": "DB connection leak in payment service",
        "resolution": "Restart connection pool",
        "immediate_actions": ["Restart pool", "Check logs"],
        "web_results": [],
        "reasoning_trace": [],
    }


def test_analysis_prompt_renders():
    state = create_sample_state()
    prompt = build_analysis_prompt(state)
    assert "INC001" in prompt
    assert "payment-service" in prompt
    assert "85.0%" in prompt  # CPU usage
    assert "INC002" in prompt  # Similar incident
    assert "What is the root cause" in prompt


def test_analysis_prompt_contains_dynatrace_data():
    state = create_sample_state()
    prompt = build_analysis_prompt(state)
    assert "Dynatrace metrics" in prompt
    assert "Dynatrace traces" in prompt
    assert "Topology" in prompt


def test_analysis_prompt_no_metrics():
    state = create_sample_state()
    state["incident"].metrics = None
    prompt = build_analysis_prompt(state)
    assert "Dynatrace metrics" not in prompt


def test_analysis_prompt_no_traces():
    state = create_sample_state()
    state["incident"].traces = None
    prompt = build_analysis_prompt(state)
    assert "Dynatrace traces" not in prompt


def test_analysis_prompt_no_topology():
    state = create_sample_state()
    state["incident"].topology = None
    prompt = build_analysis_prompt(state)
    assert "Topology" not in prompt


def test_analysis_prompt_no_similar_incidents():
    state = create_sample_state()
    state["similar_incidents"] = []
    prompt = build_analysis_prompt(state)
    assert "No similar incidents found." in prompt


def test_analysis_prompt_with_web_results():
    state = create_sample_state()
    state["web_results"] = [
        {"title": "Payment service error fix", "content": "How to fix payment errors"}
    ]
    prompt = build_analysis_prompt(state)
    assert "Payment service error fix" in prompt


def test_confidence_prompt_renders():
    state = create_sample_state()
    prompt = build_confidence_prompt(state)
    assert "DB connection leak" in prompt
    assert "Restart connection pool" in prompt
    assert "25.0%" in prompt  # Error rate
    assert "Similar incidents found: 1" in prompt


def test_confidence_prompt_no_similar():
    state = create_sample_state()
    state["similar_incidents"] = []
    prompt = build_confidence_prompt(state)
    assert "Similar incidents found: 0" in prompt


def test_resolution_prompt_renders():
    state = create_sample_state()
    prompt = build_resolution_prompt(state)
    assert "DB connection leak" in prompt
    assert "INC001" in prompt
    assert "payment-service" in prompt
    assert "INC002" in prompt  # Past resolution


def test_resolution_prompt_no_past_resolutions():
    state = create_sample_state()
    state["similar_incidents"] = [{"number": "INC002"}]  # No resolution key
    prompt = build_resolution_prompt(state)
    assert "No past resolutions available." in prompt


def test_deep_analysis_prompt_renders():
    state = create_sample_state()
    prompt = build_deep_analysis_prompt(state)
    assert "High error rate on payment service" in prompt
    assert "85.0" in prompt  # CPU
    assert "DB connection leak" in prompt  # Previous root cause


def test_deep_analysis_prompt_no_metrics():
    state = create_sample_state()
    state["incident"].metrics = None
    prompt = build_deep_analysis_prompt(state)
    assert "CPU=0" in prompt
    assert "ErrorRate=0" in prompt


def test_deep_analysis_prompt_no_traces():
    state = create_sample_state()
    state["incident"].traces = None
    prompt = build_deep_analysis_prompt(state)
    assert "Problems=[]" in prompt


def test_metrics_snippet_formatting():
    inc = create_sample_incident()
    from app.prompts.registry import _format_metrics
    result = _format_metrics(inc)
    assert "85.0%" in result
    assert "70.0%" in result
    assert "25.0%" in result
    assert "1200" in result


def test_traces_snippet_formatting():
    inc = create_sample_incident()
    from app.prompts.registry import _format_traces
    result = _format_traces(inc)
    assert "PROB001" in result
    assert "15" in result
    assert "process_payment" in result


def test_similar_incidents_formatting():
    similar = [
        {
            "number": "INC002",
            "similarity": 0.85,
            "source": "HUMAN",
            "confidence": 0.9,
            "root_cause": "DB issue",
            "resolution": "Restart",
        },
    ]
    from app.prompts.registry import _format_similar_incidents
    result = _format_similar_incidents(similar)
    assert "INC002" in result
    assert "85%" in result
    assert "HUMAN" in result


def test_system_prompts_exist():
    assert PromptRegistry.ANALYSIS_SYSTEM
    assert PromptRegistry.RESOLUTION_SYSTEM
    assert PromptRegistry.DEEP_ANALYSIS_SYSTEM
    assert PromptRegistry.CONFIDENCE_SYSTEM


def test_system_prompts_contain_keywords():
    assert "root cause" in PromptRegistry.ANALYSIS_SYSTEM.lower()
    assert "JSON" in PromptRegistry.RESOLUTION_SYSTEM
    assert "SECOND-PASS" in PromptRegistry.DEEP_ANALYSIS_SYSTEM
    assert "confidence" in PromptRegistry.CONFIDENCE_SYSTEM.lower()
