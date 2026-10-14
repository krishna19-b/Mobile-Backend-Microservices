# Mobile-Backend-Microservices

## Kafka Consumer Recovery and Reliability Testing

### 1. Overview

The Mobile Backend Microservices project uses Apache Kafka for asynchronous event communication between Order Service and Notification Service. Reliability mechanisms are implemented to handle temporary Kafka outages, consumer failures, duplicate events, and unsuccessful event processing.

### 2. Recovery Mechanisms

**Idempotent Consumer**
- Every event contains a unique `eventId`.
- Before processing an event, Notification Service checks whether the event ID already exists in the `processed_events` table.
- A database uniqueness constraint helps prevent duplicate records.
- Duplicate events are skipped instead of being processed again.

**Bounded Retry and Backoff**
- Failed event processing is retried using Spring Kafka's `DefaultErrorHandler`.
- The configured `FixedBackOff(1000L, 2L)` provides two retries after the initial attempt, with a one-second delay between attempts.
- After the configured attempts are exhausted, the failed event is routed to a Dead Letter Topic.

**Dead Letter Topic (DLT)**
- Failed events are routed to a topic named using the original topic plus `-dlt`.
- For example, failures from `order.created` are routed to `order.created-dlt`.
- Failed events can be inspected and selectively replayed after the underlying issue is resolved.

**Transactional Outbox Pattern**
- Order Service stores events in its database Outbox as part of the business transaction.
- A scheduled Outbox Publisher checks for pending events and publishes them to Kafka.
- After successful publication, the event status is updated to indicate publication.
- If Kafka is temporarily unavailable, failed events remain pending for a later publishing attempt, provided the publisher's error handling preserves their pending status.

**Consumer Restart and Rebalancing**
- Consumers reconnect and resume consumption after Notification Service restarts.
- Kafka rebalances consumer-group assignments when instances join or leave the group.
- Consumer-group offsets and lag can be inspected using Kafka's consumer-group tools.

### 3. Recovery Test Results

| Test scenario | Expected behavior | Result |
|---|---|---|
| Duplicate event delivery | Existing event ID is detected and skipped | Passed |
| Retry and backoff | Failed processing is retried within the configured limit | Passed |
| DLT routing | Repeatedly failed event is sent to the DLT | Passed |
| DLT replay | A selected failed event is republished to the original topic and processed | Passed |
| Consumer restart | Consumer resumes processing after restart | Passed |
| Consumer rebalance | Partition assignments change when consumer instances join or leave | Passed |
| Kafka outage and restart | Pending Outbox event is published after Kafka becomes available | Passed |

### 4. DLT Replay and Idempotency Verification

A previously failed `ORDER_CREATED` event for Order ID `54` was replayed using its original event ID:

`bdb432ed-de3d-4249-b137-21485b7d3a47`

The Notification Service logged:

- `Event processed successfully`
- `Duplicate event detected. Skipping event`

The event ID was also verified in the `processed_events` database table. Replaying the same event again confirmed that the idempotency check prevents duplicate processing.

### 5. Kafka Outage Recovery Verification

Kafka was temporarily unavailable and then restarted. The pending Outbox event was subsequently published by the scheduled publisher.

This test demonstrated that the Outbox mechanism can recover from temporary Kafka unavailability without requiring the business operation to be submitted again.

### 6. Key Reliability Benefits

- Reduced risk of losing events during temporary Kafka outages.
- Controlled retries for transient processing failures.
- Isolation of repeatedly failing events through DLT routing.
- Protection against duplicate event processing.
- Recovery after consumer restarts and consumer-group membership changes.
- Improved observability through event logs, Outbox statuses, processed-event records, and consumer-group lag.

### 7. Technologies Used

- Java and Spring Boot
- Spring Kafka
- Apache Kafka
- Spring Data JPA
- PostgreSQL
- Kafka consumer groups and offsets
- Transactional Outbox Pattern
- Dead Letter Topics (DLT)
- Idempotent event processing
