package com.monopolyfun.modules.payment.api.response;

import com.monopolyfun.modules.payment.service.view.PaymentIntentView;

public record PaymentIntentResponse(
        PaymentIntentView paymentIntent,
        String checkoutUrl
) {
}
