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

/** EN and RU must have exactly the same keys, and every Tradery reason needs a name. */
class LangFilesTest {
    private static JsonObject lang(String code) throws IOException {
        try (InputStream in = LangFilesTest.class.getResourceAsStream("/assets/tradery/lang/" + code + ".json")) {
            assertNotNull(in, code + ".json missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void englishAndRussianHaveTheSameKeys() throws IOException {
        Set<String> en = new TreeSet<>(lang("en_us").keySet());
        Set<String> ru = new TreeSet<>(lang("ru_ru").keySet());
        Set<String> missingInRu = new TreeSet<>(en);
        missingInRu.removeAll(ru);
        Set<String> missingInEn = new TreeSet<>(ru);
        missingInEn.removeAll(en);
        assertEquals(Set.of(), missingInRu, "missing in ru_ru");
        assertEquals(Set.of(), missingInEn, "missing in en_us");
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
                net.minecraft.resources.Identifier id = (net.minecraft.resources.Identifier) field.get(null);
                String key = "tradery.reason." + id.getNamespace() + "." + id.getPath().replace('/', '.');
                assertTrue(en.has(key), "no name for reason " + id);
            }
        }
    }
}
