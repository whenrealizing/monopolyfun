package com.monopolyfun.modules.workthread.service;

import com.monopolyfun.modules.project.domain.ProjectEntity;
import com.monopolyfun.modules.workthread.domain.ProjectRevenueAddressEntity;
import com.monopolyfun.modules.workthread.infra.WorkThreadRepository;
import com.monopolyfun.modules.workthread.service.view.RevenueAutomationView;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RevenueAutomationService {
    private static final String NATIVE_TOKEN_ADDRESS = "0x0000000000000000000000000000000000000000";
    private static final int DEFAULT_MIN_DISTRIBUTION_MINOR = 100_000;
    private static final int DEFAULT_REVENUE_MINOR_PER_SHARE = 20;
    private static final Map<String, Integer> DIFFICULTY_TASK_VALUES = Map.of(
            "easy", 1500,
            "medium", 3000,
            "hard", 5000,
            "expert", 8000);

    private final WorkThreadRepository repository;
    private final String chainId;
    private final String chainName;
    private final String asset;
    private final String tokenType;
    private final String tokenAddress;
    private final String routerAddress;
    private final int minDistributionMinor;
    private final int revenueMinorPerShare;

    public RevenueAutomationService(
            WorkThreadRepository repository,
            Environment environment) {
        this.repository = repository;
        this.chainId = readString(environment, "monopolyfun.revenue.chain-id", "MONOPOLYFUN_REVENUE_CHAIN_ID", "eip155:56");
        this.chainName = readString(environment, "monopolyfun.revenue.chain-name", "MONOPOLYFUN_REVENUE_CHAIN_NAME", "BSC");
        this.asset = readString(environment, "monopolyfun.revenue.asset", "MONOPOLYFUN_REVENUE_ASSET", "BNB");
        this.tokenType = readString(environment, "monopolyfun.revenue.token-type", "MONOPOLYFUN_REVENUE_TOKEN_TYPE", "native");
        this.tokenAddress = readString(environment, "monopolyfun.revenue.token-address", "MONOPOLYFUN_REVENUE_TOKEN_ADDRESS", NATIVE_TOKEN_ADDRESS);
        this.routerAddress = readString(environment, "monopolyfun.revenue.router-address", "MONOPOLYFUN_REVENUE_ROUTER_ADDRESS",
                readString(environment, "monopolyfun.revenue.contract-address", "MONOPOLYFUN_REVENUE_CONTRACT_ADDRESS", ""));
        this.minDistributionMinor = Math.max(1, readInt(environment, "monopolyfun.revenue.min-distribution-minor", "MONOPOLYFUN_REVENUE_MIN_DISTRIBUTION_MINOR", DEFAULT_MIN_DISTRIBUTION_MINOR));
        this.revenueMinorPerShare = Math.max(1, readInt(environment, "monopolyfun.revenue.minor-per-share", "MONOPOLYFUN_REVENUE_MINOR_PER_SHARE", DEFAULT_REVENUE_MINOR_PER_SHARE));
    }

    public Optional<ProjectRevenueAddressEntity> ensureSystemRevenueTrack(ProjectEntity project, Instant now) {
        Optional<ProjectRevenueAddressEntity> existing = repository.findActiveRevenueAddress(project.id());
        if (existing.isPresent() || !configured()) {
            return existing;
        }
        // 中文注释：收益轨道由系统配置初始化，OpenClaw 用户流无需暴露 chainId、router 和 token。
        ProjectRevenueAddressEntity saved = repository.saveRevenueAddress(new ProjectRevenueAddressEntity(
                "pra-" + UUID.randomUUID(),
                project.id(),
                chainId,
                routerAddress,
                tokenAddress,
                "active",
                now,
                now));
        return Optional.of(saved);
    }

    public RevenueAutomationView view(ProjectEntity project, int totalSnapshotShares) {
        return new RevenueAutomationView(
                chainId,
                chainName,
                asset,
                tokenType,
                tokenAddress,
                configured(),
                false,
                estimateDistributionRevenueMinor(totalSnapshotShares),
                "bonding_curve_v1");
    }

    public int estimateTaskValue(Integer requestedValue, String difficulty, String creativity, String title, String goal) {
        if (requestedValue != null && requestedValue > 0) {
            return Math.min(10_000, requestedValue);
        }
        String level = difficultyLevel(firstNonBlank(difficulty, title, goal));
        int base = DIFFICULTY_TASK_VALUES.getOrDefault(level, DIFFICULTY_TASK_VALUES.get("medium"));
        double creativityMultiplier = creativityMultiplier(firstNonBlank(creativity, title, goal));
        return Math.max(1, Math.min(10_000, (int) Math.round(base * creativityMultiplier)));
    }

    public int estimateDistributionRevenueMinor(int totalSnapshotShares) {
        int curveAmount = Math.max(0, totalSnapshotShares) * revenueMinorPerShare;
        return Math.max(minDistributionMinor, curveAmount);
    }

    private boolean configured() {
        return isEvmAddress(routerAddress) && isEvmAddress(tokenAddress);
    }

    private static boolean isEvmAddress(String value) {
        return value != null && value.trim().matches("0x[a-fA-F0-9]{40}");
    }

    private static String difficultyLevel(String value) {
        String text = normalize(value);
        if (text.contains("expert") || text.contains("极难") || text.contains("专家") || text.contains("d5")) {
            return "expert";
        }
        if (text.contains("hard") || text.contains("高难") || text.contains("困难") || text.contains("复杂") || text.contains("d4")) {
            return "hard";
        }
        if (text.contains("easy") || text.contains("简单") || text.contains("容易") || text.contains("d1")) {
            return "easy";
        }
        return "medium";
    }

    private static double creativityMultiplier(String value) {
        String text = normalize(value);
        if (text.contains("创新") || text.contains("创意") || text.contains("探索") || text.contains("creative")) {
            return 1.25;
        }
        if (text.contains("常规") || text.contains("普通") || text.contains("standard")) {
            return 1.0;
        }
        return 1.1;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String normalize(String value) {
        return (value == null ? "" : value).toLowerCase(Locale.ROOT);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String readString(Environment environment, String propertyKey, String envKey, String fallback) {
        String value = environment.getProperty(propertyKey);
        if (value == null || value.isBlank()) {
            value = environment.getProperty(envKey);
        }
        return blankToDefault(value, fallback);
    }

    private static int readInt(Environment environment, String propertyKey, String envKey, int fallback) {
        String value = readString(environment, propertyKey, envKey, String.valueOf(fallback));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
