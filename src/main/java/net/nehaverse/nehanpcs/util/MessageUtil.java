package net.nehaverse.nehanpcs.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MessageUtil {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> languages = new HashMap<>();
    private String configuredLanguage;
    private String fallbackLanguage;

    public MessageUtil(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        languages.clear();
        loadLanguage("ja", new File(plugin.getDataFolder(), "messages.yml"));

        File[] files = plugin.getDataFolder().listFiles((directory, name) ->
                name.startsWith("messages_") && name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String locale = file.getName().substring("messages_".length(), file.getName().length() - 4);
                loadLanguage(locale, file);
            }
        }

        configuredLanguage = normalize(plugin.getConfig().getString("settings.language", "auto"));
        fallbackLanguage = resolveLanguage(normalize(plugin.getConfig().getString("settings.fallback-language", "en")));
        if (fallbackLanguage == null) {
            fallbackLanguage = languages.containsKey("en") ? "en" : languages.keySet().stream().findFirst().orElse("ja");
        }
    }

    public String raw(String key) {
        return rawForLanguage(defaultLanguage(), key);
    }

    public String raw(CommandSender sender, String key) {
        return rawForLanguage(languageFor(sender), key);
    }

    public Component component(String text) {
        return LEGACY.deserialize(text);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = raw(sender, "prefix") + raw(sender, key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        sender.sendMessage(component(message));
    }

    private void loadLanguage(String locale, File file) {
        if (file.isFile()) {
            languages.put(normalize(locale), YamlConfiguration.loadConfiguration(file));
        }
    }

    private String languageFor(CommandSender sender) {
        if (!"auto".equals(configuredLanguage)) {
            String resolved = resolveLanguage(configuredLanguage);
            return resolved == null ? fallbackLanguage : resolved;
        }
        if (sender instanceof Player player) {
            String resolved = resolveLanguage(normalize(player.locale().toLanguageTag()));
            if (resolved != null) {
                return resolved;
            }
        }
        return fallbackLanguage;
    }

    private String defaultLanguage() {
        if (!"auto".equals(configuredLanguage)) {
            String resolved = resolveLanguage(configuredLanguage);
            if (resolved != null) {
                return resolved;
            }
        }
        return fallbackLanguage;
    }

    private String resolveLanguage(String locale) {
        if (languages.containsKey(locale)) {
            return locale;
        }
        int separator = locale.indexOf('_');
        String language = separator < 0 ? locale : locale.substring(0, separator);
        if (languages.containsKey(language)) {
            return language;
        }
        return languages.keySet().stream()
                .filter(candidate -> candidate.startsWith(language + "_"))
                .findFirst()
                .orElse(null);
    }

    private String rawForLanguage(String language, String key) {
        YamlConfiguration selected = languages.get(language);
        if (selected != null && selected.isString(key)) {
            return selected.getString(key, key);
        }
        YamlConfiguration fallback = languages.get(fallbackLanguage);
        if (fallback != null && fallback.isString(key)) {
            return fallback.getString(key, key);
        }
        return key;
    }

    private String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return "auto";
        }
        return locale.trim().replace('-', '_').toLowerCase(Locale.ROOT);
    }
}
