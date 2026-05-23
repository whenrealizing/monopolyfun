package com.monopolyfun.modules.digitalinventory.infra;

import com.monopolyfun.modules.digitalinventory.domain.DigitalDeliveryEntity;

import java.util.Optional;

public interface DigitalDeliveryRepository {
    DigitalDeliveryEntity save(DigitalDeliveryEntity delivery);

    Optional<DigitalDeliveryEntity> findByOrderId(String orderId);
}
