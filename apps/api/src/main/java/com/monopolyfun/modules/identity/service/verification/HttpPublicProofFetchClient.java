package com.monopolyfun.modules.identity.service.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class HttpPublicProofFetchClient implements PublicProofFetchClient {
    private static final Logger log = LoggerFactory.getLogger(HttpPublicProofFetchClient.class);
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("x", "reddit", "youtube");
    private static final Map<String, Set<String>> PROVIDER_HOSTS = Map.of(
            "x", Set.of("x.com", "www.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com"),
            "reddit", Set.of("reddit.com", "www.reddit.com", "old.reddit.com"),
            "youtube", Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"));

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public boolean supports(String provider) {
        return SUPPORTED_PROVIDERS.contains(provider);
    }

    @Override
    public PublicProofDocument fetch(String provider, URI proofUri, String proofPlacement) {
        validateHost(provider, proofUri);
        Instant observedAt = Instant.now();
        String body = fetchBody(provider, proofUri);
        String authorHandle = authorHandle(provider, proofUri, body)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Proof author fetch failed"));
        String displayName = displayName(provider, body).orElse(authorHandle);
        String profileUrl = profileUrl(provider, authorHandle);
        String canonicalUrl = canonicalUrl(body).orElse(proofUri.toString());
        return new PublicProofDocument(
                authorHandle,
                displayName,
                profileUrl,
                body,
                null,
                observedAt,
                canonicalUrl);
    }

    private void validateHost(String provider, URI proofUri) {
        String host = proofUri.getHost() == null ? "" : proofUri.getHost().toLowerCase(Locale.ROOT);
        boolean accepted = PROVIDER_HOSTS.getOrDefault(provider, Set.of()).stream().anyMatch(host::equals);
        if (!accepted) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof provider does not match proof URL");
        }
    }

    private String fetchBody(String provider, URI proofUri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(proofUri)
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "text/html,application/xhtml+xml,application/json")
                    .header("User-Agent", "Mozilla/5.0 MonopolyFunIdentityVerifier/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400 || response.body() == null || response.body().isBlank()) {
                log.warn("identity_public_proof_fetch_failed provider={} proofUrl={} status={}", provider, proofUri, response.statusCode());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Proof fetch failed");
            }
            return response.body();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("identity_public_proof_fetch_failed provider={} proofUrl={} failure={}", provider, proofUri, exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Proof fetch failed");
        }
    }

    private Optional<String> authorHandle(String provider, URI proofUri, String body) {
        return switch (provider) {
            case "x" -> firstPathSegment(proofUri).or(() -> firstMatch(body,
                    "\"screen_name\"\\s*:\\s*\"([^\"]+)\"",
                    "\"username\"\\s*:\\s*\"([^\"]+)\"",
                    "data-screen-name=\"([^\"]+)\""));
            case "reddit" -> firstMatch(body,
                    "\"author\"\\s*:\\s*\"([^\"]+)\"",
                    "\"name\"\\s*:\\s*\"u_([^\"]+)\"",
                    "data-author=\"([^\"]+)\"",
                    "u/([A-Za-z0-9_-]{3,32})")
                    .or(() -> userPathSegment(proofUri));
            case "youtube" -> firstMatch(body,
                    "\"ownerChannelName\"\\s*:\\s*\"([^\"]+)\"",
                    "\"author\"\\s*:\\s*\"([^\"]+)\"",
                    "\"channelName\"\\s*:\\s*\"([^\"]+)\"",
                    "youtube.com/@([A-Za-z0-9_.-]+)")
                    .or(() -> handlePathSegment(proofUri));
            default -> Optional.empty();
        };
    }

    private Optional<String> displayName(String provider, String body) {
        return switch (provider) {
            case "x", "reddit", "youtube" -> firstMatch(body,
                    "<meta\\s+property=\"og:title\"\\s+content=\"([^\"]+)\"",
                    "<title>([^<]+)</title>");
            default -> Optional.empty();
        };
    }

    private String profileUrl(String provider, String handle) {
        return switch (provider) {
            case "x" -> "https://x.com/" + handle;
            case "reddit" -> "https://www.reddit.com/user/" + handle;
            case "youtube" -> "https://www.youtube.com/@" + handle;
            default -> "";
        };
    }

    private Optional<String> canonicalUrl(String body) {
        return firstMatch(body,
                "<link\\s+rel=\"canonical\"\\s+href=\"([^\"]+)\"",
                "<meta\\s+property=\"og:url\"\\s+content=\"([^\"]+)\"");
    }

    private Optional<String> firstPathSegment(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (!segment.isBlank()) {
                return Optional.of(segment.replaceFirst("^@+", ""));
            }
        }
        return Optional.empty();
    }

    private Optional<String> userPathSegment(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        java.util.regex.Matcher matcher = Pattern.compile("/user/([^/]+)/?", Pattern.CASE_INSENSITIVE).matcher(path);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private Optional<String> handlePathSegment(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        java.util.regex.Matcher matcher = Pattern.compile("/@([^/]+)/?").matcher(path);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private Optional<String> firstMatch(String value, String... patterns) {
        for (String pattern : patterns) {
            java.util.regex.Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(value);
            if (matcher.find()) {
                return Optional.of(unescape(matcher.group(1)).trim());
            }
        }
        return Optional.empty();
    }

    private String unescape(String value) {
        // 中文注释：公开页常把账号和正文塞进 meta/JSON，基础反转义可让 token 与 handle 校验稳定命中。
        return value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("\\u0026", "&")
                .replace("\\/", "/");
    }
}
