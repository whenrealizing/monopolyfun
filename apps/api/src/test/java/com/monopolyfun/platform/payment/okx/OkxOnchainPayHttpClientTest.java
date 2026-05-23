package com.monopolyfun.modules.payment.infra.okx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monopolyfun.config.PaymentConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkxOnchainPayHttpClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void verifyRequestUsesStableHttpHeadersAndParsesOkxData() throws Exception {
        Capture capture = new Capture();
        startServer("/api/v6/pay/x402/verify", exchange -> {
            capture.method = exchange.getRequestMethod();
            capture.path = exchange.getRequestURI().toString();
            capture.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            capture.accessKey = exchange.getRequestHeaders().getFirst("OK-ACCESS-KEY");
            capture.passphrase = exchange.getRequestHeaders().getFirst("OK-ACCESS-PASSPHRASE");
            capture.project = exchange.getRequestHeaders().getFirst("OK-ACCESS-PROJECT");
            capture.timestamp = exchange.getRequestHeaders().getFirst("OK-ACCESS-TIMESTAMP");
            capture.signature = exchange.getRequestHeaders().getFirst("OK-ACCESS-SIGN");
            capture.userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            respond(exchange, 200, """
                    {"code":"0","data":[{"isValid":false,"invalidReason":"signature_invalid","payer":"0xabc"}]}
                    """);
        });

        var client = new OkxOnchainPayHttpClient(config(serverUrl(), "project-1"), objectMapper);
        var session = client.createPayment(new OkxOnchainPayCreateRequest(
                "order-1",
                "intent-1",
                25,
                "USDC",
                "eip155:196",
                "0x1111111111111111111111111111111111111111",
                "0xabc",
                Map.of("x402Version", 2, "accepted", Map.of("scheme", "exact"), "payload", Map.of("signature", "0xsig")),
                true,
                Map.of()));

        assertEquals("failed", session.status());
        assertEquals("POST", capture.method);
        assertEquals("/api/v6/pay/x402/verify", capture.path);
        assertEquals("key-1", capture.accessKey);
        assertEquals("pass-1", capture.passphrase);
        assertEquals("project-1", capture.project);
        assertEquals("monopolyfun-api/0.1 okx-onchain-pay", capture.userAgent);
        assertEquals(hmac(capture.timestamp + "POST" + capture.path + capture.body, "secret-1"), capture.signature);
        assertTrue(capture.body.contains("\"paymentRequirements\""));
        assertTrue(capture.body.contains("\"paymentPayload\""));
    }

    @Test
    void httpErrorIncludesOkxResponseBody() throws Exception {
        startServer("/api/v6/pay/x402/verify", exchange -> respond(exchange, 401, """
                {"msg":"Request header OK-ACCESS-KEY can not be empty.","code":"50103"}
                """));

        var client = new OkxOnchainPayHttpClient(config(serverUrl(), null), objectMapper);
        ResponseStatusException exception = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class, () ->
                client.createPayment(new OkxOnchainPayCreateRequest(
                        "order-1",
                        "intent-1",
                        25,
                        "USDC",
                        "eip155:196",
                        "0x1111111111111111111111111111111111111111",
                        "0xabc",
                        Map.of("x402Version", 2, "accepted", Map.of("scheme", "exact"), "payload", Map.of("signature", "0xsig")),
                        true,
                        Map.of())));

        assertEquals(502, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("HTTP 401"));
        assertTrue(exception.getReason().contains("50103"));
        assertFalse(exception.getReason().contains("EOFException"));
    }

    @Test
    void retryableHttpStatusRetriesBeforeReturningOkxData() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startServer("/api/v6/pay/x402/verify", exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 500, """
                        {"msg":"temporary gateway failure","code":"50000"}
                        """);
                return;
            }
            respond(exchange, 200, """
                    {"code":"0","data":[{"isValid":false,"invalidReason":"signature_invalid","payer":"0xabc"}]}
                    """);
        });

        var client = new OkxOnchainPayHttpClient(config(serverUrl(), null), objectMapper);
        var session = client.createPayment(new OkxOnchainPayCreateRequest(
                "order-1",
                "intent-1",
                25,
                "USDC",
                "eip155:196",
                "0x1111111111111111111111111111111111111111",
                "0xabc",
                Map.of("x402Version", 2, "accepted", Map.of("scheme", "exact"), "payload", Map.of("signature", "0xsig")),
                true,
                Map.of()));

        assertEquals(2, attempts.get());
        assertEquals("failed", session.status());
    }

    @Test
    void settleAndStatusParseTransactionAlias() throws Exception {
        String txHash = "0xbeba3e4a5ee44de0412caceb3cd3bcca13a7581e21db7da11259f906f1a57e73";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // 中文注释：OKX 实际回包字段使用 transaction，客户端必须保存 txHash，后续 refresh 才能完成链上对账。
        server.createContext("/api/v6/pay/x402/verify", exchange -> respond(exchange, 200, """
                {"code":"0","data":{"isValid":true,"payer":"0xabc"}}
                """));
        server.createContext("/api/v6/pay/x402/settle", exchange -> respond(exchange, 200, """
                {"code":"0","data":{"success":true,"status":"timeout","transaction":"%s","payer":"0xabc","network":"eip155:196"}}
                """.formatted(txHash)));
        server.createContext("/api/v6/pay/x402/settle/status", exchange -> respond(exchange, 200, """
                {"code":"0","data":{"success":true,"status":"success","transaction":"%s","payer":"0xabc","network":"eip155:196"}}
                """.formatted(txHash)));
        server.start();

        var client = new OkxOnchainPayHttpClient(config(serverUrl(), null), objectMapper);
        var request = new OkxOnchainPayCreateRequest(
                "order-1",
                "intent-1",
                25,
                "USDC",
                "eip155:196",
                "0x1111111111111111111111111111111111111111",
                "0xabc",
                Map.of("x402Version", 2, "accepted", Map.of("scheme", "exact"), "payload", Map.of("signature", "0xsig")),
                true,
                Map.of());

        var session = client.createPayment(request);
        assertEquals("completed", session.status());
        assertEquals(txHash, session.txHash());

        var refreshed = client.getPaymentStatus(session.txHash(), request);
        assertEquals("completed", refreshed.status());
        assertEquals(txHash, refreshed.txHash());
    }

    private void startServer(String path, ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // 中文注释：本地 HTTP server 固定校验 OKX 出站协议，避免单测依赖真实网关或真实密钥。
        server.createContext(path, exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception exception) {
                respond(exchange, 500, exception.getMessage());
            }
        });
        server.start();
    }

    private String serverUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private PaymentConfig config(String baseUrl, String projectId) {
        PaymentConfig config = new PaymentConfig();
        config.getOkx().setApiBaseUrl(baseUrl);
        config.getOkx().setApiKey("key-1");
        config.getOkx().setApiSecret("secret-1");
        config.getOkx().setApiPassphrase("pass-1");
        config.getOkx().setProjectId(projectId);
        config.getOkx().setDefaultRecipient("0x1111111111111111111111111111111111111111");
        return config;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    private static final class Capture {
        private String method;
        private String path;
        private String body;
        private String accessKey;
        private String passphrase;
        private String project;
        private String timestamp;
        private String signature;
        private String userAgent;
    }
}
