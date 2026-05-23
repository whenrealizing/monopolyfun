package com.monopolyfun.modules.identity.service.verification;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Component
public class IdentityCertifierRegistry {
    private final Map<String, IdentityCertifier> certifiers;

    public IdentityCertifierRegistry(List<IdentityCertifier> certifiers) {
        this.certifiers = certifiers.stream().collect(java.util.stream.Collectors.toMap(
                certifier -> certifier.manifest().id(),
                Function.identity()));
    }

    public Optional<IdentityCertifier> find(String certifierId) {
        return Optional.ofNullable(certifiers.get(certifierId));
    }

    public List<IdentityCertifierManifest> listManifests() {
        return certifiers.values().stream()
                .map(IdentityCertifier::manifest)
                .sorted(java.util.Comparator.comparing(IdentityCertifierManifest::name))
                .toList();
    }
}
