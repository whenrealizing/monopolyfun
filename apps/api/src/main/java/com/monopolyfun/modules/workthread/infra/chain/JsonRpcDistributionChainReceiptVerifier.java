package com.monopolyfun.modules.workthread.infra.chain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monopolyfun.modules.workthread.domain.DistributionBatchEntity;
import com.monopolyfun.modules.workthread.domain.DistributionClaimEntity;
import com.monopolyfun.modules.workthread.domain.ProjectRevenueAddressEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class JsonRpcDistributionChainReceiptVerifier implements DistributionChainReceiptVerifier {
    private static final String TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    private static final String CLAIMED_TOPIC = "0xd435c9bd215aac2214787e7b4dfbde0dfd275bdf73008b16d59b23e5165bff14";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public JsonRpcDistributionChainReceiptVerifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public void verifyClaim(ProjectRevenueAddressEntity revenueAddress, DistributionBatchEntity batch, DistributionClaimEntity claim, String txHash) {
        String rpcUrl = rpcUrl(revenueAddress.chainId());
        Map<String, Object> receipt = receipt(rpcUrl, txHash);
        require("0x1".equalsIgnoreCase(string(receipt.get("status"))), "Revenue claim receipt is not successful");
        require(eqAddress(string(receipt.get("to")), revenueAddress.contractAddress()), "Revenue claim receipt target does not match distributor");
        List<Object> logs = list(receipt.get("logs"));
        require(hasClaimedLog(logs, revenueAddress.contractAddress(), batch.period(), claim.accountId(), claim.walletAddress(), claim.amountMinor()),
                "Revenue claim receipt missing matching Claimed event");
        require(hasTransferLog(logs, revenueAddress.tokenAddress(), revenueAddress.contractAddress(), claim.walletAddress(), claim.amountMinor()),
                "Revenue claim receipt missing matching Transfer event");
    }

    private Map<String, Object> receipt(String rpcUrl, String txHash) {
        Map<String, Object> body = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "eth_getTransactionReceipt",
                "params", List.of(txHash));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(rpcUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Revenue chain RPC request failed");
            }
            Map<String, Object> payload = objectMapper.readValue(response.body(), MAP_TYPE);
            Object result = payload.get("result");
            if (result instanceof Map<?, ?> map) {
                Map<String, Object> receipt = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    receipt.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return receipt;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Revenue claim receipt not found");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Revenue chain RPC interrupted", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Revenue chain RPC failed", exception);
        }
    }

    private boolean hasClaimedLog(List<Object> logs, String distributorAddress, String period, String accountId, String walletAddress, int amountMinor) {
        for (Object item : logs) {
            Map<String, Object> log = record(item);
            List<Object> topics = list(log.get("topics"));
            if (topics.size() < 2 || !eqAddress(string(log.get("address")), distributorAddress) || !CLAIMED_TOPIC.equalsIgnoreCase(string(topics.getFirst()))) {
                continue;
            }
            if (!topicAddressEquals(string(topics.get(1)), walletAddress)) {
                continue;
            }
            ClaimedEvent event = decodeClaimedData(string(log.get("data")));
            if (event != null && period.equals(event.period()) && accountId.equals(event.accountId()) && BigInteger.valueOf(amountMinor).equals(event.amount())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTransferLog(List<Object> logs, String tokenAddress, String distributorAddress, String walletAddress, int amountMinor) {
        for (Object item : logs) {
            Map<String, Object> log = record(item);
            List<Object> topics = list(log.get("topics"));
            if (topics.size() < 3 || !eqAddress(string(log.get("address")), tokenAddress) || !TRANSFER_TOPIC.equalsIgnoreCase(string(topics.getFirst()))) {
                continue;
            }
            if (topicAddressEquals(string(topics.get(1)), distributorAddress)
                    && topicAddressEquals(string(topics.get(2)), walletAddress)
                    && BigInteger.valueOf(amountMinor).equals(hexBigInteger(string(log.get("data"))))) {
                return true;
            }
        }
        return false;
    }

    private ClaimedEvent decodeClaimedData(String data) {
        byte[] bytes = hexBytes(data);
        if (bytes.length < 96) {
            return null;
        }
        int periodOffset = uint(bytes, 0).intValueExact();
        int accountOffset = uint(bytes, 32).intValueExact();
        BigInteger amount = uint(bytes, 64);
        String period = abiString(bytes, periodOffset);
        String accountId = abiString(bytes, accountOffset);
        return period == null || accountId == null ? null : new ClaimedEvent(period, accountId, amount);
    }

    private String abiString(byte[] bytes, int offset) {
        if (offset < 0 || offset + 32 > bytes.length) {
            return null;
        }
        int length = uint(bytes, offset).intValueExact();
        int start = offset + 32;
        if (length < 0 || start + length > bytes.length) {
            return null;
        }
        return new String(bytes, start, length, StandardCharsets.UTF_8);
    }

    private String rpcUrl(String chainId) {
        String key = "MONOPOLYFUN_REVENUE_RPC_" + chainId.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Revenue chain RPC is not configured for " + chainId);
        }
        return value.trim();
    }

    private static BigInteger uint(byte[] bytes, int offset) {
        byte[] chunk = new byte[32];
        System.arraycopy(bytes, offset, chunk, 0, 32);
        return new BigInteger(1, chunk);
    }

    private static BigInteger hexBigInteger(String value) {
        return new BigInteger(cleanHex(value), 16);
    }

    private static byte[] hexBytes(String value) {
        String clean = cleanHex(value);
        return HexFormat.of().parseHex(clean.length() % 2 == 0 ? clean : "0" + clean);
    }

    private static String cleanHex(String value) {
        String text = value == null ? "" : value.trim();
        return text.startsWith("0x") || text.startsWith("0X") ? text.substring(2) : text;
    }

    private static boolean topicAddressEquals(String topic, String address) {
        String cleanTopic = cleanHex(topic);
        String cleanAddress = cleanHex(address);
        return cleanTopic.length() >= 40 && cleanTopic.substring(cleanTopic.length() - 40).equalsIgnoreCase(cleanAddress);
    }

    private static boolean eqAddress(String left, String right) {
        return cleanHex(left).equalsIgnoreCase(cleanHex(right));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static List<Object> list(Object value) {
        return value instanceof List<?> list ? List.copyOf(list) : List.of();
    }

    private static Map<String, Object> record(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> record = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                record.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return record;
        }
        return Map.of();
    }

    private record ClaimedEvent(String period, String accountId, BigInteger amount) {
    }
}
