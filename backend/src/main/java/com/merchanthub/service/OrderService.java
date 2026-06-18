package com.merchanthub.service;

import com.merchanthub.domain.OrderEntity;
import com.merchanthub.domain.OrderItem;
import com.merchanthub.dto.CommonDtos.PageResponse;
import com.merchanthub.dto.OrderDtos.*;
import com.merchanthub.repo.OrderItemRepository;
import com.merchanthub.repo.OrderRepository;
import com.merchanthub.tenant.TenantContext;
import com.merchanthub.web.error.ApiExceptions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;

    public OrderService(OrderRepository orders, OrderItemRepository orderItems) {
        this.orders = orders;
        this.orderItems = orderItems;
    }

    private static final Instant MIN = Instant.EPOCH;
    private static final Instant MAX = Instant.parse("2999-12-31T23:59:59Z");

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> list(String status, Instant from, Instant to, int page, int size) {
        UUID mid = TenantContext.requireMerchantId();
        Page<OrderEntity> result = orders.search(mid,
                (status == null || status.isBlank()) ? "" : status,
                from != null ? from : MIN,
                to != null ? to : MAX,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PageResponse.of(result, o -> new OrderSummary(
                o.getId(), o.getExternalId(), o.getTotal(), o.getCurrency(),
                o.getStatus(), o.getCustomerEmail(), o.getCreatedAt(),
                orderItems.countByOrderId(o.getId())));
    }

    @Transactional(readOnly = true)
    public OrderDetail get(UUID id) {
        UUID mid = TenantContext.requireMerchantId();
        OrderEntity o = orders.findByIdAndMerchantId(id, mid)
                .orElseThrow(() -> new ApiExceptions.NotFound("Order not found"));
        var items = orderItems.findByOrderId(o.getId()).stream()
                .map(this::toItemView).toList();
        return new OrderDetail(o.getId(), o.getExternalId(), o.getTotal(), o.getCurrency(),
                o.getStatus(), o.getCustomerEmail(), o.getCreatedAt(), items);
    }

    private OrderItemView toItemView(OrderItem it) {
        return new OrderItemView(it.getId(), it.getProductId(), it.getSku(),
                it.getQuantity(), it.getUnitPrice());
    }
}
