package dev.eliasnvx.tradery.client;

import dev.eliasnvx.tradery.menu.ShopLedgerMenu;
import dev.eliasnvx.tradery.network.TraderyPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * "Show" in the shop ledger: a column of glowing particles over the shop for {@link #DURATION_TICKS}. Client side
 * only (the server just sends the position); a few beams at a time, drawn while the player is in their dimension.
 */
public final class ShopBeams {
    /** 30 seconds. */
    static final int DURATION_TICKS = 600;
    static final int MAX_BEAMS = 4;
    /** Blocks of beam above the shop. */
    static final int HEIGHT = 40;
    private static final int PARTICLES_PER_PULSE = 8;

    private record Beam(GlobalPos pos, long until) {
    }

    private static final List<Beam> BEAMS = new ArrayList<>();
    private static long ticks;

    private ShopBeams() {
    }

    /** The server answered "Show": start a beam, close the ledger so the player can look for it, say where it is. */
    public static void onBeam(TraderyPayloads.ShopBeamPayload payload) {
        GlobalPos pos = payload.pos();
        BEAMS.removeIf(beam -> beam.pos().equals(pos));
        while (BEAMS.size() >= MAX_BEAMS) {
            BEAMS.remove(0);
        }
        BEAMS.add(new Beam(pos, ticks + DURATION_TICKS));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.player.containerMenu instanceof ShopLedgerMenu) {
            minecraft.player.closeContainer();
        }
        BlockPos at = pos.pos();
        String where = at.getX() + " " + at.getY() + " " + at.getZ();
        if (minecraft.level != null && minecraft.level.dimension() == pos.dimension()) {
            long distance = Math.round(Math.sqrt(minecraft.player.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5)));
            minecraft.player.sendOverlayMessage(Component.translatable("tradery.ledger.beam", where, distance));
        } else {
            minecraft.player.sendOverlayMessage(Component.translatable("tradery.ledger.beam_elsewhere", where, dimensionName(pos)));
        }
    }

    /** A dimension's name: the vanilla three translated, others as their id. */
    public static Component dimensionName(GlobalPos pos) {
        String id = pos.dimension().identifier().toString();
        return switch (id) {
            case "minecraft:overworld" -> Component.translatable("tradery.ledger.dim.overworld");
            case "minecraft:the_nether" -> Component.translatable("tradery.ledger.dim.nether");
            case "minecraft:the_end" -> Component.translatable("tradery.ledger.dim.end");
            default -> Component.literal(id);
        };
    }

    public static void tick(Minecraft minecraft) {
        ticks++;
        if (BEAMS.isEmpty()) {
            return;
        }
        BEAMS.removeIf(beam -> beam.until() <= ticks);
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused() || ticks % 2 != 0) {
            return;
        }
        RandomSource random = level.getRandom();
        for (Beam beam : BEAMS) {
            if (beam.pos().dimension() != level.dimension()) {
                continue;
            }
            BlockPos pos = beam.pos().pos();
            for (int i = 0; i < PARTICLES_PER_PULSE; i++) {
                double y = pos.getY() + 1.1 + random.nextDouble() * HEIGHT;
                // Always visible: the point is to find the shop from afar
                level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.25, y,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.25, 0.0, 0.02, 0.0);
            }
        }
    }

    /** Beams still shown (for tests and the client test). */
    public static int active() {
        return BEAMS.size();
    }

    public static void reset() {
        BEAMS.clear();
    }
}
