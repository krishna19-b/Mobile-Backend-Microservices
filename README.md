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

-------------------------------------------------------------------------------------------------------------------------------------------------------------------
# Mobile Backend Microservices — Observability Work Log

**Date:** 15 October 2026  
**Project:** Mobile-Backend-Microservices  
**Focus:** Observability, Kafka consumer recovery, Actuator metrics, and troubleshooting

## 1. What is observability?

Observability means understanding what an application is doing by examining its logs, metrics, and health information.

In this project, observability helps us answer questions such as:

- Is Notification Service running?
- Are Kafka consumers processing messages?
- Are messages waiting to be consumed?
- Did event processing succeed or fail?
- Are failed messages retried?
- Are unrecoverable messages sent to a Dead Letter Topic (DLT)?
- How long does event processing take?

The three main observability signals are:

| Signal | Purpose | Project example |
|---|---|---|
| Logs | Explain what happened | `event_processed`, `event_processing_failed` |
| Metrics | Provide numerical measurements | Event counts and processing duration |
| Health checks | Indicate application health and readiness | `/actuator/health` |

## 2. Project components involved

- **Order Service:** Creates orders and records outbox events.
- **Outbox publisher:** Periodically publishes pending events to Kafka.
- **Kafka:** Transfers events between services.
- **Notification Service:** Consumes `order.created` and `order.cancelled`.
- **ProcessedEvent:** Records processed event IDs to prevent duplicate processing.
- **Dead Letter Topics:** Store messages that cannot be recovered through the configured error handler.
- **Spring Boot Actuator:** Exposes health and metrics endpoints.

Relevant Kafka topics:

- `order.created`
- `order.cancelled`
- `order.created-dlt`
- `order.cancelled-dlt`

## 3. Health and readiness checks

Health and readiness endpoints were previously verified as working.

### Commands

```powershell
Invoke-RestMethod "http://localhost:8085/actuator/health"
```

```powershell
Invoke-RestMethod "http://localhost:8085/actuator/health/readiness"
```

### Why do we use them?

- **Health:** Checks whether the application is healthy.
- **Readiness:** Indicates whether the application is ready to receive work, depending on configured health indicators.

These checks are useful for troubleshooting and deployment monitoring.

**Status:** Previously verified working.

## 4. Kafka consumer lag and offsets

Kafka stores messages in topic partitions. A consumer group tracks its committed position in each partition.

Important terms:

- **Current/committed offset:** The stored position from which the consumer group resumes reading. Kafka's committed offset normally represents the next record to read.
- **Log end offset:** The end position of records currently available in a partition.
- **Lag:** The difference between the partition's end offset and the consumer group's committed position.

### Command used earlier

```powershell
docker exec -it kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group notification-group
```

### Why use this command?

It helps identify whether the consumer group is keeping up with Kafka.

- `LAG = 0`: The consumer group has caught up to the current partition end at the time of the check.
- Positive lag: Records remain behind the consumer group's committed position.
- Increasing lag: May indicate slow processing, consumer problems, or a growing message backlog.

**Status:** Consumer lag was checked earlier and reported as zero. We did not repeat this completed check today.

## 5. Structured event-processing logs

The `OrderCreatedConsumer` code shared during troubleshooting includes log fields such as:

- `eventType`
- `eventId`
- `correlationId`
- `orderId`
- `userId`
- `processingTimeMs`

Log event names include:

```text
event_received
event_processed
event_processing_skipped
event_processing_failed
```

### Why use these logs?

- `eventId` identifies a particular event.
- `correlationId` helps trace related operations across services.
- `orderId` identifies the order associated with the event.
- `processingTimeMs` measures how long processing took.
- Consistent event names make log searches easier.

The shared consumer uses `System.nanoTime()` to measure elapsed time. It checks `ProcessedEventRepository` to skip previously processed event IDs and handles a database uniqueness conflict as a possible duplicate.

**Status:** This pattern is present in the shared `OrderCreatedConsumer` source. The full current `OrderCancelledConsumer` source has not been reviewed in this session.

## 6. Kafka retry and Dead Letter Topic recovery

The configured Spring Kafka retry pattern reviewed earlier was:

```java
new FixedBackOff(1000L, 2L)
```

This means:

- One initial delivery attempt.
- Two retry attempts.
- A one-second delay between retries.

After recovery is exhausted, the configured `DeadLetterPublishingRecoverer` can publish the failed record to its DLT.

### Why use retries?

A temporary problem, such as a brief database or network failure, may disappear on a later attempt.

### Why use a DLT?

A message that cannot be processed after retries can be retained for investigation instead of being silently discarded.

**Important:** The configuration confirms the intended retry policy. A DLT record confirms that a record reached the DLT, but it does not prove the exact retry count for that particular record.

### 6.1 Read messages from the created-order DLT

Command used:

```powershell
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order.created-dlt --from-beginning --max-messages 5
```

What each part means:

- `docker exec -it kafka`: Runs a command inside the running container named `kafka`.
- `kafka-console-consumer.sh`: Reads and displays Kafka messages.
- `--bootstrap-server localhost:9092`: Connects to Kafka.
- `--topic order.created-dlt`: Selects the created-order DLT.
- `--from-beginning`: Starts reading from the earliest retained record.
- `--max-messages 5`: Stops after displaying five records.

### Observed result

Five records were printed for order IDs:

- `32`
- `33`
- `34`
- `35`
- `54`

This confirms that `order.created-dlt` contains failed-message records. Some records were dated earlier, so this output does not mean all five failures occurred today.

### 6.2 Check the latest DLT offset

Command used:

```powershell
docker exec -it kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic order.created-dlt --time -1
```

Observed output:

```text
order.created-dlt:0:9
```

Meaning:

- Topic: `order.created-dlt`
- Partition: `0`
- Latest/end offset: `9`

Offsets are positions in a partition, not order IDs. If the earliest offset is `0` and all records through offset `8` remain available, an end offset of `9` indicates nine records. To establish the exact retained count, compare earliest and latest offsets and account for any deleted or expired records.

### 6.3 Cancellation DLT

The following command was started:

```powershell
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order.cancelled-dlt --from-beginning --max-messages 5
```

The output was not shared, so the cancellation DLT contents were not confirmed by today's evidence.

## 7. Spring Boot Actuator metrics

Actuator exposes application diagnostics over HTTP when the relevant endpoints are enabled and exposed.

The Notification Service runs on port `8085`.

Configuration already present in `application.properties`:

```properties
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.probes.enabled=true
```

### Why use this configuration?

- `health` exposes health information.
- `info` exposes application information if configured.
- `metrics` exposes Micrometer measurements.
- Health probes support readiness/liveness monitoring.

### 7.1 List available metrics

Command:

```powershell
Invoke-RestMethod "http://localhost:8085/actuator/metrics"
```

This lists the metric names available in the running application.

### 7.2 Inspect the built-in Kafka listener timer

Command:

```powershell
Invoke-RestMethod "http://localhost:8085/actuator/metrics/spring.kafka.listener"
```

Observed measurements:

```text
name          : spring.kafka.listener
description   : Kafka Listener Timer
baseUnit      : seconds
COUNT         : 0.0
TOTAL_TIME    : 0.0
MAX           : 0.0
```

Available tag categories included:

```text
result
exception
name
```

Their meanings:

- `COUNT`: Number of timer recordings represented by the selected metric series.
- `TOTAL_TIME`: Total accumulated recorded time in seconds.
- `MAX`: Maximum recorded duration in seconds.
- `result`: Result category associated with a timer recording.
- `exception`: Exception category.
- `name`: Listener container name.

### 7.3 Discover listener container names

Command:

```powershell
(Invoke-RestMethod "http://localhost:8085/actuator/metrics/spring.kafka.listener").availableTags |
    Where-Object { $_.tag -eq "name" } |
    Select-Object -ExpandProperty values
```

Observed names:

```text
org.springframework.kafka.KafkaListenerEndpointContainer#0-0
org.springframework.kafka.KafkaListenerEndpointContainer#1-0
org.springframework.kafka.KafkaListenerEndpointContainer#2-0
```

These likely represent the two notification consumers and the temporary test consumer, but the mapping was not verified.

### 7.4 Inspect an individual listener

Example command:

```powershell
Invoke-RestMethod "http://localhost:8085/actuator/metrics/spring.kafka.listener?tag=name:org.springframework.kafka.KafkaListenerEndpointContainer%230-0"
```

Equivalent checks were performed for containers `#1-0` and `#2-0`.

All three returned:

```text
COUNT      : 0.0
TOTAL_TIME : 0.0
MAX        : 0.0
```

### 7.5 Check result and exception tags

Commands:

```powershell
(Invoke-RestMethod "http://localhost:8085/actuator/metrics/spring.kafka.listener").availableTags |
    Where-Object { $_.tag -eq "result" } |
    Select-Object -ExpandProperty values
```

```powershell
(Invoke-RestMethod "http://localhost:8085/actuator/metrics/spring.kafka.listener").availableTags |
    Where-Object { $_.tag -eq "exception" } |
    Select-Object -ExpandProperty values
```

Observed tag values:

```text
success
none
```

These are available tag values. Their presence does not prove that any timer measurements have been recorded.

We also queried:

```powershell
Invoke-RestMethod "http://localhost:8085/actuator/metrics/spring.kafka.listener?tag=result:success"
```

The measurements were still zero.

**Conclusion:** The built-in listener metric endpoint exists, but all the timer measurements queried today were zero. This does not prove that Kafka has no messages or that the consumers never processed events. The cause of the zero measurements remains unresolved.

## 8. Custom Actuator metrics returning 404

The shared `OrderCreatedConsumer` registers custom Micrometer instruments inside its `finally` block.

### Counter

```java
Counter.builder("notification.events.processed")
    .tag("event_type", "ORDER_CREATED")
    .tag("outcome", outcome)
    .register(meterRegistry)
    .increment();
```

This counter is intended to count event outcomes such as success, duplicate, and failure.

### Timer

```java
Timer.builder("notification.event.processing")
    .tag("event_type", "ORDER_CREATED")
    .tag("outcome", outcome)
    .register(meterRegistry)
    .record(
        System.nanoTime() - startNanos,
        java.util.concurrent.TimeUnit.NANOSECONDS
    );
```

This timer is intended to measure event-processing duration.

The tags help distinguish event types and outcomes.

### Commands tested

Counter:

```powershell
Invoke-RestMethod "http://localhost:8085/actuator/metrics/notification.events.processed"
```

Timer:

```powershell
Invoke-RestMethod "http://localhost:8085/actuator/metrics/notification.event.processing"
```

Both were reported to return HTTP `404 Not Found`.

### What does 404 mean here?

Actuator cannot find the requested metric name in the running application's metrics registry.

One possibility is that these instruments are registered lazily, inside the consumer method, and the method has not run since the latest restart. This is only a hypothesis; the actual cause has not been established.

### Remaining investigation

1. Confirm whether a consumer method executes after the latest application restart.
2. Look for `event_received`, `event_processed`, or `event_processing_skipped` logs.
3. If the consumer executes but the metric is still missing, inspect the exact running code and metric-registration path.
4. Retry the Actuator metric endpoint after confirming listener execution.

Do not change the Java code unnecessarily before establishing the cause.

## 9. Commands used — quick reference

| Command | Purpose |
|---|---|
| `/actuator/health` | Check application health |
| `/actuator/health/readiness` | Check readiness |
| `/actuator/metrics` | List available metric names |
| `/actuator/metrics/spring.kafka.listener` | Inspect built-in Kafka listener timing |
| `kafka-consumer-groups.sh ... --describe --group notification-group` | Inspect consumer offsets and lag |
| `kafka-console-consumer.sh ... --from-beginning --max-messages 5` | Read up to five retained DLT messages |
| `kafka-get-offsets.sh ... --time -1` | Inspect latest/end offsets |
| `/actuator/metrics/notification.events.processed` | Inspect the custom event counter |
| `/actuator/metrics/notification.event.processing` | Inspect the custom processing timer |

`Invoke-RestMethod` sends an HTTP request and presents the JSON response as PowerShell objects. `docker exec` runs a command inside a Docker container. These commands inspect the running application or Kafka; they do not edit your Java source files.

## 10. Status at the end of 15 October

### Verified or previously checked

- Notification Service health/readiness had previously been verified.
- Kafka consumer lag was checked earlier and reported as zero at that time.
- `order.created-dlt` contains failed-message records.
- `order.created-dlt` partition `0` returned end offset `9`.
- The configured retry policy was reviewed as one initial attempt plus two retries, with a one-second backoff.
- Structured logging and duplicate protection are present in the shared `OrderCreatedConsumer` source.

### Still unresolved or needing confirmation

1. Custom Actuator metrics return 404.
2. Built-in listener timer measurements were zero in today's queries.
3. The exact retry count for a particular failed record has not been demonstrated by today's DLT output.
4. Cancellation DLT contents were not confirmed by the output shared today.
5. The complete current `OrderCancelledConsumer` implementation has not been reviewed in this session.
6. The final end-to-end integration review still needs to verify order creation, outbox publishing, Kafka delivery, cancellation, duplicate handling, and Kafka restart recovery together.

## 11. Recommended next steps

Complete these one at a time:

1. Investigate why custom metrics are missing, first confirming consumer execution.
2. Recheck built-in listener timing after a known event has been consumed.
3. Compare `OrderCancelledConsumer` with `OrderCreatedConsumer`.
4. Record evidence of retry and DLT behavior without repeating the already completed basic DLT check.
5. Complete the end-to-end integration checklist and document the result of each scenario.

## 12. Short team-lead update

Today I continued observability work on the event-driven Mobile Backend Microservices project. I inspected Kafka DLT records and offsets, reviewed the configured bounded retry/backoff and DLT recovery setup, and queried Spring Boot Actuator's built-in Kafka listener metrics. The created-order DLT contains failed-message records, while the listener timer queries returned zero measurements. The custom notification counter and processing timer endpoints still return 404 and remain under investigation. Existing structured logging, duplicate-event protection, health/readiness, and earlier consumer-lag checks were also reviewed.

---

**Note:** This README records the commands and outputs shared during the 15 October 2026 session. It distinguishes verified results from assumptions and does not claim unresolved metrics or unverified scenarios are complete.

-------------------------------------------------------------------------------------------------------------------------------------------------------------------# Dead Letter Topic (DLT) Replay Procedure

## 1. Purpose

This document describes how to inspect failed Kafka messages, identify the cause, correct the issue, and safely replay an event in the Mobile Backend Microservices project.

## 2. Current Configuration

- **Kafka broker:** `localhost:9092`
- **Notification consumer group:** `notification-group`
- **Test consumer group:** `notification-dlt-test-group`
- **Retry interval:** 1 second
- **Maximum retries:** 2 after the initial attempt
- **Total processing attempts:** 3
- **DLT naming convention:** Original topic name + `-dlt`

Example:
- Original topic: `order.cancelled.test`
- DLT topic: `order.cancelled.test-dlt`

## 3. Inspect Failed Messages

Run the following PowerShell command to read records from the test DLT:

```powershell
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order.cancelled.test-dlt --from-beginning --timeout-ms 5000
```

Identify the failed event using its `eventId` and investigate the cause before replaying it.

The consumer may print a `TimeoutException` after the configured timeout if no more records arrive. This does not necessarily indicate a Kafka failure.

## 4. Replay Procedure

1. Identify the failed event in the DLT.
2. Inspect the exception and determine why processing failed.
3. Correct the underlying issue, such as invalid JSON, incompatible event fields, or a temporary downstream failure.
4. Prepare a valid event matching the expected event schema.
5. Publish the corrected event to the original topic using an approved replay method.
6. Verify the Notification Service logs and the expected business outcome.
7. Record the replay result for troubleshooting and auditing.

For the test topic, the original topic is `order.cancelled.test`.

## 5. Safe Replay Rules

- Do not replay malformed JSON without correcting it.
- Do not repeatedly replay an event when the underlying problem remains unresolved.
- Preserve the original `eventId` when retrying the same business event.
- Ensure idempotency checks and business operations are designed to prevent duplicate effects.
- Use a new event ID only when creating a genuinely new business event, not simply to bypass duplicate detection.
- Validate the event schema before publishing.
- For production, use a controlled replay process with appropriate access controls and audit logging.

**Important:** Replaying a record with an already-processed `eventId` may cause an idempotent consumer to skip it. Investigate the original processing result before deciding whether replay is appropriate.

## 6. Verification Checklist

- [ ] Failed event identified in the DLT.
- [ ] Root cause investigated and corrected.
- [ ] Event schema and payload validated.
- [ ] Replay performed to the correct original topic.
- [ ] Consumer logs checked.
- [ ] Duplicate processing avoided.
- [ ] Business outcome verified.
- [ ] Replay result documented.

## 7. Test Evidence

The following test was verified in the development environment on 16 October 2026:

- Test event ID: `retry-test-001`
- Source topic: `order.cancelled.test`
- DLT topic: `order.cancelled.test-dlt`
- Processing attempts: 3
- DLT record confirmed: Yes

The test consumer deliberately throws an exception. This test verifies retry handling and DLT publishing, not successful business processing.


-----------------------------------------------------------------------------------------------------------------------------------------------------------------

---

# Saga Orchestration and Compensation

## 1. Overview

The Order Service coordinates the order workflow using a persisted Saga. The Saga tracks progress across inventory reservation and payment authorization, and compensates inventory if payment fails.

## 2. Saga State Management

The Saga state is stored in the `order_sagas` table, and individual inventory steps are tracked in `order_saga_steps`.

### Saga statuses

- `STARTED` — Saga processing has begun.
- `INVENTORY_RESERVED` — Inventory reservation completed.
- `PAYMENT_PENDING` — Payment authorization is pending or needs recovery.
- `COMPENSATING` — Compensation is in progress.
- `COMPLETED` — Order workflow completed successfully.
- `COMPENSATION_FAILED` — Compensation needs further recovery.
- `FAILED` — Saga ended unsuccessfully after the compensation workflow.

### Inventory step statuses

- `PENDING` — Inventory step has not completed.
- `RESERVED` — Inventory was reserved.
- `COMPENSATED` — Reserved inventory was released.
- `COMPENSATION_FAILED` — Inventory compensation failed and needs recovery.

## 3. Successful workflow

1. Order Service receives the `ORDER_CREATED` event.
2. The Saga reserves inventory using an operation key for the order item.
3. Saga state and inventory step status are persisted.
4. Payment authorization is requested.
5. If payment is authorized, the order becomes `CONFIRMED` and the Saga becomes `COMPLETED`.

## 4. Failed payment and compensation

If payment fails, the Saga enters compensation:

1. The Saga records the `COMPENSATING` state.
2. Reserved inventory is released through the Product Service.
3. Completed compensation is recorded against the inventory step.
4. After successful compensation, the order becomes `CANCELLED` and the Saga becomes `FAILED`.

The compensation operation key helps prevent duplicate inventory effects when the same operation is retried.

## 5. Recovery

A scheduled recovery method checks unfinished Sagas using the configured `saga.recovery.fixed-delay-ms` property. The configured interval is 10 seconds.

Recovery uses persisted Saga and step states to resume unfinished work. Completed or terminal failed Sagas are skipped. Payment status is checked when an authorization request throws an exception and the payment status can be retrieved.

## 6. Verification results

| Scenario | Observed result |
|---|---|
| Successful order workflow — Order 74 | Saga `COMPLETED`; order `CONFIRMED`; inventory step `RESERVED` |
| Payment failure — Order 75 | Saga `FAILED` after compensation; order `CANCELLED`; inventory step `COMPENSATED` |
| Repeated compensation operation | No additional stock increase was observed when the compensation operation was repeated |
| Payment service outage and scheduled recovery | Not yet verified |
| Late payment event and repeated compensation across multiple service instances | Not yet verified |

These results reflect the database states and behavior observed during the tests. They do not establish that every timeout, late-event, or multi-instance race condition has been tested.

## 7. Main implementation components

- `OrderSagaListener` — Processes order-created events, coordinates Saga steps, and recovers unfinished Sagas.
- `ProductClient` — Communicates with Product Service to reserve and compensate inventory.
- `PaymentClient` — Communicates with Payment Service to authorize payments and retrieve payment status.
- `order_sagas` — Persists Saga-level progress.
- `order_saga_steps` — Persists inventory-step progress and compensation status.




