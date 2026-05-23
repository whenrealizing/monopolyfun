package com.monopolyfun.modules.delivery.service;

import com.monopolyfun.modules.delivery.domain.DeliveryReceipt;
import com.monopolyfun.modules.delivery.domain.DeliveryRequest;

public interface DeliveryProvider {
    String providerCode();

    DeliveryReceipt deliver(DeliveryRequest request);
}
