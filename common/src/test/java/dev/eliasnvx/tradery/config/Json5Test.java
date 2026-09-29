package dev.eliasnvx.tradery.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Json5Test {
    @Test
    void readsJson5Syntax() throws Exception {
        JsonObject root = Json5.parse("""
            // line comment
            {
              unquoted: 1,
              'single': 'it\\'s',
              "double": "a\\nb",
              /* block
                 comment */
              hex: 0xFF,
              lead: .5,
              trail: 5.,
              plus: +3,
              neg: -0.25,
              big: 12345678901234567890.12,
              inf: -Infinity,
              list: [1, 2, 3,],
              nested: { a: true, b: null, },
              "minecraft:zombie": { min: 1, max: 3 },
            }
            """).getAsJsonObject();
        assertEquals(1, root.get("unquoted").getAsInt());
        assertEquals("it's", root.get("single").getAsString());
        assertEquals("a\nb", root.get("double").getAsString());
        assertEquals(255, root.get("hex").getAsInt());
        assertEquals(new BigDecimal("0.5"), root.get("lead").getAsBigDecimal());
        assertEquals(new BigDecimal("5.0"), root.get("trail").getAsBigDecimal());
        assertEquals(3, root.get("plus").getAsInt());
        assertEquals(new BigDecimal("-0.25"), root.get("neg").getAsBigDecimal());
        assertEquals(new BigDecimal("12345678901234567890.12"), root.get("big").getAsBigDecimal());
        assertEquals(Double.NEGATIVE_INFINITY, root.get("inf").getAsDouble());
        assertEquals(3, root.getAsJsonArray("list").size());
        assertTrue(root.getAsJsonObject("nested").get("b").isJsonNull());
        assertEquals(3, root.getAsJsonObject("minecraft:zombie").get("max").getAsInt());
    }

    @Test
    void reportsPosition() {
        Json5.ParseException e = assertThrows(Json5.ParseException.class, () -> Json5.parse("{\n  a: 1\n  b: 2\n}"));
        assertTrue(e.getMessage().contains("line 3"), e.getMessage());
        assertThrows(Json5.ParseException.class, () -> Json5.parse("{a: 1, a: 2}"));
        assertThrows(Json5.ParseException.class, () -> Json5.parse("{a: 'unterminated}"));
        assertThrows(Json5.ParseException.class, () -> Json5.parse("{a: 1} extra"));
        assertThrows(Json5.ParseException.class, () -> Json5.parse("/* never closed"));
        assertThrows(Json5.ParseException.class, () -> Json5.parse(""));
    }

    @Test
    void writesAndReadsBack() throws Exception {
        JsonObject root = new JsonObject();
        JsonObject currency = new JsonObject();
        currency.addProperty("symbol", "₮");
        currency.addProperty("decimals", 2);
        root.add("currency", currency);
        root.addProperty("fee", new BigDecimal("2.50"));
        JsonArray list = new JsonArray();
        list.add("minecraft:bedrock");
        root.add("blacklist", list);
        root.addProperty("quote\"key", "x\\y");

        String text = Json5.write(root, Map.of("currency", "Currency shown\non screen", "fee", "Percent"), "Tradery config");
        assertTrue(text.startsWith("// Tradery config\n"), text);
        assertTrue(text.contains("  // Currency shown\n  // on screen\n  currency: {"), text);
        assertTrue(text.contains("fee: 2.5"), text);
        assertTrue(text.contains("blacklist: [\"minecraft:bedrock\"]"), text);

        JsonElement back = Json5.parse(text);
        assertEquals("₮", back.getAsJsonObject().getAsJsonObject("currency").get("symbol").getAsString());
        assertEquals(new BigDecimal("2.5"), back.getAsJsonObject().get("fee").getAsBigDecimal());
        assertEquals("x\\y", back.getAsJsonObject().get("quote\"key").getAsString());
    }
}
