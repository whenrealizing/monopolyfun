package com.monopolyfun.modules.identity.service.verification;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class XPublicProofCertifier extends PublicProofIdentityCertifier {
    public XPublicProofCertifier(List<PublicProofFetchClient> fetchClients) {
        super(IdentityCertifierCatalog.xPublicProofSpec(), fetchClients);
    }

    @Override
    protected String profileUrl(String handle) {
        return "https://x.com/" + handle;
    }
}
