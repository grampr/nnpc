package net.nehaverse.nehanpcs.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class SkinService {
    private final Plugin plugin;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public SkinService(Plugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Optional<SkinData>> fetchByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest uuidRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> uuidResponse = client.send(uuidRequest, HttpResponse.BodyHandlers.ofString());
                if (uuidResponse.statusCode() != 200) {
                    return Optional.empty();
                }
                JsonObject uuidJson = JsonParser.parseString(uuidResponse.body()).getAsJsonObject();
                String uuid = uuidJson.get("id").getAsString();

                HttpRequest skinRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false"))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> skinResponse = client.send(skinRequest, HttpResponse.BodyHandlers.ofString());
                if (skinResponse.statusCode() != 200) {
                    return Optional.empty();
                }
                JsonObject skinJson = JsonParser.parseString(skinResponse.body()).getAsJsonObject();
                JsonArray properties = skinJson.getAsJsonArray("properties");
                for (int i = 0; i < properties.size(); i++) {
                    JsonObject property = properties.get(i).getAsJsonObject();
                    if ("textures".equals(property.get("name").getAsString())) {
                        return Optional.of(new SkinData(
                                username,
                                property.get("value").getAsString(),
                                property.get("signature").getAsString()
                        ));
                    }
                }
            } catch (IOException | InterruptedException | RuntimeException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                plugin.getLogger().warning("Failed to fetch skin for " + username + ": " + ex.getMessage());
            }
            return Optional.empty();
        });
    }

    public record SkinData(String source, String value, String signature) {
    }
}
