package com.monopolyfun.modules.digitalinventory.api;

import com.monopolyfun.modules.digitalinventory.api.request.UploadDigitalInventoryRequest;
import com.monopolyfun.modules.digitalinventory.api.response.DigitalInventorySummaryView;
import com.monopolyfun.modules.digitalinventory.api.response.DigitalInventoryUploadResponse;
import com.monopolyfun.modules.digitalinventory.service.DigitalInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items/{itemId}/digital-inventory")
public class DigitalInventoryController {
    private final DigitalInventoryService digitalInventoryService;

    public DigitalInventoryController(DigitalInventoryService digitalInventoryService) {
        this.digitalInventoryService = digitalInventoryService;
    }

    @PostMapping
    @Operation(operationId = "uploadDigitalInventory")
    public DigitalInventoryUploadResponse upload(
            @PathVariable String itemId,
            @Valid @RequestBody UploadDigitalInventoryRequest request) {
        return digitalInventoryService.upload(itemId, request);
    }

    @GetMapping("/summary")
    @Operation(operationId = "getDigitalInventorySummary")
    public DigitalInventorySummaryView summary(
            @PathVariable String itemId,
            @RequestParam String actorAccountId) {
        return digitalInventoryService.summary(itemId, actorAccountId);
    }

}
