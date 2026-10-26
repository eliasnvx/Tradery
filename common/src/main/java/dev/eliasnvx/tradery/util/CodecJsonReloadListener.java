package dev.eliasnvx.tradery.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.eliasnvx.tradery.Tradery;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Data pack JSON files decoded with a {@link Codec}, one per id: what 26.x's generic
 * {@code SimpleJsonResourceReloadListener<T>} does, for 1.21.1. A file that can't be read is logged and skipped.
 */
public abstract class CodecJsonReloadListener<T> extends SimplePreparableReloadListener<Map<ResourceLocation, T>> {
    private final Codec<T> codec;
    private final FileToIdConverter converter;

    protected CodecJsonReloadListener(Codec<T> codec, FileToIdConverter converter) {
        this.codec = codec;
        this.converter = converter;
    }

    @Override
    protected Map<ResourceLocation, T> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, T> files = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : converter.listMatchingResources(manager).entrySet()) {
            ResourceLocation id = converter.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                codec.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> Tradery.LOGGER.error("Couldn't parse data file {} from {}: {}", id, entry.getKey(), error))
                    .ifPresent(value -> files.put(id, value));
            } catch (Exception e) {
                Tradery.LOGGER.error("Couldn't read data file {} from {}", id, entry.getKey(), e);
            }
        }
        return files;
    }
}
