package com.studymode.persistence.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import com.studymode.domain.Validation;

public final class StartupConfigurationLoader {
    private static final Set<String> KEYS = Set.of("start-page", "blocker", "window-width", "window-height", "demo");
    private static final Set<String> PAGES = Set.of("dashboard", "focus", "history", "classrooms", "workspaces", "blocked", "goals", "statistics", "achievements", "settings");

    public StartupConfiguration load(List<String> arguments, Map<String, String> environment) throws IOException {
        Map<String, String> flags = new HashMap<>();
        for (String argument : arguments) {
            int equals = argument.indexOf('=');
            if (!argument.startsWith("--") || equals < 3 || equals == argument.length() - 1) throw new IllegalArgumentException("Use --option=value for startup arguments.");
            String key = argument.substring(2, equals);
            if (!KEYS.contains(key) && !Set.of("data-dir", "config").contains(key)) throw new IllegalArgumentException("Unknown startup option: " + key);
            if (flags.put(key, argument.substring(equals + 1)) != null) throw new IllegalArgumentException("Duplicate startup option: " + key);
        }
        Path root;
        if (flags.containsKey("data-dir")) root = absolutePath(flags.get("data-dir"));
        else {
            String local = environment.get("LOCALAPPDATA");
            if (local == null || local.isBlank()) throw new IllegalArgumentException("LOCALAPPDATA is unavailable. Supply --data-dir=<absolute-path>.");
            root = absolutePath(local).resolve("StudyMode");
        }
        Properties properties = new Properties();
        try (var input = getClass().getResourceAsStream("/config/defaults.properties")) {
            if (input == null) throw new IOException("Default configuration is missing.");
            properties.load(input);
        }
        Path saved = root.resolve("settings.properties");
        if (Files.exists(saved)) read(saved, properties);
        if (flags.containsKey("config")) read(absolutePath(flags.get("config")), properties);
        flags.forEach((key, value) -> { if (KEYS.contains(key)) properties.setProperty(key, value); });
        return validate(root, properties);
    }

    public void saveWindowPreferences(Path root, int width, int height) throws IOException {
        Properties saved = new Properties();
        Path destination = root.resolve("settings.properties");
        if (Files.exists(destination)) read(destination, saved);
        saved.setProperty("window-width", Integer.toString(width)); saved.setProperty("window-height", Integer.toString(height));
        validate(root, withDefaults(saved));
        Files.createDirectories(root);
        Path temporary = Files.createTempFile(root, "settings-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { saved.store(writer, "Study Mode user preferences"); }
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private Properties withDefaults(Properties saved) throws IOException {
        Properties combined = new Properties();
        try (var input = getClass().getResourceAsStream("/config/defaults.properties")) {
            if (input == null) throw new IOException("Default configuration is missing.");
            combined.load(input);
        }
        combined.putAll(saved); return combined;
    }
    private void read(Path file, Properties target) throws IOException {
        Properties source = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { source.load(reader); }
        for (String key : source.stringPropertyNames()) {
            if (!KEYS.contains(key)) throw new IllegalArgumentException("Unknown setting '" + key + "' in " + file.getFileName());
            target.setProperty(key, source.getProperty(key).strip());
        }
    }
    private StartupConfiguration validate(Path root, Properties properties) {
        String page = properties.getProperty("start-page");
        if (!PAGES.contains(page)) throw new IllegalArgumentException("Unknown start-page: " + page);
        if (!Set.of("simulated","windows-dry-run","windows").contains(properties.getProperty("blocker"))) throw new IllegalArgumentException("Use blocker=simulated, windows-dry-run or windows.");
        try {
            int width = Validation.range(Integer.parseInt(properties.getProperty("window-width")), 900, 3840, "Window width");
            int height = Validation.range(Integer.parseInt(properties.getProperty("window-height")), 640, 2160, "Window height");
            String demo = properties.getProperty("demo");
            if (!Set.of("true", "false").contains(demo)) throw new IllegalArgumentException("demo must be exactly true or false.");
            return new StartupConfiguration(root, page, width, height, Boolean.parseBoolean(demo),properties.getProperty("blocker"));
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Window width and height must be whole numbers.", e); }
    }
    private Path absolutePath(String value) {
        try {
            Path path = Path.of(value).normalize();
            if (!path.isAbsolute()) throw new IllegalArgumentException("Configuration and data paths must be absolute.");
            return path;
        } catch (InvalidPathException e) { throw new IllegalArgumentException("The supplied path is invalid.", e); }
    }
}
