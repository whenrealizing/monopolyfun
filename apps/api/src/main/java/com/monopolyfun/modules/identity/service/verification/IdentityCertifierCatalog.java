package com.monopolyfun.modules.identity.service.verification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class IdentityCertifierCatalog {
    public static final String METHOD_PUBLIC_PROOF = "public_proof";

    private static final Map<String, BadgeSpec> BADGE_SPECS = Map.of(
            "x_public_proof", new BadgeSpec("x_public_proof_verified", "X 已验证", "x", 90),
            "reddit_public_proof", new BadgeSpec("reddit_public_proof_verified", "Reddit 已验证", "reddit", 80),
            "youtube_public_proof", new BadgeSpec("youtube_public_proof_verified", "YouTube 已验证", "youtube", 80));

    private IdentityCertifierCatalog() {
    }

    public static PublicProofSpec xPublicProofSpec() {
        return publicProofSpec(
                "x_public_proof",
                "X",
                "x",
                "发布推文、评论或个人简介 token，证明当前账号控制 X 身份。",
                List.of("post", "comment", "bio"),
                90);
    }

    public static PublicProofSpec redditPublicProofSpec() {
        return publicProofSpec(
                "reddit_public_proof",
                "Reddit",
                "reddit",
                "发布帖子、评论或个人简介 token，证明当前账号控制 Reddit 身份。",
                List.of("post", "comment", "bio"),
                90);
    }

    public static PublicProofSpec youtubePublicProofSpec() {
        return publicProofSpec(
                "youtube_public_proof",
                "YouTube",
                "youtube",
                "在频道简介、视频简介或评论中放置 token，证明当前账号控制 YouTube 身份。",
                List.of("channel_about", "video_description", "comment"),
                90);
    }

    public static Optional<BadgeSpec> badgeSpec(String certifierId) {
        return Optional.ofNullable(BADGE_SPECS.get(certifierId));
    }

    private static PublicProofSpec publicProofSpec(
            String certifierId,
            String name,
            String provider,
            String description,
            List<String> placements,
            int expiresInDays) {
        return new PublicProofSpec(
                certifierId,
                provider,
                placements,
                new IdentityCertifierManifest(
                        certifierId,
                        name,
                        provider,
                        METHOD_PUBLIC_PROOF,
                        description,
                        "medium",
                        BADGE_SPECS.get(certifierId).code(),
                        expiresInDays,
                        inputSchema(List.of("handle", "proofPlacement"), Map.<String, Object>of("proofPlacement", placements)),
                        inputSchema(List.of("proofUrl"), Map.of())));
    }

    private static Map<String, Object> inputSchema(List<String> required, Map<String, Object> options) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("required", required);
        schema.put("options", options);
        return schema;
    }

    public record BadgeSpec(String code, String label, String icon, int weight) {
    }
}
