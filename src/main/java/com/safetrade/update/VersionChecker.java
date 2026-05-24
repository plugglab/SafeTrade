package com.safetrade.update;

import com.safetrade.SafeTradePlugin;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionChecker {

    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HTML_URL_PATTERN = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");

    private final SafeTradePlugin plugin;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private volatile boolean updateAvailable;
    private volatile String latestVersion = "";
    private volatile String latestUrl = "";

    public VersionChecker(SafeTradePlugin plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdates() {
        if (!plugin.getConfig().getBoolean("settings.version-check.enabled", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String repo = plugin.getConfig().getString("settings.version-check.github-repo", "plugglab/SafeTrade");
            if (repo == null || repo.isBlank() || !repo.contains("/")) {
                plugin.getLogger().warning("SafeTrade version check skipped: invalid GitHub repo setting.");
                return;
            }

            try {
                ReleaseInfo releaseInfo = fetchLatestRelease(repo);
                if (releaseInfo == null) {
                    releaseInfo = fetchLatestTag(repo);
                }
                if (releaseInfo == null || releaseInfo.version().isBlank()) {
                    plugin.getLogger().warning("SafeTrade version check failed: no release or tag information found.");
                    return;
                }

                latestVersion = normalizeVersion(releaseInfo.version());
                latestUrl = plugin.getConfig().getString("settings.version-check.download-url", "").trim();
                if (latestUrl.isBlank()) {
                    latestUrl = releaseInfo.url().isBlank()
                            ? "https://github.com/" + repo
                            : releaseInfo.url();
                }
                updateAvailable = compareVersions(latestVersion, plugin.getDescription().getVersion()) > 0;

                if (updateAvailable) {
                    plugin.getLogger().info("SafeTrade update available: " + latestVersion + " - " + latestUrl);
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("SafeTrade version check failed: " + exception.getMessage());
            }
        });
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getLatestUrl() {
        return latestUrl;
    }

    private ReleaseInfo fetchLatestRelease(String repo) throws IOException, InterruptedException {
        String response = send("https://api.github.com/repos/" + repo + "/releases/latest");
        if (response == null || response.contains("\"Not Found\"")) {
            return null;
        }

        String version = findFirst(response, TAG_NAME_PATTERN);
        String url = findFirst(response, HTML_URL_PATTERN);
        if (version.isBlank()) {
            version = findFirst(response, NAME_PATTERN);
        }
        return version.isBlank() ? null : new ReleaseInfo(version, url);
    }

    private ReleaseInfo fetchLatestTag(String repo) throws IOException, InterruptedException {
        String response = send("https://api.github.com/repos/" + repo + "/tags");
        if (response == null || response.isBlank()) {
            return null;
        }

        String version = findFirst(response, NAME_PATTERN);
        if (version.isBlank()) {
            return null;
        }
        return new ReleaseInfo(version, "https://github.com/" + repo + "/tags");
    }

    private String send(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SafeTrade-VersionChecker")
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            return null;
        }
        return response.body();
    }

    private String findFirst(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private int compareVersions(String left, String right) {
        List<String> leftParts = List.of(normalizeVersion(left).split("[.-]"));
        List<String> rightParts = List.of(normalizeVersion(right).split("[.-]"));
        int max = Math.max(leftParts.size(), rightParts.size());

        for (int i = 0; i < max; i++) {
            String leftPart = i < leftParts.size() ? leftParts.get(i) : "0";
            String rightPart = i < rightParts.size() ? rightParts.get(i) : "0";

            int comparison;
            if (isNumber(leftPart) && isNumber(rightPart)) {
                comparison = Integer.compare(Integer.parseInt(leftPart), Integer.parseInt(rightPart));
            } else {
                comparison = leftPart.compareToIgnoreCase(rightPart);
            }

            if (comparison != 0) {
                return comparison;
            }
        }

        return 0;
    }

    private boolean isNumber(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private record ReleaseInfo(String version, String url) {
    }
}
