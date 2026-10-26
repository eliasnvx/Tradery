package dev.eliasnvx.tradery.neoforge.clienttest.shots;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Dev-only state for the screenshot run: hides vanilla's HUD (hotbar, hearts, crosshair, effects) while Tradery's
 * overlays and the action bar stay, and records rendered frames as raw RGBA at a fixed frame rate.
 */
public final class CleanHud {
    public static volatile boolean hideVanillaHud;

    private static FileChannel clip;
    private static ByteBuffer frame;
    private static long clipStart;
    private static long framesWritten;
    private static int fps;

    private CleanHud() {
    }

    /** Render thread: starts writing frames to {@code raw} ({@code width x height x 4} bytes each, RGBA). */
    public static void startClip(Path raw, int framesPerSecond) throws IOException {
        clip = FileChannel.open(raw, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        fps = framesPerSecond;
        framesWritten = 0;
        clipStart = System.nanoTime();
    }

    /** Render thread: stops recording; returns the number of frames written. */
    public static long stopClip() throws IOException {
        FileChannel channel = clip;
        clip = null;
        if (channel != null) {
            channel.close();
        }
        return framesWritten;
    }

    /** Called after every rendered frame: writes it as many times as the fixed frame rate needs (0 if early). */
    public static void onFrameRendered(Minecraft minecraft) {
        FileChannel channel = clip;
        if (channel == null) {
            return;
        }
        long due = (System.nanoTime() - clipStart) * fps / 1_000_000_000L + 1;
        if (framesWritten >= due) {
            return;
        }
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            int[] pixels = image.getPixelsRGBA();
            if (frame == null || frame.capacity() != pixels.length * 4) {
                frame = ByteBuffer.allocateDirect(pixels.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            }
            frame.clear();
            frame.asIntBuffer().put(pixels);
            while (framesWritten < due) {
                frame.rewind();
                while (frame.hasRemaining()) {
                    channel.write(frame);
                }
                framesWritten++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Can't write the clip", e);
        }
    }
}
