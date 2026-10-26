package dev.eliasnvx.tradery.config;

import org.jetbrains.annotations.Nullable;
import dev.eliasnvx.tradery.vending.DisplayAnimation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

import static dev.eliasnvx.tradery.config.ConfigCodecs.field;

/** {@code config/tradery/client.json5}: HUD, notifications and display preferences of this player. */
public record ClientConfig(Hud hud, Notifications notifications, Vending vending, VillageTitle villageTitle) {

    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        public boolean right() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        public boolean bottom() {
            return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }
    }

    public enum Format {
        /** "1 250.00" */
        FULL,
        /** "1.2K" */
        SHORT
    }

    /** {@code SERVER} follows the server's default animation. */
    public enum AnimationOverride {
        SERVER(null), STATIC(DisplayAnimation.STATIC), SPIN(DisplayAnimation.SPIN), BOB(DisplayAnimation.BOB),
        SPIN_BOB(DisplayAnimation.SPIN_BOB), NONE(DisplayAnimation.NONE);

        private final @Nullable DisplayAnimation animation;

        AnimationOverride(@Nullable DisplayAnimation animation) {
            this.animation = animation;
        }

        /** The animation this forces, or null for the block's own. */
        public @Nullable DisplayAnimation animation() {
            return animation;
        }
    }

    public record Hud(boolean enabled, Corner corner, int offsetX, int offsetY, double scale, Format format,
                      boolean countUp, boolean popups) {
        static final Hud DEFAULT = new Hud(true, Corner.BOTTOM_RIGHT, 4, 4, 1.0, Format.FULL, true, true);
        static final Codec<Hud> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(Hud::enabled),
            field(ConfigCodecs.enumCodec(Corner.class), "corner", DEFAULT.corner).forGetter(Hud::corner),
            field(Codec.intRange(-4096, 4096), "offsetX", DEFAULT.offsetX).forGetter(Hud::offsetX),
            field(Codec.intRange(-4096, 4096), "offsetY", DEFAULT.offsetY).forGetter(Hud::offsetY),
            field(Codec.doubleRange(0.5, 2.0), "scale", DEFAULT.scale).forGetter(Hud::scale),
            field(ConfigCodecs.enumCodec(Format.class), "format", DEFAULT.format).forGetter(Hud::format),
            field(Codec.BOOL, "countUp", DEFAULT.countUp).forGetter(Hud::countUp),
            field(Codec.BOOL, "popups", DEFAULT.popups).forGetter(Hud::popups)
        ).apply(i, Hud::new));

        public Hud withEnabled(boolean enabled) {
            return new Hud(enabled, corner, offsetX, offsetY, scale, format, countUp, popups);
        }
    }

    public record Notifications(boolean sale, boolean empty, boolean noSpace, boolean offlineSummary) {
        static final Notifications DEFAULT = new Notifications(true, true, true, true);
        static final Codec<Notifications> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "sale", DEFAULT.sale).forGetter(Notifications::sale),
            field(Codec.BOOL, "empty", DEFAULT.empty).forGetter(Notifications::empty),
            field(Codec.BOOL, "noSpace", DEFAULT.noSpace).forGetter(Notifications::noSpace),
            field(Codec.BOOL, "offlineSummary", DEFAULT.offlineSummary).forGetter(Notifications::offlineSummary)
        ).apply(i, Notifications::new));
    }

    /** The look-at hint of vending blocks. {@code AUTO}: on, unless Jade (which shows the same) is installed. */
    public enum HintMode {
        AUTO, ON, OFF
    }

    public record Vending(AnimationOverride animationOverride, HintMode hint, int hintY) {
        static final Vending DEFAULT = new Vending(AnimationOverride.SERVER, HintMode.AUTO, 8);
        static final Codec<Vending> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(ConfigCodecs.enumCodec(AnimationOverride.class), "animationOverride", DEFAULT.animationOverride).forGetter(Vending::animationOverride),
            field(ConfigCodecs.enumCodec(HintMode.class), "hint", DEFAULT.hint).forGetter(Vending::hint),
            field(Codec.intRange(0, 4096), "hintY", DEFAULT.hintY).forGetter(Vending::hintY)
        ).apply(i, Vending::new));
    }

    /** The village's name and level shown for a few seconds when the player enters it. */
    public record VillageTitle(boolean enabled, int offsetY, double scale) {
        static final VillageTitle DEFAULT = new VillageTitle(true, 56, 1.0);
        static final Codec<VillageTitle> CODEC = RecordCodecBuilder.create(i -> i.group(
            field(Codec.BOOL, "enabled", DEFAULT.enabled).forGetter(VillageTitle::enabled),
            field(Codec.intRange(0, 4096), "offsetY", DEFAULT.offsetY).forGetter(VillageTitle::offsetY),
            field(Codec.doubleRange(0.5, 3.0), "scale", DEFAULT.scale).forGetter(VillageTitle::scale)
        ).apply(i, VillageTitle::new));
    }

    public static final ClientConfig DEFAULT = new ClientConfig(Hud.DEFAULT, Notifications.DEFAULT, Vending.DEFAULT, VillageTitle.DEFAULT);

    public static final Codec<ClientConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        field(Hud.CODEC, "hud", DEFAULT.hud).forGetter(ClientConfig::hud),
        field(Notifications.CODEC, "notifications", DEFAULT.notifications).forGetter(ClientConfig::notifications),
        field(Vending.CODEC, "vending", DEFAULT.vending).forGetter(ClientConfig::vending),
        field(VillageTitle.CODEC, "villageTitle", DEFAULT.villageTitle).forGetter(ClientConfig::villageTitle)
    ).apply(i, ClientConfig::new));

    public ClientConfig withHud(Hud hud) {
        return new ClientConfig(hud, notifications, vending, villageTitle);
    }

    public static final String HEADER = """
        Tradery client config (this computer only). JSON5.
        The game rewrites this file when you toggle the HUD; comments are regenerated.""";

    public static final Map<String, String> COMMENTS = Map.ofEntries(
        Map.entry("hud", "Balance on screen"),
        Map.entry("hud.enabled", "Also toggled with /tradery hud or a key (Controls > Tradery, unbound by default)"),
        Map.entry("hud.corner", "TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT or BOTTOM_RIGHT"),
        Map.entry("hud.offsetX", "Distance from the corner in GUI pixels"),
        Map.entry("hud.scale", "0.5..2.0"),
        Map.entry("hud.format", "FULL (1 250.00) or SHORT (1.2K)"),
        Map.entry("hud.countUp", "The number rolls to its new value"),
        Map.entry("hud.popups", "+120 / -40 lines on every change"),
        Map.entry("notifications", "Messages about your vending blocks"),
        Map.entry("notifications.offlineSummary", "Summary of sales made while you were away, shown when you join"),
        Map.entry("vending.animationOverride", "SERVER (as the server says), STATIC, SPIN, BOB, SPIN_BOB or NONE"),
        Map.entry("vending.hint", "Owner, offer and trade keys at the top of the screen while you look at a vending block: "
            + "AUTO (off when Jade is installed, it shows the same), ON or OFF"),
        Map.entry("vending.hintY", "Distance of the hint from the top of the screen, in GUI pixels"),
        Map.entry("villageTitle", "The village's name and level, shown for a few seconds at the top of the screen when you enter it"),
        Map.entry("villageTitle.offsetY", "Distance from the top of the screen, in GUI pixels: under the look-at hints, in the middle, clear of minimaps in the corners"),
        Map.entry("villageTitle.scale", "0.5..3.0")
    );
}
