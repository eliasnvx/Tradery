package dev.eliasnvx.tradery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.eliasnvx.tradery.api.Reasons;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every language has exactly the English keys and placeholders, and every Tradery reason needs a name. */
class LangFilesTest {
    /** Every shipped language; a new lang file must be listed here (and in README / the mod page). */
    static final List<String> LANGUAGES = List.of("en_us", "ru_ru", "uk_ua", "be_by", "pl_pl", "de_de", "nl_nl", "sv_se", "fr_fr",
        "es_es", "pt_br", "ja_jp", "zh_cn", "zh_tw", "zh_hk");
    private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("%(\\d+\\$)?[sd%]");

    private static JsonObject lang(String code) throws IOException {
        try (InputStream in = LangFilesTest.class.getResourceAsStream("/assets/tradery/lang/" + code + ".json")) {
            assertNotNull(in, code + ".json missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<String> placeholders(String text) {
        List<String> found = new java.util.ArrayList<>();
        java.util.regex.Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        java.util.Collections.sort(found);
        return found;
    }

    @Test
    void everyLanguageHasTheEnglishKeysAndPlaceholders() throws IOException {
        JsonObject en = lang("en_us");
        for (String code : LANGUAGES) {
            JsonObject other = lang(code);
            Set<String> missing = new TreeSet<>(en.keySet());
            missing.removeAll(other.keySet());
            Set<String> extra = new TreeSet<>(other.keySet());
            extra.removeAll(en.keySet());
            assertEquals(Set.of(), missing, "missing in " + code);
            assertEquals(Set.of(), extra, "not in en_us but in " + code);
            for (String key : en.keySet()) {
                String text = other.get(key).getAsString();
                assertTrue(!text.isBlank(), code + " " + key + " is empty");
                assertEquals(placeholders(en.get(key).getAsString()), placeholders(text), code + " " + key + " placeholders");
            }
        }
    }

    /** Lang files on disk and {@link #LANGUAGES} agree, so a new language is never half-added. */
    @Test
    void everyLangFileIsListed() throws IOException {
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("tradery.sources")).getParent()
            .resolve("resources/assets/tradery/lang");
        Set<String> onDisk = new TreeSet<>();
        try (var files = java.nio.file.Files.list(dir)) {
            files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".json")).forEach(n -> onDisk.add(n.substring(0, n.length() - 5)));
        }
        assertEquals(new TreeSet<>(LANGUAGES), onDisk);
    }

    /**
     * Every literal translation key in the sources ({@code "tradery.…"}, {@code "block.tradery.…"}, …) exists in
     * en_us, so players never see raw keys. Keys built at runtime are listed explicitly.
     */
    @Test
    void everyKeyInTheSourcesIsTranslated() throws IOException {
        JsonObject en = lang("en_us");
        java.nio.file.Path root = java.nio.file.Path.of(System.getProperty("tradery.sources"));
        java.util.regex.Pattern literal = java.util.regex.Pattern.compile("\"((?:tradery|block\\.tradery|item\\.tradery|key\\.tradery|itemGroup\\.tradery)\\.[a-z0-9_.]+[a-z0-9_])\"");
        Set<String> missing = new TreeSet<>();
        try (var files = java.nio.file.Files.walk(root)) {
            for (java.nio.file.Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                java.util.regex.Matcher m = literal.matcher(java.nio.file.Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (!en.has(key) && !key.endsWith(".") && !key.endsWith("_") && !key.equals("tradery.spawner") && !key.startsWith("tradery.reason.")
                        && !key.equals("tradery.apiVersion") && !key.equals("tradery.sources")) {
                        missing.add(key + " (" + root.relativize(file) + ")");
                    }
                }
            }
        }
        for (String dynamic : List.of("tradery.command.eco_give", "tradery.command.eco_take", "tradery.command.eco_set",
            "tradery.animation.static", "tradery.animation.spin", "tradery.animation.bob", "tradery.animation.spin_bob", "tradery.animation.none",
            "tradery.screen.admin.infinite", "tradery.screen.admin.burn", "tradery.screen.admin.no_fee", "tradery.screen.admin.server",
            "tradery.screen.admin.infinite_tip", "tradery.screen.admin.burn_tip", "tradery.screen.admin.no_fee_tip", "tradery.screen.admin.server_tip",
            "tradery.screen.buy_one", "tradery.screen.buy_eight", "tradery.screen.buy_max",
            "tradery.screen.sell_one", "tradery.screen.sell_eight", "tradery.screen.sell_max")) {
            if (!en.has(dynamic)) {
                missing.add(dynamic + " (built at runtime)");
            }
        }
        assertEquals(Set.of(), missing, "keys used in code but missing in en_us.json");
    }

    @Test
    void everyReasonHasAName() throws Exception {
        JsonObject en = lang("en_us");
        for (Field field : Reasons.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                net.minecraft.resources.ResourceLocation id = (net.minecraft.resources.ResourceLocation) field.get(null);
                String key = "tradery.reason." + id.getNamespace() + "." + id.getPath().replace('/', '.');
                assertTrue(en.has(key), "no name for reason " + id);
            }
        }
    }
}
