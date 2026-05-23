package com.monopolyfun.modules.post.service;

import com.monopolyfun.modules.post.domain.PostKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PostItemSupport {
    public static final String SUBJECT_TYPE = "post_item";
    public static final int PROJECT_SHARE_TOTAL = 21_000_000;
    public static final int PROJECT_TASK_BUDGET = 10_500_000;
    public static final int PROJECT_RESERVE_BUDGET = 4_200_000;
    public static final int DEFAULT_LOCK_TIMEOUT_SECONDS = 1_800;
    public static final int DEFAULT_PROGRESS_TIMEOUT_SECONDS = 1_800;
    public static final int INITIAL_BASE_REWARD = 8_000;
    public static final double REWARD_DECAY = 0.997d;
    public static final int MIN_BASE_REWARD = 300;
    public static final double DEFAULT_PROJECT_DIFFICULTY_SCORE = 1d;
    public static final String FULFILLMENT_MODE_REVIEWED = "reviewed_delivery";
    public static final String FULFILLMENT_MODE_INSTANT = "instant_fulfillment";
    public static final String FULFILLMENT_MODE_STOCK = "stock_fulfillment";
    public static final String DELIVERY_MODE_REVIEWED = "reviewed_delivery";
    public static final String DELIVERY_MODE_INSTANT = "instant_fulfillment";
    public static final String DELIVERY_MODE_STOCK = "stock_fulfillment";

    private PostItemSupport() {
    }

    public static String marketIdForPost(PostKind postKind, String postId) {
        return "mkt-public-" + postKind.name().toLowerCase(Locale.ROOT) + "-" + postId;
    }

    public static Map<String, Object> defaultMarketMetadata(PostKind postKind) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", postKind.name().toLowerCase(Locale.ROOT) + "-bootstrap");
        metadata.put("postKind", postKind.name().toLowerCase(Locale.ROOT));
        return metadata;
    }

    public static Map<String, Object> createItemMetadata(
            PostKind postKind,
            String postId,
            String summary,
            String itemKind,
            String fulfillmentMode,
            String deliveryMode,
            String deliverySource,
            String buyerNotePlaceholder,
            String agentInstruction,
            List<String> acceptanceCriteria,
            String priority,
            BigDecimal priceAmount,
            BigDecimal budgetAmount,
            String currency,
            String paymentMethod,
            String paymentNetwork,
            String paymentRecipient,
            Double difficultyScore,
            int lockTimeoutSeconds,
            int progressTimeoutSeconds) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("postKind", postKind.name().toLowerCase(Locale.ROOT));
        metadata.put("postId", postId);
        metadata.put("summary", summary);
        metadata.put("itemKind", itemKind);
        metadata.put("fulfillmentMode", fulfillmentMode);
        metadata.put("deliveryMode", deliveryMode);
        metadata.put("deliverySource", deliverySource);
        if (buyerNotePlaceholder != null && !buyerNotePlaceholder.isBlank())
            metadata.put("buyerNotePlaceholder", buyerNotePlaceholder);
        if (agentInstruction != null && !agentInstruction.isBlank()) metadata.put("agentInstruction", agentInstruction);
        metadata.put("acceptanceCriteria", acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria));
        metadata.put("priority", priority);
        if (priceAmount != null) metadata.put("priceAmount", priceAmount);
        if (budgetAmount != null) metadata.put("budgetAmount", budgetAmount);
        if (currency != null && !currency.isBlank()) metadata.put("currency", currency);
        if (paymentMethod != null && !paymentMethod.isBlank()) metadata.put("paymentMethod", paymentMethod);
        if (paymentNetwork != null && !paymentNetwork.isBlank()) metadata.put("paymentNetwork", paymentNetwork);
        if (paymentRecipient != null && !paymentRecipient.isBlank()) metadata.put("paymentRecipient", paymentRecipient);
        if (difficultyScore != null) metadata.put("difficultyScore", difficultyScore);
        metadata.put("lockTimeoutSeconds", lockTimeoutSeconds);
        metadata.put("progressTimeoutSeconds", progressTimeoutSeconds);
        return metadata;
    }

    public static Map<String, Object> createOrderMetadata(
            PostKind postKind,
            String postId,
            String itemId,
            String fulfillmentMode,
            String deliveryMode,
            String deliverySource,
            String buyerNote,
            Integer reservedShares,
            Integer reservedCurveSlot,
            Double difficultyScoreSnapshot,
            Instant lockExpiresAt,
            Instant nextProgressDueAt) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("postKind", postKind.name().toLowerCase(Locale.ROOT));
        metadata.put("postId", postId);
        metadata.put("itemId", itemId);
        metadata.put("fulfillmentMode", fulfillmentMode);
        metadata.put("deliveryMode", deliveryMode);
        metadata.put("deliverySource", deliverySource);
        metadata.put("buyerNote", buyerNote == null ? "" : buyerNote);
        if (reservedShares != null) metadata.put("reservedShares", reservedShares);
        if (reservedCurveSlot != null) metadata.put("reservedCurveSlot", reservedCurveSlot);
        if (difficultyScoreSnapshot != null) metadata.put("difficultyScoreSnapshot", difficultyScoreSnapshot);
        if (reservedShares != null) metadata.put("rewardSharesPreview", reservedShares);
        if (lockExpiresAt != null) metadata.put("lockExpiresAt", lockExpiresAt.toString());
        if (nextProgressDueAt != null) metadata.put("nextProgressDueAt", nextProgressDueAt.toString());
        metadata.put("progressCount", 0);
        return metadata;
    }

    public static Map<String, Object> withOrderParticipants(
            Map<String, Object> base,
            PostKind postKind,
            String ownerAccountId,
            String claimantAccountId) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(base == null ? Map.of() : base);
        switch (postKind) {
            case OFFER -> {
                putParticipant(metadata, "buyerAccountId", claimantAccountId);
                putParticipant(metadata, "sellerAccountId", ownerAccountId);
                putParticipant(metadata, "fulfillerAccountId", ownerAccountId);
                putParticipant(metadata, "acceptorAccountId", claimantAccountId);
            }
            case REQUEST -> {
                putParticipant(metadata, "buyerAccountId", ownerAccountId);
                putParticipant(metadata, "sellerAccountId", claimantAccountId);
                putParticipant(metadata, "fulfillerAccountId", claimantAccountId);
                putParticipant(metadata, "acceptorAccountId", ownerAccountId);
            }
            case PROJECT -> {
                putParticipant(metadata, "buyerAccountId", ownerAccountId);
                putParticipant(metadata, "sellerAccountId", claimantAccountId);
                putParticipant(metadata, "fulfillerAccountId", claimantAccountId);
                putParticipant(metadata, "acceptorAccountId", ownerAccountId);
            }
            case REVIEW -> {
                putParticipant(metadata, "buyerAccountId", ownerAccountId);
                putParticipant(metadata, "sellerAccountId", claimantAccountId);
                putParticipant(metadata, "fulfillerAccountId", claimantAccountId);
                putParticipant(metadata, "acceptorAccountId", ownerAccountId);
            }
        }
        metadata.put("roleModelVersion", com.monopolyfun.modules.order.domain.OrderEntity.ROLE_MODEL_VERSION);
        return metadata;
    }

    private static void putParticipant(Map<String, Object> metadata, String key, String accountId) {
        if (accountId != null && !accountId.isBlank()) {
            metadata.put(key, accountId);
        }
    }

    public static Map<String, Object> withOrderTimingMetadata(
            Map<String, Object> base,
            Instant lockExpiresAt,
            Instant nextProgressDueAt,
            Instant lastProgressAt,
            int progressCount) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(base);
        if (lockExpiresAt != null) metadata.put("lockExpiresAt", lockExpiresAt.toString());
        if (nextProgressDueAt != null) metadata.put("nextProgressDueAt", nextProgressDueAt.toString());
        if (lastProgressAt != null) metadata.put("lastProgressAt", lastProgressAt.toString());
        metadata.put("progressCount", progressCount);
        return metadata;
    }

    public static boolean isPostItem(Map<String, Object> metadata) {
        return postKind(metadata) != null && postId(metadata) != null;
    }

    public static PostKind postKind(Map<String, Object> metadata) {
        String value = readString(metadata, "postKind", null);
        if (value == null) return null;
        try {
            return PostKind.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static String postId(Map<String, Object> metadata) {
        return readString(metadata, "postId", null);
    }

    public static String itemId(Map<String, Object> metadata, String fallback) {
        return readString(metadata, "itemId", fallback);
    }

    public static String itemKind(Map<String, Object> metadata) {
        return readString(metadata, "itemKind", "work");
    }

    public static String fulfillmentMode(Map<String, Object> metadata) {
        return readString(metadata, "fulfillmentMode", FULFILLMENT_MODE_REVIEWED);
    }

    public static String deliveryMode(Map<String, Object> metadata) {
        return readString(metadata, "deliveryMode", DELIVERY_MODE_REVIEWED);
    }

    public static String deliverySource(Map<String, Object> metadata) {
        return readString(metadata, "deliverySource", "submitted_result");
    }

    public static String buyerNotePlaceholder(Map<String, Object> metadata) {
        return readString(metadata, "buyerNotePlaceholder", "");
    }

    public static String agentInstruction(Map<String, Object> metadata) {
        return readString(metadata, "agentInstruction", "");
    }

    public static List<String> acceptanceCriteria(Map<String, Object> metadata) {
        Object value = metadata.get("acceptanceCriteria");
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(item -> String.valueOf(item).trim())
                    .toList();
        }
        return List.of();
    }

    public static boolean isReviewedDelivery(Map<String, Object> metadata) {
        return DELIVERY_MODE_REVIEWED.equalsIgnoreCase(deliveryMode(metadata));
    }

    public static boolean isInstantFulfillment(Map<String, Object> metadata) {
        return DELIVERY_MODE_INSTANT.equalsIgnoreCase(deliveryMode(metadata));
    }

    public static boolean isStockFulfillment(Map<String, Object> metadata) {
        return DELIVERY_MODE_STOCK.equalsIgnoreCase(deliveryMode(metadata));
    }

    public static boolean isReviewedFulfillment(Map<String, Object> metadata) {
        return FULFILLMENT_MODE_REVIEWED.equalsIgnoreCase(fulfillmentMode(metadata));
    }

    public static int lockTimeoutSeconds(Map<String, Object> metadata) {
        return readInt(metadata, "lockTimeoutSeconds", DEFAULT_LOCK_TIMEOUT_SECONDS);
    }

    public static int progressTimeoutSeconds(Map<String, Object> metadata) {
        return readInt(metadata, "progressTimeoutSeconds", DEFAULT_PROGRESS_TIMEOUT_SECONDS);
    }

    public static double difficultyScore(Map<String, Object> metadata) {
        return readDouble(metadata, "difficultyScore", DEFAULT_PROJECT_DIFFICULTY_SCORE);
    }

    public static String summary(Map<String, Object> metadata) {
        return readString(metadata, "summary", "");
    }

    public static String priority(Map<String, Object> metadata) {
        return readString(metadata, "priority", "medium");
    }

    public static Instant metadataInstant(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) return null;
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static BigDecimal metadataAmount(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal amount) return amount.stripTrailingZeros();
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros();
        try {
            return new BigDecimal(String.valueOf(value).trim()).stripTrailingZeros();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Integer metadataInt(Map<String, Object> metadata, String key) {
        BigDecimal value = metadataAmount(metadata, key);
        if (value == null) return null;
        try {
            return value.intValueExact();
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    private static int readInt(Map<String, Object> metadata, String key, int fallback) {
        Integer value = metadataInt(metadata, key);
        return value == null ? fallback : value;
    }

    private static double readDouble(Map<String, Object> metadata, String key, double fallback) {
        Object value = metadata.get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static String readString(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }
}
