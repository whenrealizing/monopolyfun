package com.monopolyfun.modules.identity.service.verification;

import java.net.URI;

public interface PublicProofFetchClient {
    boolean supports(String provider);

    PublicProofDocument fetch(String provider, URI proofUri, String proofPlacement);
}
