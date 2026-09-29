package dev.eliasnvx.tradery.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileTest {
    private static ConfigFile<ServerConfig> server(Path dir) {
        return new ConfigFile<>(dir.resolve("server.json5"), ServerConfig.CODEC, ServerConfig.DEFAULT, ServerConfig.COMMENTS, ServerConfig.HEADER);
    }

    @Test
    void defaultFilesRoundTripWithoutProblems(@TempDir Path dir) throws Exception {
        ConfigFile<ServerConfig> file = server(dir);
        ConfigFile.Result<ServerConfig> server = file.decode(file.encode(ServerConfig.DEFAULT));
        assertEquals(ServerConfig.DEFAULT, server.value());
        assertTrue(server.problems().isEmpty(), server.problems().toString());

        ConfigFile<ClientConfig> clientFile = new ConfigFile<>(dir.resolve("client.json5"), ClientConfig.CODEC, ClientConfig.DEFAULT,
            ClientConfig.COMMENTS, ClientConfig.HEADER);
        ConfigFile.Result<ClientConfig> client = clientFile.decode(clientFile.encode(ClientConfig.DEFAULT));
        assertEquals(ClientConfig.DEFAULT, client.value());
        assertTrue(client.problems().isEmpty(), client.problems().toString());
    }

    @Test
    void missingFileIsWrittenWithComments(@TempDir Path dir) throws Exception {
        ConfigFile<ServerConfig> file = server(dir);
        assertEquals(ServerConfig.DEFAULT, file.load());
        String text = Files.readString(dir.resolve("server.json5"));
        assertTrue(text.contains("// Destroyed from every money sale"), text);
        assertTrue(text.contains("symbol: \"₮\""), text);
    }

    @Test
    void partialAndBrokenValuesFallBackToDefaults(@TempDir Path dir) throws Exception {
        ConfigFile.Result<ServerConfig> result = server(dir).decode("""
            {
              currency: { symbol: "$", decimals: 9 },
              vending: { feePercent: 5.5, defaultAnimation: "spin", colour: "red" },
              pay: { taxPercent: 150 },
              economy: { startingBalance: "250.75" },
            }
            """);
        ServerConfig config = result.value();
        assertEquals("$", config.currency().symbol());
        assertEquals(2, config.currency().decimals(), "out of range → default");
        assertEquals(new BigDecimal("5.5"), config.vending().feePercent());
        assertEquals(dev.eliasnvx.tradery.vending.DisplayAnimation.SPIN, config.vending().defaultAnimation());
        assertEquals(BigDecimal.ZERO, config.pay().taxPercent(), "out of range → default");
        assertEquals(new BigDecimal("250.75"), config.economy().startingBalance());
        assertEquals(ServerConfig.DEFAULT.ore(), config.ore());

        String problems = String.join("\n", result.problems());
        assertTrue(problems.contains("currency.decimals"), problems);
        assertTrue(problems.contains("vending.colour"), problems);
        assertTrue(problems.contains("pay.taxPercent"), problems);
        assertEquals(3, result.problems().size(), problems);
    }

    @Test
    void commentsCoverRealKeys() {
        // A typo in a comment key would silently drop the comment
        com.google.gson.JsonObject json = ServerConfig.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, ServerConfig.DEFAULT)
            .getOrThrow().getAsJsonObject();
        for (String path : ServerConfig.COMMENTS.keySet()) {
            assertTrue(exists(json, path), "comment for unknown key " + path);
        }
        com.google.gson.JsonObject client = ClientConfig.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, ClientConfig.DEFAULT)
            .getOrThrow().getAsJsonObject();
        for (String path : ClientConfig.COMMENTS.keySet()) {
            assertTrue(exists(client, path), "comment for unknown key " + path);
        }
    }

    private static boolean exists(com.google.gson.JsonObject root, String path) {
        com.google.gson.JsonElement current = root;
        for (String part : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) {
                return false;
            }
            current = current.getAsJsonObject().get(part);
        }
        return current != null;
    }
}
