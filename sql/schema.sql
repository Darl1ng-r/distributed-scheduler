-- Distributed Task Scheduler PostgreSQL Schema Initialization

CREATE TABLE IF NOT EXISTS task_schedules (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(255) NOT NULL,
    webhook_url TEXT NOT NULL,
    headers JSONB,
    max_retries INT DEFAULT 3,
    backoff_multiplier DOUBLE PRECISION DEFAULT 2.0,
    initial_interval_sec INT DEFAULT 5,
    timezone VARCHAR(50) DEFAULT 'UTC',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_run_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_executions (
    id VARCHAR(255) PRIMARY KEY,
    task_schedule_id VARCHAR(255) NOT NULL REFERENCES task_schedules(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    attempt INT NOT NULL DEFAULT 1,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    response_status INT
);

CREATE TABLE IF NOT EXISTS outbox_messages (
    id VARCHAR(255) PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_task_schedules_status ON task_schedules(status);
CREATE INDEX IF NOT EXISTS idx_task_executions_schedule_id_started ON task_executions(task_schedule_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_executions_status ON task_executions(status);
CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_messages(status, created_at ASC);
