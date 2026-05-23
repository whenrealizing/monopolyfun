package com.monopolyfun.modules.digitalinventory.api;

import com.monopolyfun.modules.digitalinventory.api.response.DigitalDeliveryRevealView;
import com.monopolyfun.modules.digitalinventory.service.DigitalInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class DigitalDeliveryController {
    private final DigitalInventoryService digitalInventoryService;

    public DigitalDeliveryController(DigitalInventoryService digitalInventoryService) {
        this.digitalInventoryService = digitalInventoryService;
    }

    @GetMapping("/{orderNo}/digital-delivery")
    @Operation(operationId = "revealDigitalDelivery")
    public DigitalDeliveryRevealView revealDeliveredPayload(@PathVariable String orderNo) {
        return digitalInventoryService.revealDeliveredPayload(orderNo);
    }
}
