package dev.eliasnvx.tradery.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.eliasnvx.tradery.util.CodecSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The in-game day for wages, produce, orders and market days, which only moves forward. 1.21.1's {@code /time set}
 * moves the absolute day time back (26.x keeps a clock that only advances), so the day number is counted here:
 * natural time and {@code /time add} add the days that passed; a clock set back from the evening or the night to
 * the morning ({@code /time set day} to skip a night) starts the next day; any other jump back changes nothing.
 * Saved data {@code tradery_days}. Server thread only.
 */
public final class DayCounter extends SavedData {
    static final String NAME = "tradery_days";
    private static final long DAY = 24000L;
    private static final long EVENING = 12000L;

    record State(long day, long lastDayTime) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("day", -1L).forGetter(State::day),
            Codec.LONG.optionalFieldOf("last_day_time", -1L).forGetter(State::lastDayTime)
        ).apply(i, State::new));
    }

    static final Codec<DayCounter> CODEC = State.CODEC.xmap(DayCounter::new, c -> new State(c.day, c.lastDayTime));
    public static final CodecSavedData.Factory<DayCounter> FACTORY = CodecSavedData.factory(NAME, CODEC, DayCounter::new);

    private long day = -1;
    private long lastDayTime = -1;

    public DayCounter() {
    }

    private DayCounter(State state) {
        this.day = state.day();
        this.lastDayTime = state.lastDayTime();
    }

    public static DayCounter get(MinecraftServer server) {
        return CodecSavedData.get(server, FACTORY, NAME);
    }

    /** Today's number for the overworld's day time {@code dayTime}. */
    public long today(long dayTime) {
        if (day < 0 || lastDayTime < 0) {
            day = Math.max(0, dayTime / DAY);
        } else if (dayTime >= lastDayTime) {
            day += dayTime / DAY - lastDayTime / DAY;
        } else if (lastDayTime % DAY >= EVENING && dayTime % DAY < EVENING) {
            day++;
        }
        if (dayTime != lastDayTime) {
            lastDayTime = dayTime;
            setDirty();
        }
        return day;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return CodecSavedData.save(CODEC, this, tag);
    }
}
