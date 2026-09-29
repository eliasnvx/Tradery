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
