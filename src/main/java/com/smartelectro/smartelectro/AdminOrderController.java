package com.smartelectro.smartelectro;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final StoreService storeService;

    public AdminOrderController(StoreService storeService) {
        this.storeService = storeService;
    }

    @PatchMapping("/{id}/status")
    public StoreDtos.OrderView updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return storeService.updateOrderStatus(id, request.status());
    }

    public record StatusUpdateRequest(@NotNull OrderStatus status) {
    }
}
