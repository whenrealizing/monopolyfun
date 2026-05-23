package com.monopolyfun.modules.post.api.response;

import com.monopolyfun.modules.post.service.view.OfferView;
import com.monopolyfun.shared.command.CommandReceipt;

public record OfferCreateResponse(
        OfferView offer,
        CommandReceipt receipt
) {
    public OfferCreateResponse(OfferView offer) {
        this(offer, null);
    }
}
