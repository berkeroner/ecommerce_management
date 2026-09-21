package com.ecommerce.management.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.management.dto.common.DataResponse;
import com.ecommerce.management.dto.order.OrderRequest;
import com.ecommerce.management.dto.order.OrderResponse;
import com.ecommerce.management.dto.order.OrderStatusResponse;
import com.ecommerce.management.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<DataResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new DataResponse<>(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        OrderResponse response = orderService.findById(id);
        return ResponseEntity.ok(new DataResponse<>(response));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<DataResponse<OrderStatusResponse>> getOrderStatus(@PathVariable Long id) {
        OrderStatusResponse response = orderService.getStatus(id);
        return ResponseEntity.ok(new DataResponse<>(response));
    }

}
