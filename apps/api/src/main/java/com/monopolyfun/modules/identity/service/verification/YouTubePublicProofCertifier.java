package com.monopolyfun.modules.identity.service.verification;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class YouTubePublicProofCertifier extends PublicProofIdentityCertifier {
    public YouTubePublicProofCertifier(List<PublicProofFetchClient> fetchClients) {
        super(IdentityCertifierCatalog.youtubePublicProofSpec(), fetchClients);
    }

    @Override
    protected String profileUrl(String handle) {
        return "https://www.youtube.com/@" + handle;
    }
}
