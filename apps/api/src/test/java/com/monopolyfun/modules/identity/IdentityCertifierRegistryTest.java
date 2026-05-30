package com.monopolyfun;

import com.monopolyfun.modules.identity.service.verification.IdentityCertifierRegistry;
import com.monopolyfun.modules.identity.service.verification.XPublicProofCertifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityCertifierRegistryTest {
    @Test
    void registryReturnsExtendedManifestFields() {
        IdentityCertifierRegistry registry = new IdentityCertifierRegistry(List.of(
                new XPublicProofCertifier(List.of())));

        var manifests = registry.listManifests();

        assertEquals(List.of("X"), manifests.stream().map(manifest -> manifest.name()).toList());
        assertEquals("x_public_proof_verified", registry.find("x_public_proof").orElseThrow().manifest().badgeCode());
        assertEquals(List.of("handle", "proofPlacement"), registry.find("x_public_proof").orElseThrow().manifest().startInputSchema().get("required"));
    }
}
