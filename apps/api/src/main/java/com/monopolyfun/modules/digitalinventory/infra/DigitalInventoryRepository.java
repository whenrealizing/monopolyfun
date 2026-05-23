package com.monopolyfun.modules.digitalinventory.infra;

import com.monopolyfun.modules.digitalinventory.domain.DigitalInventoryItemEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DigitalInventoryRepository {
    DigitalInventoryItemEntity save(DigitalInventoryItemEntity item);

    List<DigitalInventoryItemEntity> saveAll(List<DigitalInventoryItemEntity> items);

    Optional<DigitalInventoryItemEntity> reserveAvailable(String listingId, String orderId, Instant now);

    Optional<DigitalInventoryItemEntity> findReservedByOrderId(String orderId);

    Optional<DigitalInventoryItemEntity> findDeliveredByOrderId(String orderId);

    Optional<DigitalInventoryItemEntity> findById(String id);

    Map<String, Integer> countByListingId(String listingId);
}
