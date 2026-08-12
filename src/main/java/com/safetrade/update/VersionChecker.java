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

public class VersionChecker {

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

        String version = extractJsonField(response, "tag_name");
        String url = extractJsonField(response, "html_url");
        if (version.isBlank()) {
            version = extractJsonField(response, "name");
        }
        return version.isBlank() ? null : new ReleaseInfo(version, url);
    }

    private ReleaseInfo fetchLatestTag(String repo) throws IOException, InterruptedException {
        String response = send("https://api.github.com/repos/" + repo + "/tags");
        if (response == null || response.isBlank() || !response.startsWith("[")) {
            return null;
        }

        // Pobieramy pierwszy tag z tablicy JSON
        String version = extractJsonField(response, "name");
        if (version.isBlank()) {
            return null;
        }
        return new ReleaseInfo(version, "https://github.com/" + repo + "/tags");
    }

    private String send(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SafeTrade-Plugin-VersionChecker")
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            plugin.getLogger().warning("GitHub API returned status code " + response.statusCode() + " for URL: " + url);
            return null;
        }
        return response.body();
    }

    // Bezpieczniejsza metoda wyciągania pól z JSON bez zewnętrznych bibliotek
    String extractJsonField(String json, String field) {
        int index = json.indexOf("\"" + field + "\"");
        if (index == -1)
            return "";

        int colonIndex = json.indexOf(':', index);
        if (colonIndex == -1)
            return "";

        int firstQuote = json.indexOf('"', colonIndex);
        if (firstQuote == -1)
            return "";

        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote == -1)
            return "";

        return json.substring(firstQuote + 1, secondQuote);
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