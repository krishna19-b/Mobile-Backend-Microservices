package com.krishna.notificationservice.consumer;

import com.krishna.notificationservice.event.OrderCancelledEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCancelledDltTestConsumer {

    @KafkaListener(topics = "order.cancelled.test",groupId = "notification-dlt-test-group",containerFactory = "orderCancelledKafkaListenerContainerFactory")
    public void consume(OrderCancelledEvent event) {
        System.out.println("DLT TEST: Received event " + event.getEventId() + ". Throwing intentional exception to test retries.");
        throw new RuntimeException("Intentional failure for DLT testing");
    }
}