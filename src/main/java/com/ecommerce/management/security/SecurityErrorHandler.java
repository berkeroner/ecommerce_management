package com.ecommerce.management.security;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.ecommerce.management.web.ApiErrorResponse;
import com.ecommerce.management.web.CorrelationIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED", "Authentication is required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        write(request, response, HttpServletResponse.SC_FORBIDDEN,
                "FORBIDDEN", "You are not allowed to perform this operation");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, int status,
            String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String correlationId = (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        ApiErrorResponse body = new ApiErrorResponse(
                new ApiErrorResponse.ApiError(code, message, Map.of(), correlationId));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
