package dev.eliasnvx.tradery.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.datafixers.util.Pair;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/** Codec building blocks for the JSON5 configs. */
public final class ConfigCodecs {
    private ConfigCodecs() {
    }

    /**
     * A decimal number in major units ("100", 0.5, "12.50"). Reads the exact digits: JSON5 numbers are parsed
     * as {@link BigDecimal}, strings are accepted too.
     */
    public static final Codec<BigDecimal> DECIMAL = new Codec<>() {
        @Override
        public <T> DataResult<Pair<BigDecimal, T>> decode(DynamicOps<T> ops, T input) {
            DataResult<BigDecimal> number = ops.getNumberValue(input).flatMap(ConfigCodecs::toDecimal);
            if (number.result().isPresent()) {
                return number.map(n -> Pair.of(n, input));
            }
            return ops.getStringValue(input).flatMap(text -> {
                try {
                    return DataResult.success(Pair.of(new BigDecimal(text.trim()), input));
                } catch (NumberFormatException e) {
                    return DataResult.error(() -> "Not a number: " + text);
                }
            });
        }

        @Override
        public <T> DataResult<T> encode(BigDecimal input, DynamicOps<T> ops, T prefix) {
            return ops.mergeToPrimitive(prefix, ops.createNumeric(input));
        }

        @Override
        public String toString() {
            return "Decimal";
        }
    };

    /** A decimal {@code >= 0}. */
    public static final Codec<BigDecimal> NON_NEGATIVE = validate(DECIMAL, value -> value.signum() >= 0
        ? DataResult.success(value) : DataResult.error(() -> "Must not be negative: " + value.toPlainString()));

    /** A percentage in {@code [0, 100]}. */
    public static final Codec<BigDecimal> PERCENT = validate(DECIMAL, value ->
        value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0
            ? DataResult.success(value) : DataResult.error(() -> "Must be 0..100: " + value.toPlainString()));

    /** {@code codec} that refuses values failing {@code check}, both ways (DFU 6 has no {@code Codec#validate}). */
    public static <A> Codec<A> validate(Codec<A> codec, Function<A, DataResult<A>> check) {
        return codec.flatXmap(check, check);
    }

    /** A string of {@code min..max} characters (DFU 6 has no {@code Codec.string(min, max)}). */
    public static Codec<String> string(int min, int max) {
        return validate(Codec.STRING, value -> value.length() >= min && value.length() <= max
            ? DataResult.success(value)
            : DataResult.error(() -> "String \"" + value + "\" has " + value.length() + " characters, expected " + min + ".." + max));
    }

    private static DataResult<BigDecimal> toDecimal(Number number) {
        if (number instanceof BigDecimal decimal) {
            return DataResult.success(decimal);
        }
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return DataResult.error(() -> "Not a finite number: " + d);
            }
            return DataResult.success(new BigDecimal(Double.toString(d)));
        }
        try {
            return DataResult.success(new BigDecimal(number.toString()));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not a number: " + number);
        }
    }

    /** Case-insensitive enum by constant name; writes the constant name. */
    public static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        E[] values = type.getEnumConstants();
        return Codec.STRING.comapFlatMap(text -> {
            for (E value : values) {
                if (value.name().equalsIgnoreCase(text.trim())) {
                    return DataResult.success(value);
                }
            }
            return DataResult.error(() -> "Unknown value '" + text + "', expected one of " + Arrays.toString(values));
        }, E::name);
    }

    /**
     * {@code codec} as a field that falls back to {@code fallback} when missing or invalid (reported by
     * {@link ConfigDiff}), and that is always written, even when it equals the default, so config files list
     * every option. DFU 6's {@code optionalFieldOf} is lenient: an invalid value reads as missing.
     */
    public static <A> MapCodec<A> field(Codec<A> codec, String name, A fallback) {
        return codec.optionalFieldOf(name).xmap(value -> value.orElse(fallback), Optional::of);
    }

    /** An int in {@code [min, max]}. */
    public static Codec<Integer> intRange(int min, int max) {
        return Codec.intRange(min, max);
    }

    /** A double in {@code [min, max]}. */
    public static Codec<Double> doubleRange(double min, double max) {
        return Codec.doubleRange(min, max);
    }
}
