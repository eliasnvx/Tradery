package dev.eliasnvx.tradery.config;

import dev.eliasnvx.tradery.platform.Platform;

import java.nio.file.Path;

/** Current configs. Server values are read on the server thread; client values on the client thread. */
public final class TraderyConfig {
    private static ConfigFile<ServerConfig> serverFile;
    private static ConfigFile<ClientConfig> clientFile;
    private static volatile ServerConfig server = ServerConfig.DEFAULT;
    private static volatile ClientConfig client = ClientConfig.DEFAULT;

    private TraderyConfig() {
    }

    public static Path directory() {
        return Platform.get().configDir().resolve("tradery");
    }

    public static ServerConfig server() {
        return server;
    }

    public static ClientConfig client() {
        return client;
    }

    /** (Re)reads {@code server.json5}. */
    public static ServerConfig loadServer() {
        if (serverFile == null) {
            serverFile = new ConfigFile<>(directory().resolve("server.json5"), ServerConfig.CODEC, ServerConfig.DEFAULT,
                ServerConfig.COMMENTS, ServerConfig.HEADER);
        }
        server = serverFile.load();
        return server;
    }

    /** (Re)reads {@code client.json5}. */
    public static ClientConfig loadClient() {
        if (clientFile == null) {
            clientFile = new ConfigFile<>(directory().resolve("client.json5"), ClientConfig.CODEC, ClientConfig.DEFAULT,
                ClientConfig.COMMENTS, ClientConfig.HEADER);
        }
        client = clientFile.load();
        return client;
    }

    /** Replaces the client config and writes it. */
    public static void saveClient(ClientConfig config) {
        client = config;
        if (clientFile != null) {
            clientFile.save(config);
        }
    }
}
