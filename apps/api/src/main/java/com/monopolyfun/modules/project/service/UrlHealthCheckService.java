package com.monopolyfun.modules.project.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class UrlHealthCheckService {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public Map<String, Object> check(String url) {
        URI uri = validatePublicHttpUrl(url);
        Instant startedAt = Instant.now();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("url", uri.toString());
        result.put("checkedAt", startedAt.toString());
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            result.put("reachable", response.statusCode() >= 200 && response.statusCode() < 400);
            result.put("statusCode", response.statusCode());
            result.put("finalUrl", response.uri().toString());
            result.put("latencyMs", latencyMs);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            result.put("reachable", false);
            result.put("error", exception.getClass().getSimpleName());
        }
        return Map.copyOf(result);
    }

    private URI validatePublicHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deployment URL must be http or https");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deployment URL host is required");
            }
            InetAddress address = InetAddress.getByName(host);
            // 中文注释：部署 proof 会触发服务端访问，限制内网地址可避免把平台变成 SSRF 跳板。
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deployment URL must be publicly reachable");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deployment URL is invalid", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deployment URL host cannot be resolved", exception);
        }
    }
}
