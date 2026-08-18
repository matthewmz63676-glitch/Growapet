package me.growapet.commerce;

import me.growapet.GrowAPet;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Provider boundary; no player command is trusted until the provider API confirms it. */
public final class CommerceVerifier {
    private static final Pattern STATUS_ID = Pattern.compile("\\\"status\\\"\\s*:\\s*\\{[^}]*\\\"id\\\"\\s*:\\s*\\\"?(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STATUS_DESCRIPTION = Pattern.compile("\\\"status\\\"\\s*:\\s*\\{[^}]*?\\\"description\\\"\\s*:\\s*\\\"([^\\\"]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern QUANTITY = Pattern.compile("\\\"quantity\\\"\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PACKAGE_ID = Pattern.compile("\\\"packages?\\\"\\s*:\\s*(?:\\[\\s*)?\\{[^}]*?\\\"id\\\"\\s*:\\s*\\\"?([A-Za-z0-9._:-]+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PLAYER_UUID = Pattern.compile("\\\"(?:uuid|minecraft_uuid)\\\"\\s*:\\s*\\\"?([0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12})", Pattern.CASE_INSENSITIVE);
    private final GrowAPet plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();

    public CommerceVerifier(GrowAPet plugin) { this.plugin = plugin; }

    public CompletableFuture<Payment> verify(String transactionId, UUID playerId, String packageId, int quantity) {
        if (transactionId == null || !transactionId.matches("[A-Za-z0-9._:-]{1,128}") || playerId == null || packageId == null || !packageId.matches("[A-Za-z0-9._:-]{1,128}") || quantity < 1)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid commerce verification request"));
        String secret = System.getenv(plugin.getConfigManager().commerce().getString("secret-env", "GROWAPET_TEBEX_SECRET"));
        if (secret == null || secret.isBlank()) return CompletableFuture.failedFuture(new IllegalStateException("Tebex secret is not configured"));
        String base = apiBase();
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/payments/" + URLEncoder.encode(transactionId, StandardCharsets.UTF_8)))
                .timeout(java.time.Duration.ofSeconds(Math.max(3, plugin.getConfigManager().commerce().getLong("verification-timeout-seconds", 10))))
                .header("X-Tebex-Secret", secret).header("Accept", "application/json").GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenCompose(response -> {
            if (response.statusCode() / 100 != 2) return CompletableFuture.failedFuture(new IllegalStateException("Tebex returned HTTP " + response.statusCode()));
            Payment payment = parse(response.body(), transactionId, playerId, packageId, quantity);
            return payment.valid() ? CompletableFuture.completedFuture(payment) : CompletableFuture.failedFuture(new IllegalStateException(payment.failure()));
        });
    }

    /** Re-reads a payment for refund/chargeback reconciliation; callers still verify the result. */
    public CompletableFuture<ProviderStatus> verifyStatus(String transactionId) {
        if (transactionId == null || !transactionId.matches("[A-Za-z0-9._:-]{1,128}"))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid commerce transaction"));
        String secret = System.getenv(plugin.getConfigManager().commerce().getString("secret-env", "GROWAPET_TEBEX_SECRET"));
        if (secret == null || secret.isBlank()) return CompletableFuture.failedFuture(new IllegalStateException("Tebex secret is not configured"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase() + "/payments/" + URLEncoder.encode(transactionId, StandardCharsets.UTF_8)))
                .timeout(java.time.Duration.ofSeconds(Math.max(3, plugin.getConfigManager().commerce().getLong("verification-timeout-seconds", 10))))
                .header("X-Tebex-Secret", secret).header("Accept", "application/json").GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenCompose(response -> {
            if (response.statusCode() / 100 != 2) return CompletableFuture.failedFuture(new IllegalStateException("Tebex returned HTTP " + response.statusCode()));
            return CompletableFuture.completedFuture(parseStatus(response.body(), transactionId));
        });
    }

    static Payment parse(String json, String transactionId, UUID playerId, String packageId, int expectedQuantity) {
        if (json == null || json.isBlank()) return Payment.invalid("Empty provider response");
        String normalized = json.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String uuid = playerId.toString().replace("-", "").toLowerCase(Locale.ROOT);
        boolean statusComplete = normalized.contains("\"status\":{\"id\":1") || normalized.contains("\"description\":\"complete\"");
        String compactUuid = normalized.replace("-", "");
        Matcher playerMatcher = PLAYER_UUID.matcher(json);
        boolean playerMatch = playerMatcher.find() && playerMatcher.group(1).replace("-", "").equalsIgnoreCase(uuid);
        if (!playerMatch) playerMatch = compactUuid.contains(uuid);
        Matcher packageMatcher = PACKAGE_ID.matcher(json);
        boolean packageMatch = false;
        while (packageMatcher.find()) if (packageId.equalsIgnoreCase(packageMatcher.group(1))) { packageMatch = true; break; }
        if (!packageMatch) {
            String compactPackage = packageId.toLowerCase(Locale.ROOT).replace("-", "");
            packageMatch = compactUuid.contains("\"package_id\":" + compactPackage) || compactUuid.contains("\"package_id\":\"" + packageId.toLowerCase(Locale.ROOT) + "\"");
        }
        Matcher quantityMatcher = QUANTITY.matcher(json); int quantity = quantityMatcher.find() ? Integer.parseInt(quantityMatcher.group(1)) : expectedQuantity;
        if (!statusComplete) return Payment.invalid("Payment is not complete");
        if (!playerMatch) return Payment.invalid("Payment player does not match");
        if (!packageMatch) return Payment.invalid("Payment package does not match");
        if (quantity != expectedQuantity) return Payment.invalid("Payment quantity does not match");
        return new Payment(true, transactionId, playerId, packageId, quantity, "");
    }

    static ProviderStatus parseStatus(String json, String transactionId) {
        if (json == null || json.isBlank()) return new ProviderStatus(false, transactionId, "UNKNOWN", "Empty provider response");
        Matcher id = STATUS_ID.matcher(json); Matcher description = STATUS_DESCRIPTION.matcher(json);
        String status = description.find() ? description.group(1).trim().toUpperCase(Locale.ROOT) : id.find() && "1".equals(id.group(1)) ? "COMPLETE" : "UNKNOWN";
        boolean valid = status.contains("REFUND") || status.contains("CHARGEBACK") || status.contains("REVERSED");
        return new ProviderStatus(valid, transactionId, status, valid ? "" : "Provider has not confirmed a refund or chargeback");
    }

    private String apiBase() {
        String base = plugin.getConfigManager().commerce().getString("api-base-url", "https://plugin.tebex.io").replaceAll("/+$", "");
        URI uri = URI.create(base);
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalStateException("Tebex API URL must use HTTPS");
        return base;
    }

    public record Payment(boolean valid, String transactionId, UUID playerId, String packageId, int quantity, String failure) {
        static Payment invalid(String failure) { return new Payment(false, "", null, "", 0, failure); }
    }

    public record ProviderStatus(boolean valid, String transactionId, String status, String failure) { }
}
