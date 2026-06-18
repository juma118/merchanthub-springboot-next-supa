package com.merchanthub.web;

import com.merchanthub.dto.ProductDtos.InventoryRow;
import com.merchanthub.dto.ProductDtos.InventoryUpdateRequest;
import com.merchanthub.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    public List<InventoryRow> list() {
        return inventory.list();
    }

    @PutMapping("/{productId}")
    public InventoryRow update(@PathVariable UUID productId, @Valid @RequestBody InventoryUpdateRequest req) {
        return inventory.update(productId, req);
    }
}
