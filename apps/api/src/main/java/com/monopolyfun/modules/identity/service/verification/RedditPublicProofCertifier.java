package com.monopolyfun.modules.identity.service.verification;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedditPublicProofCertifier extends PublicProofIdentityCertifier {
    public RedditPublicProofCertifier(List<PublicProofFetchClient> fetchClients) {
        super(IdentityCertifierCatalog.redditPublicProofSpec(), fetchClients);
    }

    @Override
    protected String profileUrl(String handle) {
        return "https://www.reddit.com/user/" + handle;
    }
}
