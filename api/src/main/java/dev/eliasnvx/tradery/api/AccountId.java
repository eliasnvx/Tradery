package dev.eliasnvx.tradery.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Identifies an account: a player's, or a system account owned by a mod.
 */
public sealed interface AccountId permits AccountId.Player, AccountId.System {
    /** Serialized as {@code "player:<uuid>"} or {@code "system:<namespace>:<path>"}. */
    Codec<AccountId> CODEC = Codec.STRING.comapFlatMap(AccountId::parse, AccountId::serialize);

    /**
     * @return the stable string form, see {@link #CODEC}
     */
    String serialize();

    /**
     * A player's account.
     *
     * @param uuid the player's UUID
     */
    record Player(UUID uuid) implements AccountId {
        @Override
        public String serialize() {
            return "player:" + uuid;
        }
    }

    /**
     * A system account.
     *
     * @param id the account id, in the owning mod's namespace
     */
    record System(ResourceLocation id) implements AccountId {
        @Override
        public String serialize() {
            return "system:" + id;
        }
    }

    /**
     * @param uuid a player UUID
     * @return the player's account id
     */
    static AccountId player(UUID uuid) {
        return new Player(uuid);
    }

    /**
     * @param id a system account id
     * @return the system account id
     */
    static AccountId system(ResourceLocation id) {
        return new System(id);
    }

    /**
     * Parses the string form.
     *
     * @param text {@code "player:<uuid>"} or {@code "system:<namespace>:<path>"}
     * @return the id or an error
     */
    static DataResult<AccountId> parse(String text) {
        if (text.startsWith("player:")) {
            try {
                return DataResult.success(new Player(UUID.fromString(text.substring("player:".length()))));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Bad player account id: " + text);
            }
        }
        if (text.startsWith("system:")) {
            return ResourceLocation.read(text.substring("system:".length())).map(System::new);
        }
        return DataResult.error(() -> "Unknown account id: " + text);
    }
}
