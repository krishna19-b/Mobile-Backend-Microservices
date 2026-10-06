package com.krishna.orderservice.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignCorrelationIdConfig {

    private static final String CORRELATION_ID =
            "X-Correlation-ID";

    private static final String INTERNAL_SERVICE_KEY =
            "X-Internal-Service-Key";

    @Value("${internal.service.secret}")
    private String internalServiceSecret;

    @Bean
    public RequestInterceptor correlationIdInterceptor() {

        return requestTemplate -> {

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes)
                            RequestContextHolder.getRequestAttributes();

            if (attributes != null) {

                HttpServletRequest request =
                        attributes.getRequest();

                String correlationId =
                        request.getHeader(CORRELATION_ID);

                if (correlationId != null) {
                    requestTemplate.header(
                            CORRELATION_ID,
                            correlationId);
                }
            }

            requestTemplate.header(
                    INTERNAL_SERVICE_KEY,
                    internalServiceSecret);
        };
    }
}