
package com.krishna.notificationservice.consumer;

import com.krishna.notificationservice.entity.ProcessedEvent;
import com.krishna.notificationservice.event.OrderCancelledEvent;
import com.krishna.notificationservice.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
public class OrderCancelledConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(OrderCancelledConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final MeterRegistry meterRegistry;

    public OrderCancelledConsumer(
            ProcessedEventRepository processedEventRepository,
            MeterRegistry meterRegistry) {
        this.processedEventRepository = processedEventRepository;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = "order.cancelled", groupId = "notification-group", containerFactory = "orderCancelledKafkaListenerContainerFactory")
    public void consume(OrderCancelledEvent event) {

        long startNanos = System.nanoTime();
        String outcome = "failure";

        try {
            if (processedEventRepository.existsByEventId(event.getEventId())) {
                outcome = "duplicate";

                log.info(
                        "event_processing_skipped eventType={} eventId={} correlationId={} orderId={} reason=duplicate processingTimeMs={}",
                        event.getEventType(),
                        event.getEventId(),
                        event.getCorrelationId(),
                        event.getOrderId(),
                        elapsedMillis(startNanos)
                );
                return;
            }

            log.info(
                    "event_received eventType={} eventId={} correlationId={} orderId={} userId={}",
                    event.getEventType(),
                    event.getEventId(),
                    event.getCorrelationId(),
                    event.getOrderId(),
                    event.getUserId()
            );

            ProcessedEvent processedEvent = new ProcessedEvent(
                    event.getEventId(),
                    LocalDateTime.now()
            );

            processedEventRepository.saveAndFlush(processedEvent);
            outcome = "success";

            log.info(
                    "event_processed eventType={} eventId={} correlationId={} orderId={} processingTimeMs={}",
                    event.getEventType(),
                    event.getEventId(),
                    event.getCorrelationId(),
                    event.getOrderId(),
                    elapsedMillis(startNanos)
            );

        } catch (DataIntegrityViolationException exception) {
            if (processedEventRepository.existsByEventId(event.getEventId())) {
                outcome = "duplicate";

                log.info(
                        "event_processing_skipped eventType={} eventId={} correlationId={} orderId={} reason=duplicate_during_save processingTimeMs={}",
                        event.getEventType(),
                        event.getEventId(),
                        event.getCorrelationId(),
                        event.getOrderId(),
                        elapsedMillis(startNanos)
                );
                return;
            }

            log.error(
                    "event_processing_failed eventType={} eventId={} correlationId={} orderId={} processingTimeMs={}",
                    event.getEventType(),
                    event.getEventId(),
                    event.getCorrelationId(),
                    event.getOrderId(),
                    elapsedMillis(startNanos),
                    exception
            );
            throw exception;

        } catch (RuntimeException exception) {
            log.error(
                    "event_processing_failed eventType={} eventId={} correlationId={} orderId={} processingTimeMs={}",
                    event.getEventType(),
                    event.getEventId(),
                    event.getCorrelationId(),
                    event.getOrderId(),
                    elapsedMillis(startNanos),
                    exception
            );
            throw exception;

        } finally {
            Counter.builder("notification.events.processed")
                    .tag("event_type", "ORDER_CANCELLED")
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .increment();

            Timer.builder("notification.event.processing")
                    .tag("event_type", "ORDER_CANCELLED")
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(
                            System.nanoTime() - startNanos,
                            TimeUnit.NANOSECONDS
                    );
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
