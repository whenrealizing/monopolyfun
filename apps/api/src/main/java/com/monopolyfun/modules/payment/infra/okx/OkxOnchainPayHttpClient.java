package com.monopolyfun.modules.payment.infra.okx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monopolyfun.config.PaymentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OkxOnchainPayHttpClient implements OkxOnchainPayClient {
    private static final Logger log = LoggerFactory.getLogger(OkxOnchainPayHttpClient.class);
    private static final String XLAYER_CHAIN_ID = "eip155:196";
    private static final String VERIFY_PATH = "/api/v6/pay/x402/verify";
    private static final String SETTLE_PATH = "/api/v6/pay/x402/settle";
    private static final String SETTLE_STATUS_PATH = "/api/v6/pay/x402/settle/status";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String USER_AGENT = "monopolyfun-api/0.1 okx-onchain-pay";
    private static final DateTimeFormatter OKX_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Map<String, String> XLAYER_ASSET_ADDRESSES = Map.of(
            "USDC", "0x74b7f16337b8972027f6196a17a631ac6de26d22",
            "USDG", "0x4ae46a509f6b1d9056937ba4500cb143933d2dc8",
            "USDT0", "0x779ded0c9e1022225f8e0630b35a9b54be713736");
    private static final Map<String, Map<String, Object>> XLAYER_EIP712_DOMAINS = Map.of(
            "USDC", Map.of("name", "USD Coin", "version", "2"),
            "USDG", Map.of("name", "Global Dollar", "version", "2"),
            "USDT0", Map.of("name", "USD₮0", "version", "2"));

    private final PaymentConfig paymentConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final OkxResiliencePolicy resiliencePolicy;

    // 中文注释：生产 Bean 明确使用配置构造函数，测试专用构造函数只负责注入替身 HttpClient。
    @Autowired
    public OkxOnchainPayHttpClient(PaymentConfig paymentConfig, ObjectMapper objectMapper) {
        this(paymentConfig, objectMapper, HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1)
                .build());
    }

    OkxOnchainPayHttpClient(PaymentConfig paymentConfig, ObjectMapper objectMapper, HttpClient httpClient) {
        this(paymentConfig, objectMapper, httpClient, new OkxResiliencePolicy());
    }

    OkxOnchainPayHttpClient(
            PaymentConfig paymentConfig,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            OkxResiliencePolicy resiliencePolicy) {
        this.paymentConfig = paymentConfig;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.resiliencePolicy = resiliencePolicy;
    }

    @Override
    public Map<String, Object> buildPaymentRequirements(int amountMinor, String asset, String recipient) {
        String normalizedAsset = normalizeAsset(asset);
        LinkedHashMap<String, Object> requirements = new LinkedHashMap<>();
        requirements.put("scheme", "exact");
        requirements.put("network", XLAYER_CHAIN_ID);
        requirements.put("asset", XLAYER_ASSET_ADDRESSES.getOrDefault(normalizedAsset, normalizedAsset));
        requirements.put("amount", String.valueOf((long) amountMinor * 10_000L));
        requirements.put("payTo", requireRecipient(recipient));
        // 中文注释：maxTimeoutSeconds 会进入 EIP-3009 validBefore，前后端必须使用同一个窗口。
        requirements.put("maxTimeoutSeconds", Math.max(1, paymentConfig.getOkx().getMaxTimeoutSeconds()));
        // 中文注释：extra 是 EIP-712 token domain，钱包签名和 OKX verify 都依赖这里的 name/version。
        requirements.put("extra", XLAYER_EIP712_DOMAINS.getOrDefault(normalizedAsset, Map.of("name", normalizedAsset, "version", "2")));
        return Map.copyOf(requirements);
    }

    @Override
    public OkxOnchainPaySession createPayment(OkxOnchainPayCreateRequest request) {
        Map<String, Object> requirements = buildPaymentRequirements(request.amountMinor(), request.asset(), request.recipient());
        if (request.paymentPayload() == null || request.paymentPayload().isEmpty()) {
            return session(request, "requires_payment", request.idempotencyKey(), null, null, null, requirements,
                    Map.of("paymentRequirements", requirements, "metadata", metadata(request)));
        }

        Map<String, Object> verified = verifyPayment(request.paymentPayload(), requirements);
        if (!readBoolean(verified, List.of("valid", "isValid", "success"))) {
            return session(request, "failed", request.idempotencyKey(), null, readString(verified, List.of("payer", "from", "sender")),
                    null, requirements, Map.of("verify", verified, "metadata", metadata(request)));
        }

        Map<String, Object> settled = settlePayment(request.paymentPayload(), requirements, request.syncSettle());
        String status = normalizeStatus(readString(settled, List.of("status", "state", "settleStatus")));
        String settlementId = readString(settled, List.of("settlementId", "settleId", "settleRequestId"));
        String txHash = readString(settled, List.of("transaction", "txHash", "transactionHash"));
        String payer = readString(settled, List.of("payer", "from", "sender"));
        Map<String, Object> statusData = Map.of();
        if (txHash != null && !txHash.isBlank()) {
            // 中文注释：settle 成功后立即查询 settle/status，capture 判定绑定 OKX 最终结算事实。
            statusData = dataRecord(requestRaw(SETTLE_STATUS_PATH + "?txHash=" + txHash, "GET", null));
            String statusValue = readString(statusData, List.of("status", "state", "settleStatus"));
            status = statusValue == null ? status : normalizeStatus(statusValue);
            txHash = readString(statusData, List.of("transaction", "txHash", "transactionHash")) == null
                    ? txHash
                    : readString(statusData, List.of("transaction", "txHash", "transactionHash"));
        }
        return session(request, status, settlementId == null ? request.idempotencyKey() : settlementId, txHash, payer,
                settlementId, requirements, Map.of("verify", verified, "settlement", settled, "status", statusData, "paymentRequirements", requirements, "metadata", metadata(request)));
    }

    @Override
    public OkxOnchainPaySession getPaymentStatus(String paymentSessionId, OkxOnchainPayCreateRequest request) {
        if (paymentSessionId == null || paymentSessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OKX payment status requires payment id or tx hash");
        }
        // 中文注释：OKX x402 settle/status 按 txHash 查询；本地保留 paymentId 是为了幂等和审计。
        Map<String, Object> raw = requestRaw(SETTLE_STATUS_PATH + "?txHash=" + paymentSessionId, "GET", null);
        Map<String, Object> data = dataRecord(raw);
        String status = normalizeStatus(readString(data, List.of("status", "state", "settleStatus")));
        String txHash = readString(data, List.of("transaction", "txHash", "transactionHash"));
        String settlementId = readString(data, List.of("settlementId", "settleId", "settleRequestId"));
        Map<String, Object> requirements = buildPaymentRequirements(request.amountMinor(), request.asset(), request.recipient());
        // 中文注释：本地 paymentId 只是幂等键，OKX 返回真实 txHash 后才写入链上交易字段，避免把支付单号展示成交易哈希。
        return session(request, status, settlementId == null ? paymentSessionId : settlementId,
                txHash, readString(data, List.of("payer", "from", "sender")),
                settlementId, requirements, Map.of("status", raw, "paymentRequirements", requirements, "metadata", metadata(request)));
    }

    private Map<String, Object> verifyPayment(Map<String, Object> paymentPayload, Map<String, Object> requirements) {
        Map<String, Object> raw = requestRaw(VERIFY_PATH, "POST", Map.of(
                "x402Version", x402Version(paymentPayload),
                "paymentPayload", paymentPayload,
                "paymentRequirements", requirements));
        return dataRecord(raw);
    }

    private Map<String, Object> settlePayment(Map<String, Object> paymentPayload, Map<String, Object> requirements, boolean syncSettle) {
        Map<String, Object> raw = requestRaw(SETTLE_PATH, "POST", Map.of(
                "x402Version", x402Version(paymentPayload),
                "paymentPayload", paymentPayload,
                "paymentRequirements", requirements,
                "syncSettle", syncSettle));
        return dataRecord(raw);
    }

    private Map<String, Object> requestRaw(String path, String method, Map<String, Object> body) {
        requireConfig();
        String bodyText = body == null ? "" : writeJson(body);
        ResponseStatusException lastRetryableFailure = null;
        int maxAttempts = Math.max(1, resiliencePolicy.maxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequest request = buildSignedRequest(path, method, bodyText);
            try {
                // 中文注释：OKX 网关偶发 408/429/5xx 或提前断流时，限定重试能吸收瞬时失败且保留最终错误细节。
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    ResponseStatusException failure = okxHttpFailure(path, response.statusCode(), response.body());
                    if (resiliencePolicy.retryableStatusCode(response.statusCode()) && attempt < maxAttempts) {
                        lastRetryableFailure = failure;
                        sleepBeforeRetry(path, attempt, failure);
                        continue;
                    }
                    throw failure;
                }
                Map<String, Object> raw = readJson(response.body());
                if (raw == null) {
                    throw okxFailure(path, "empty response body", null);
                }
                requireOkxSuccess(path, raw);
                return raw;
            } catch (ResponseStatusException exception) {
                throw exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw okxFailure(path, rootCauseMessage(exception), exception);
            } catch (IOException exception) {
                ResponseStatusException failure = okxFailure(path, rootCauseMessage(exception), exception);
                if (attempt < maxAttempts) {
                    lastRetryableFailure = failure;
                    sleepBeforeRetry(path, attempt, exception);
                    continue;
                }
                throw failure;
            } catch (Exception exception) {
                throw okxFailure(path, rootCauseMessage(exception), exception);
            }
        }
        throw lastRetryableFailure == null ? okxFailure(path, "retry attempts exhausted", null) : lastRetryableFailure;
    }

    private HttpRequest buildSignedRequest(String path, String method, String bodyText) {
        // 中文注释：每次重试都重新签名，避免 OKX 时间戳过旧导致后续尝试直接鉴权失败。
        String timestamp = OKX_TIMESTAMP_FORMATTER.format(Instant.now());
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("OK-ACCESS-KEY", paymentConfig.getOkx().getApiKey().trim())
                .header("OK-ACCESS-SIGN", sign(timestamp, method, path, bodyText))
                .header("OK-ACCESS-TIMESTAMP", timestamp)
                .header("OK-ACCESS-PASSPHRASE", paymentConfig.getOkx().getApiPassphrase().trim());
        String projectId = trim(paymentConfig.getOkx().getProjectId());
        if (projectId != null) {
            builder.header("OK-ACCESS-PROJECT", projectId);
        }
        return bodyText.isBlank()
                ? builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
                : builder.method(method, HttpRequest.BodyPublishers.ofString(bodyText, StandardCharsets.UTF_8)).build();
    }

    private ResponseStatusException okxHttpFailure(String path, int statusCode, String body) {
        String retryHint = resiliencePolicy.retryableStatusCode(statusCode)
                ? " retryable maxAttempts=" + resiliencePolicy.maxAttempts() + " backoffMs=" + resiliencePolicy.backoff().toMillis()
                : " non_retryable";
        return okxFailure(path, "HTTP " + statusCode + retryHint + " " + safeDetail(body), null);
    }

    private void sleepBeforeRetry(String path, int attempt, Exception cause) {
        log.warn("okx onchain pay request retry path={} attempt={} detail={}", path, attempt, safeDetail(rootCauseMessage(cause)));
        try {
            Thread.sleep(resiliencePolicy.backoff().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw okxFailure(path, rootCauseMessage(exception), exception);
        }
    }

    private void requireOkxSuccess(String path, Map<String, Object> raw) {
        Object code = raw.get("code");
        if (code == null || "0".equals(String.valueOf(code))) {
            return;
        }
        throw okxFailure(path, "OKX code=" + code + " " + safeDetail(writeJson(raw)), null);
    }

    private ResponseStatusException okxFailure(String path, String detail, Exception cause) {
        String reason = "OKX Onchain Pay request failed: " + path + " -> " + safeDetail(detail);
        // 中文注释：OKX 失败必须把路径和上游摘要带出来，前端和日志才能区分签名、鉴权、网络和 x402 校验问题。
        log.warn("okx onchain pay request failed path={} detail={}", path, safeDetail(detail), cause);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason, cause);
    }

    private String safeDetail(String value) {
        if (value == null || value.isBlank()) {
            return "no detail";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 800 ? compact : compact.substring(0, 800) + "...";
    }

    private String rootCauseMessage(Exception exception) {
        Throwable cursor = exception;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String type = cursor.getClass().getSimpleName();
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? type : type + ": " + message;
    }

    private OkxOnchainPaySession session(
            OkxOnchainPayCreateRequest request,
            String status,
            String paymentId,
            String txHash,
            String payer,
            String settlementId,
            Map<String, Object> requirements,
            Map<String, Object> raw) {
        return new OkxOnchainPaySession(
                paymentId,
                null,
                status,
                request.amountMinor(),
                normalizeAsset(request.asset()),
                network(request.network()),
                requireRecipient(request.recipient()),
                payer == null ? request.payer() : payer,
                txHash,
                requirements,
                settlementId,
                request.orderId(),
                null,
                raw);
    }

    private String sign(String timestamp, String method, String path, String bodyText) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(paymentConfig.getOkx().getApiSecret().trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal((timestamp + method + path + bodyText).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign OKX request", exception);
        }
    }

    private void requireConfig() {
        if (trim(paymentConfig.getOkx().getApiKey()) == null
                || trim(paymentConfig.getOkx().getApiSecret()) == null
                || trim(paymentConfig.getOkx().getApiPassphrase()) == null
                || trim(paymentConfig.getOkx().getDefaultRecipient()) == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "OKX Onchain Pay config missing");
        }
    }

    private String baseUrl() {
        return paymentConfig.getOkx().getApiBaseUrl().replaceAll("/+$", "");
    }

    private String requireRecipient(String recipient) {
        String value = trim(recipient);
        if (value != null) return value;
        value = trim(paymentConfig.getOkx().getDefaultRecipient());
        if (value != null) return value;
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "OKX Onchain Pay recipient missing");
    }

    private String normalizeAsset(String asset) {
        String value = trim(asset);
        if (value == null) value = trim(paymentConfig.getOkx().getDefaultAsset());
        return value == null ? "USDC" : value.toUpperCase(Locale.ROOT);
    }

    private String network(String network) {
        String value = trim(network);
        if (value != null && !XLAYER_CHAIN_ID.equals(value)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OKX Onchain Pay only supports X Layer eip155:196");
        }
        return XLAYER_CHAIN_ID;
    }

    private Map<String, Object> metadata(OkxOnchainPayCreateRequest request) {
        return request.metadata() == null ? Map.of() : request.metadata();
    }

    private String normalizeStatus(String value) {
        String status = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (status.equals("completed") || status.equals("success") || status.equals("settled")) return "completed";
        if (status.equals("funded")) return "funded";
        if (status.equals("expired")) return "expired";
        if (status.equals("cancelled") || status.equals("canceled")) return "cancelled";
        if (status.equals("failed") || status.equals("rejected")) return "failed";
        if (status.equals("requires_payment") || status.equals("created")) return "requires_payment";
        return "pending";
    }

    private int x402Version(Map<String, Object> paymentPayload) {
        Object value = paymentPayload.get("x402Version");
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && !text.isBlank()) return Integer.parseInt(text);
        return 2;
    }

    private Map<String, Object> dataRecord(Map<String, Object> raw) {
        Object data = raw.get("data");
        if (data instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> map) {
            return mapOf(map);
        }
        if (data instanceof Map<?, ?> map) return mapOf(map);
        return raw;
    }

    private String readString(Map<String, Object> record, List<String> keys) {
        for (String key : keys) {
            Object value = record.get(key);
            if (value instanceof String text && !text.isBlank()) return text.trim();
        }
        return null;
    }

    private boolean readBoolean(Map<String, Object> record, List<String> keys) {
        for (String key : keys) {
            Object value = record.get(key);
            if (value instanceof Boolean bool) return bool;
            if (value instanceof String text && !text.isBlank()) {
                String normalized = text.trim().toLowerCase(Locale.ROOT);
                if (normalized.equals("true") || normalized.equals("success")) return true;
                if (normalized.equals("false") || normalized.equals("failed")) return false;
            }
        }
        return false;
    }

    private Map<String, Object> mapOf(Map<?, ?> input) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String writeJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize OKX request", exception);
        }
    }

    private Map<String, Object> readJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse OKX response", exception);
        }
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
