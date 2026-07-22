-- Sample Task Schedules Seed Data

INSERT INTO task_schedules (id, name, cron_expression, webhook_url, headers, max_retries, backoff_multiplier, initial_interval_sec, status)
VALUES 
  ('sched-001', 'Nightly Billing Sync', '0 0 2 * * ?', 'https://api.mycompany.internal/webhooks/billing-sync', '{"Authorization": "Bearer sample-token"}'::jsonb, 3, 2.0, 5, 'ACTIVE'),
  ('sched-002', 'Hourly Health Monitor Check', '0 0 * * * ?', 'https://api.mycompany.internal/webhooks/health-check', '{"X-System-ID": "scheduler-v1"}'::jsonb, 5, 2.0, 2, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;
