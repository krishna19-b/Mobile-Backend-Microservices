package com.krishna.gatewayservice.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(CorrelationIdFilter.class);

    private static final String CORRELATION_ID = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        String correlationId = exchange.getRequest()
                .getHeaders()
                .getFirst(CORRELATION_ID);

        if (correlationId == null ||
                !correlationId.matches("[a-zA-Z0-9-]{1,64}")) {

            correlationId = UUID.randomUUID().toString();
        }

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .header(CORRELATION_ID, correlationId)
                .build();

        ServerWebExchange updatedExchange = exchange
                .mutate()
                .request(request)
                .build();

        updatedExchange.getResponse()
                .getHeaders()
                .set(CORRELATION_ID, correlationId);

        MDC.put("correlationId", correlationId);

        log.info(
                "service=gatewayservice correlationId={} method={} endpoint={}",
                correlationId,
                request.getMethod(),
                request.getURI().getPath()
        );

        return chain.filter(updatedExchange)
                .doFinally(signal -> MDC.remove("correlationId"));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}