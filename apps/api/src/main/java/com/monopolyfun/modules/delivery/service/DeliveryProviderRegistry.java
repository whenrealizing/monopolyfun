package com.monopolyfun.modules.delivery.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DeliveryProviderRegistry {
    private final Map<String, DeliveryProvider> providers;

    public DeliveryProviderRegistry(List<DeliveryProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(provider -> provider.providerCode().toLowerCase(Locale.ROOT), Function.identity()));
    }

    public DeliveryProvider requireProvider(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deliveryProvider is required");
        }
        DeliveryProvider provider = providers.get(providerCode.trim().toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported deliveryProvider");
        }
        return provider;
    }
}
