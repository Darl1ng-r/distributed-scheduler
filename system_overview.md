# System Overview: Distributed Task Orchestrator & Scheduler

This document provides a high-level conceptual overview, feature checklist, technical requirements, and operational expectations for the Distributed Task Orchestrator & Scheduler system.

---

## 1. Core Vision & Idea
Modern enterprise architectures rely heavily on scheduling background tasks (e.g., generating nightly reports, synchronizing user accounts, executing automated billing cycles). Running these schedules in single, standalone cron jobs introduces single points of failure (SPOF) and doesn't scale.

The **Distributed Task Orchestrator & Scheduler** solves this by separating scheduling logic, coordination, and task execution into independent, scalable components:
*   **Decoupled Scheduling**: Coordinators determine *when* tasks run, but do not execute them.
*   **Reliable Queueing**: Tasks are sent to RabbitMQ to guarantee delivery.
*   **Stateless Workers**: Workers consume tasks from the queue and run them concurrently using Java 21 Virtual Threads.
*   **Secure External Execution**: Workers do not execute arbitrary shell commands or scripts locally; they perform authenticated HTTP webhooks to target microservices, keeping the system secure.

---

## 2. Core Feature Set

| Feature | Description | Business/Technical Value |
| :--- | :--- | :--- |
| **Dynamic Cron Scheduling** | Register, pause, update, or delete schedules at runtime via REST API. | Avoids redeploying code to change task schedules. |
| **High Availability (HA)** | Multiple Coordinators run simultaneously; one acts as Active Leader via Redis lock. | Zero downtime if the primary scheduler node crashes. |
| **Scalable Worker Pools** | Add/remove worker instances on the fly without configuring coordinators. | Horizontal scalability for high-load days. |
| **Fail-safe Execution** | RabbitMQ message acknowledgments ensure tasks are not lost if a worker dies mid-job. | Job durability. |
| **Exponential Backoff Retries** | Automatic retries with increasing delay (e.g., 5s, 10s, 20s...) on task failure. | Resiliency against transient third-party service outages. |
| **Secure Request Signing** | Outgoing webhooks are signed using an HMAC-SHA256 signature in the HTTP headers. | Target services can verify that requests originated from this system. |
| **Audit Trails & Logs** | Full history of task executions (status, duration, error logs, status codes). | Easier debugging and compliance auditing. |

---

## 3. System Prerequisites (To Run the System)

To run or develop this system, you need the following infrastructure and tools:

### Development Tools
*   **Java Development Kit (JDK) 21**: Necessary for Virtual Threads support.
*   **Maven 3.9+**: For building the multi-module project.
*   **IDE**: IntelliJ IDEA, Eclipse, or VS Code (with Java/Spring extensions).

### Infrastructure / Middleware Services
*   **PostgreSQL 15+**: Used as the relational database for persistent task schedules and execution logs.
*   **Redis 7+**: Used for leader election locks and cache keys.
*   **RabbitMQ 3.12+**: The message broker used to route tasks and coordinate retries.

*Note: For local development, all infrastructure components (Postgres, Redis, RabbitMQ) can be launched using a single Docker Compose file.*

---

## 4. Architectural & Scaling Expectations

### How It Scales
*   **Coordinators**: You should run at least 2 coordinator instances for redundancy. Adding more does not increase performance, since only the Active Leader polls the database.
*   **Workers**: You can run 1 to N worker instances. Scale this number up as task throughput increases.
*   **Virtual Threads**: Within each worker, Java 21 virtual threads allow a single worker instance to handle thousands of concurrent outbound HTTP webhook connections without thread-starvation issues.

### Failover Performance
*   **Coordinator Fails**: The Redis lock expires (typically configured for 10–15 seconds). The standby coordinator detects this, acquires the lock, and starts polling. Task dispatching is paused for at most the duration of the lock TTL.
*   **Worker Fails**: If a worker crashes while calling a webhook, the RabbitMQ TCP connection drops. RabbitMQ immediately returns the message to the queue, and another worker picks it up.
*   **Target Webhook Fails**: The worker catches the non-2xx HTTP code or connection timeout, updates the task status to `RETRYING` in PostgreSQL, and routes the message to the RabbitMQ retry queue (using Dead Letter Exchanges).
