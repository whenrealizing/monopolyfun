package com.monopolyfun.modules.delivery.provider;

import com.monopolyfun.modules.delivery.domain.DeliveryReceipt;
import com.monopolyfun.modules.delivery.domain.DeliveryRequest;
import com.monopolyfun.modules.delivery.service.DeliveryProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;

@Component
public class PhoneRechargeDeliveryProvider implements DeliveryProvider {
    @Override
    public String providerCode() {
        return "phone_recharge";
    }

    @Override
    public DeliveryReceipt deliver(DeliveryRequest request) {
        String phone = text(request.input().get("phone"));
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手机号格式不正确");
        }
        if ("13800000000".equals(phone)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "服务商拒绝本次充值");
        }
        String maskedPhone = phone.substring(0, 3) + "****" + phone.substring(7);
        LinkedHashMap<String, Object> raw = new LinkedHashMap<>();
        raw.put("phone", maskedPhone);
        raw.put("amount", request.input().getOrDefault("amount", request.settlementSnapshot().get("amount")));
        raw.put("idempotencyKey", request.idempotencyKey());
        raw.put("network", request.settlementSnapshot().getOrDefault("paymentNetwork", "eip155:196"));
        // 中文注释：首版 provider 是确定性本地实现，保留真实 provider 单号形态便于后续替换外部充值渠道。
        return new DeliveryReceipt(providerCode(), "phone-recharge-" + Math.abs(request.idempotencyKey().hashCode()), "succeeded", Instant.now(), raw);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
