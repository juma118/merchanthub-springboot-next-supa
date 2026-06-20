package com.merchanthub.service;

import com.merchanthub.domain.Alert;
import com.merchanthub.domain.OrderEntity;
import com.merchanthub.domain.OrderItem;
import com.merchanthub.domain.Product;
import com.merchanthub.repo.OrderItemRepository;
import com.merchanthub.repo.OrderRepository;
import com.merchanthub.repo.ProductRepository;
import com.merchanthub.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderIngestionService {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ProductRepository products;
    private final InventoryService inventoryService;
    private final AlertService alertService;
    private final com.merchanthub.messaging.OutboxService outbox;

    public OrderIngestionService(OrderRepository orders, OrderItemRepository orderItems,
                                 ProductRepository products, InventoryService inventoryService,
                                 AlertService alertService, com.merchanthub.messaging.OutboxService outbox) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.products = products;
        this.inventoryService = inventoryService;
        this.alertService = alertService;
        this.outbox = outbox;
    }

    public record ItemInput(String sku, int quantity, BigDecimal unitPrice) {}

    /** @return true if a new order was created, false if it already existed. */
    @Transactional
    public boolean ingest(String externalId, BigDecimal total, String currency, String status,
                          String customerEmail, Instant createdAt, List<ItemInput> items) {
        UUID mid = TenantContext.requireMerchantId();

        if (externalId != null && orders.findByMerchantIdAndExternalId(mid, externalId).isPresent()) {
            return false;  // already ingested
        }

        OrderEntity order = new OrderEntity();
        order.setMerchantId(mid);
        order.setExternalId(externalId);
        order.setCurrency(currency != null ? currency : "USD");
        order.setStatus(normalizeStatus(status));
        order.setCustomerEmail(customerEmail);
        order.setCreatedAt(createdAt != null ? createdAt : Instant.now());

        BigDecimal computed = BigDecimal.ZERO;
        Map<String, UUID> skuToProduct = new HashMap<>();
        for (Product p : products.findByMerchantId(mid)) skuToProduct.put(p.getSku(), p.getId());

        order.setTotal(total != null ? total : BigDecimal.ZERO);
        orders.save(order);

        for (ItemInput it : items == null ? List.<ItemInput>of() : items) {
            OrderItem oi = new OrderItem();
            oi.setMerchantId(mid);
            oi.setOrderId(order.getId());
            oi.setProductId(skuToProduct.get(it.sku()));
            oi.setSku(it.sku());
            oi.setQuantity(it.quantity());
            oi.setUnitPrice(it.unitPrice() != null ? it.unitPrice() : BigDecimal.ZERO);
            orderItems.save(oi);
            computed = computed.add(oi.getUnitPrice().multiply(BigDecimal.valueOf(it.quantity())));
        }

        if (total == null && computed.compareTo(BigDecimal.ZERO) > 0) {
            order.setTotal(computed);
            orders.save(order);
        }

        // Revenue-generating orders consume stock; abandoned/cancelled do not.
        if (OrderEntity.PAID.equals(order.getStatus()) || OrderEntity.FULFILLED.equals(order.getStatus())) {
            inventoryService.applyOrderItems(items == null ? List.of()
                    : items.stream().map(i -> new InventoryService.ItemQty(i.sku(), i.quantity())).toList());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id", order.getId().toString());
        payload.put("external_id", externalId);
        payload.put("total", order.getTotal());
        payload.put("status", order.getStatus());
        alertService.create(Alert.NEW_ORDER, payload);

        // Publish a domain event via the transactional outbox (same transaction as
        // the order). The notification-service consumes it from Kafka.
        Map<String, Object> event = new HashMap<>();
        event.put("event", "OrderIngested");
        event.put("orderId", order.getId().toString());
        event.put("merchantId", mid.toString());
        event.put("externalId", externalId);
        event.put("total", order.getTotal());
        event.put("status", order.getStatus());
        event.put("customerEmail", customerEmail);
        event.put("occurredAt", Instant.now().toString());
        outbox.append(com.merchanthub.messaging.OutboxService.TOPIC_ORDER_INGESTED, mid.toString(), "OrderIngested", event);

        return true;
    }

    private String normalizeStatus(String status) {
        if (status == null) return OrderEntity.CREATED;
        return switch (status.toLowerCase()) {
            case "paid" -> OrderEntity.PAID;
            case "fulfilled" -> OrderEntity.FULFILLED;
            case "cancelled", "canceled" -> OrderEntity.CANCELLED;
            case "abandoned" -> OrderEntity.ABANDONED;
            default -> OrderEntity.CREATED;
        };
    }
}
