package dev.eliasnvx.tradery.config;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.eliasnvx.tradery.Tradery;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * One JSON5 config file backed by a codec. A missing file is written with defaults and comments; an existing
 * file is never rewritten unless {@link #save} is called (the client config, which the game changes itself).
 * Broken values fall back to their defaults and are reported in the log.
 */
public final class ConfigFile<T> {
    private final Path path;
    private final Codec<T> codec;
    private final T defaults;
    private final Map<String, String> comments;
    private final String header;

    public ConfigFile(Path path, Codec<T> codec, T defaults, Map<String, String> comments, String header) {
        this.path = path;
        this.codec = codec;
        this.defaults = defaults;
        this.comments = comments;
        this.header = header;
    }

    public Path path() {
        return path;
    }

    public T defaults() {
        return defaults;
    }

    /** Reads the file, writing it with defaults first if it doesn't exist. Never throws. */
    public T load() {
        if (!Files.exists(path)) {
            save(defaults);
            return defaults;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Result<T> result = decode(text);
            for (String problem : result.problems()) {
                Tradery.LOGGER.warn("{}: {}", path.getFileName(), problem);
            }
            return result.value();
        } catch (IOException e) {
            Tradery.LOGGER.error("Can't read {}, using defaults", path, e);
            return defaults;
        } catch (Json5.ParseException e) {
            Tradery.LOGGER.error("{} is not valid JSON5 ({}), using defaults. Fix the file and run /tradery reload.",
                path.getFileName(), e.getMessage());
            return defaults;
        }
    }

    /** Decoded value and what was wrong with the text. */
    public record Result<T>(T value, List<String> problems) {
    }

    /** Decodes JSON5 text; invalid values become defaults and are listed in {@link Result#problems()}. */
    public Result<T> decode(String text) throws Json5.ParseException {
        JsonElement json = Json5.parse(text);
        T value = codec.parse(JsonOps.INSTANCE, json).resultOrPartial().orElse(defaults);
        JsonElement normalized = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        return new Result<>(value, ConfigDiff.problems(json, normalized));
    }

    /** Encodes a value as commented JSON5. */
    public String encode(T value) {
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        return Json5.write(json, comments, header);
    }

    /** Writes the file atomically (temp file + move). Logs and swallows I/O errors. */
    public void save(T value) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, encode(value), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Tradery.LOGGER.error("Can't write {}", path, e);
        }
    }
}
