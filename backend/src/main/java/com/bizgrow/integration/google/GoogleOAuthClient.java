package com.YemomBizGrow.integration.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GoogleOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String GA4_BASE_URL = "https://analyticsdata.googleapis.com/v1beta/properties/";
    private static final String SEARCH_CONSOLE_BASE_URL = "https://www.googleapis.com/webmasters/v3/sites/";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GoogleOAuthClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String scope
    ) {}

    public record UserInfoResponse(
            String id,
            String email,
            String name
    ) {}

    public record Ga4DailyMetric(
            LocalDate date,
            long sessions,
            long users,
            long pageViews,
            double bounceRate
    ) {}

    public record GscDailyMetric(
            LocalDate date,
            long clicks,
            long impressions,
            double ctr,
            double position
    ) {}

    public record Ga4PropertyItem(String propertyId, String displayName) {}
    public record GscSiteItem(String siteUrl, String permissionLevel) {}

    public TokenResponse exchangeAuthorizationCode(
            String code,
            String clientId,
            String clientSecret,
            String redirectUri
    ) throws IOException, InterruptedException {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth credentials are not configured");
        }

        Map<String, String> params = new HashMap<>();
        params.put("code", code);
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        params.put("redirect_uri", redirectUri);
        params.put("grant_type", "authorization_code");

        String formBody = buildFormData(params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            log.error("Google token exchange failed: status={} body={}", response.statusCode(), response.body());
            throw new IOException("Failed to exchange Google OAuth code: HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String accessToken = root.path("access_token").asText();
        String refreshToken = root.hasNonNull("refresh_token") ? root.path("refresh_token").asText() : null;
        long expiresIn = root.path("expires_in").asLong(3600);
        String scope = root.path("scope").asText("");

        return new TokenResponse(accessToken, refreshToken, expiresIn, scope);
    }

    public TokenResponse refreshAccessToken(
            String refreshToken,
            String clientId,
            String clientSecret
    ) throws IOException, InterruptedException {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth credentials are not configured");
        }

        Map<String, String> params = new HashMap<>();
        params.put("refresh_token", refreshToken);
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        params.put("grant_type", "refresh_token");

        String formBody = buildFormData(params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            log.error("Google token refresh failed: status={} body={}", response.statusCode(), response.body());
            throw new IOException("Failed to refresh Google OAuth token: HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String newAccessToken = root.path("access_token").asText();
        long expiresIn = root.path("expires_in").asLong(3600);
        String scope = root.path("scope").asText("");

        return new TokenResponse(newAccessToken, refreshToken, expiresIn, scope);
    }

    public UserInfoResponse fetchUserInfo(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(USERINFO_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            log.error("Google userinfo fetch failed: status={} body={}", response.statusCode(), response.body());
            throw new IOException("Failed to fetch Google user info: HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String id = root.path("id").asText();
        String email = root.path("email").asText();
        String name = root.path("name").asText(email);

        return new UserInfoResponse(id, email, name);
    }

    public List<Ga4DailyMetric> fetchGa4DailyMetrics(
            String accessToken,
            String propertyId,
            LocalDate startDate,
            LocalDate endDate
    ) throws IOException, InterruptedException {
        if (propertyId == null || propertyId.isBlank()) {
            return List.of();
        }

        String url = GA4_BASE_URL + propertyId + ":runReport";

        Map<String, Object> body = Map.of(
                "dateRanges", List.of(Map.of(
                        "startDate", startDate.toString(),
                        "endDate", endDate.toString()
                )),
                "dimensions", List.of(Map.of("name", "date")),
                "metrics", List.of(
                        Map.of("name", "sessions"),
                        Map.of("name", "totalUsers"),
                        Map.of("name", "screenPageViews"),
                        Map.of("name", "bounceRate")
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            log.warn("GA4 report query failed: status={} body={}", response.statusCode(), response.body());
            return List.of();
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode rows = root.path("rows");
        List<Ga4DailyMetric> metrics = new ArrayList<>();

        if (rows.isArray()) {
            for (JsonNode row : rows) {
                String dateStr = row.path("dimensionValues").get(0).path("value").asText();
                // GA4 returns YYYYMMDD
                LocalDate metricDate = parseGa4Date(dateStr);
                JsonNode metricVals = row.path("metricValues");
                long sessions = parseLongSafe(metricVals.get(0).path("value").asText());
                long users = parseLongSafe(metricVals.get(1).path("value").asText());
                long pageViews = parseLongSafe(metricVals.get(2).path("value").asText());
                double bounceRate = parseDoubleSafe(metricVals.get(3).path("value").asText()) * 100.0;

                metrics.add(new Ga4DailyMetric(metricDate, sessions, users, pageViews, bounceRate));
            }
        }

        return metrics;
    }

    public List<GscDailyMetric> fetchGscDailyMetrics(
            String accessToken,
            String siteUrl,
            LocalDate startDate,
            LocalDate endDate
    ) throws IOException, InterruptedException {
        if (siteUrl == null || siteUrl.isBlank()) {
            return List.of();
        }

        String encodedSite = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8);
        String url = SEARCH_CONSOLE_BASE_URL + encodedSite + "/searchAnalytics/query";

        Map<String, Object> body = Map.of(
                "startDate", startDate.toString(),
                "endDate", endDate.toString(),
                "dimensions", List.of("date"),
                "rowLimit", 1000
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            log.warn("Google Search Console query failed: status={} body={}", response.statusCode(), response.body());
            return List.of();
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode rows = root.path("rows");
        List<GscDailyMetric> metrics = new ArrayList<>();

        if (rows.isArray()) {
            for (JsonNode row : rows) {
                String dateStr = row.path("keys").get(0).asText();
                LocalDate metricDate = LocalDate.parse(dateStr);
                long clicks = row.path("clicks").asLong(0);
                long impressions = row.path("impressions").asLong(0);
                double ctr = row.path("ctr").asDouble(0.0);
                double position = row.path("position").asDouble(0.0);

                metrics.add(new GscDailyMetric(metricDate, clicks, impressions, ctr, position));
            }
        }

        return metrics;
    }

    private LocalDate parseGa4Date(String yyyymmdd) {
        try {
            if (yyyymmdd.length() == 8) {
                int y = Integer.parseInt(yyyymmdd.substring(0, 4));
                int m = Integer.parseInt(yyyymmdd.substring(4, 6));
                int d = Integer.parseInt(yyyymmdd.substring(6, 8));
                return LocalDate.of(y, m, d);
            }
            return LocalDate.parse(yyyymmdd);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private long parseLongSafe(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0L;
        }
    }

    private double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String buildFormData(Map<String, String> data) {
        return data.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public List<Ga4PropertyItem> listGa4Properties(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return List.of();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://analyticsadmin.googleapis.com/v1beta/accountSummaries"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("Failed to list GA4 properties: status={}, body={}", response.statusCode(), response.body());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            List<Ga4PropertyItem> list = new ArrayList<>();
            JsonNode summaries = root.path("accountSummaries");
            if (summaries.isArray()) {
                for (JsonNode acc : summaries) {
                    JsonNode propertySummaries = acc.path("propertySummaries");
                    if (propertySummaries.isArray()) {
                        for (JsonNode prop : propertySummaries) {
                            String propId = prop.path("property").asText("");
                            String displayName = prop.path("displayName").asText(propId);
                            if (!propId.isBlank()) {
                                list.add(new Ga4PropertyItem(propId, displayName));
                            }
                        }
                    }
                }
            }
            return list;
        } catch (Exception e) {
            log.warn("Error listing GA4 properties from Google API: {}", e.getMessage());
            return List.of();
        }
    }

    public List<GscSiteItem> listSearchConsoleSites(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return List.of();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/webmasters/v3/sites"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("Failed to list Search Console sites: status={}, body={}", response.statusCode(), response.body());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            List<GscSiteItem> list = new ArrayList<>();
            JsonNode siteEntries = root.path("siteEntry");
            if (siteEntries.isArray()) {
                for (JsonNode entry : siteEntries) {
                    String siteUrl = entry.path("siteUrl").asText("");
                    String perm = entry.path("permissionLevel").asText("siteOwner");
                    if (!siteUrl.isBlank()) {
                        list.add(new GscSiteItem(siteUrl, perm));
                    }
                }
            }
            return list;
        } catch (Exception e) {
            log.warn("Error listing Search Console sites from Google API: {}", e.getMessage());
            return List.of();
        }
    }
}
