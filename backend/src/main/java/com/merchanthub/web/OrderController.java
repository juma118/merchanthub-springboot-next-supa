package com.merchanthub.web;

import com.merchanthub.dto.CommonDtos.PageResponse;
import com.merchanthub.dto.OrderDtos.OrderDetail;
import com.merchanthub.dto.OrderDtos.OrderSummary;
import com.merchanthub.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public PageResponse<OrderSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orders.list(status, WebParams.parseInstant(from), WebParams.parseInstant(to),
                page, Math.min(size, 100));
    }

    @GetMapping("/{id}")
    public OrderDetail get(@PathVariable UUID id) {
        return orders.get(id);
    }
}
