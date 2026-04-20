-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Incident embeddings table for RAG retrieval
CREATE TABLE IF NOT EXISTS incident_embeddings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sys_id      VARCHAR(64) UNIQUE NOT NULL,
    number      VARCHAR(32) NOT NULL,
    description TEXT NOT NULL,
    root_cause  TEXT,
    resolution  TEXT,
    priority    VARCHAR(16),
    embedding   vector(1536),
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- IVFFlat index for fast approximate nearest-neighbour search
CREATE INDEX IF NOT EXISTS incident_embeddings_idx
    ON incident_embeddings
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Seed with sample resolved incidents for bootstrapping
INSERT INTO incident_embeddings (sys_id, number, description, root_cause, resolution, priority)
VALUES
(
    'seed-001', 'INC0000001',
    'Payment service database connection pool exhausted causing 500 errors',
    'Connection pool size (10) too small for peak traffic load. Pool exhausted under 50+ concurrent requests.',
    'Increase connection pool size to 50 in application config. Restart payment-service pod. Monitor pg_stat_activity.',
    'CRITICAL'
),
(
    'seed-002', 'INC0000002',
    'Order service response time degraded above 5000ms SLA threshold',
    'N+1 query pattern in order listing endpoint fetching product details individually.',
    'Deploy fix with JOIN query replacing N+1 pattern. Add Redis cache for product details with 5min TTL.',
    'HIGH'
),
(
    'seed-003', 'INC0000003',
    'API gateway returning 503 service unavailable for all downstream calls',
    'Circuit breaker opened due to 95% error rate on auth-service after certificate expiry.',
    'Renew TLS certificate on auth-service. Reset circuit breaker. Verify health checks pass.',
    'CRITICAL'
),
(
    'seed-004', 'INC0000004',
    'High CPU usage on payment-service pods causing throttling',
    'Memory leak in connection handler — connections not closed after timeout, GC pressure causing CPU spike.',
    'Rolling restart of payment-service pods. Deploy hotfix with proper connection cleanup in finally block.',
    'HIGH'
),
(
    'seed-005', 'INC0000005',
    'Kafka consumer lag growing on incident-events topic processor-service group',
    'Consumer processing blocked on synchronous RAG HTTP call taking 45s timeout per message.',
    'Increase consumer thread pool from 3 to 10. Set RAG service timeout to 30s. Add async dispatch for MEDIUM severity.',
    'MEDIUM'
)
ON CONFLICT (sys_id) DO NOTHING;
