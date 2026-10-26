package dev.eliasnvx.tradery.network.codec;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/** The 1.20.5+ {@code ByteBufCodecs} members Tradery uses, over {@link FriendlyByteBuf} (see {@link StreamCodec}). */
public final class ByteBufCodecs {
    public static final StreamCodec<FriendlyByteBuf, Boolean> BOOL = StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean);
    public static final StreamCodec<FriendlyByteBuf, Integer> INT = StreamCodec.of(FriendlyByteBuf::writeInt, FriendlyByteBuf::readInt);
    public static final StreamCodec<FriendlyByteBuf, Integer> VAR_INT = StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt);
    public static final StreamCodec<FriendlyByteBuf, Long> VAR_LONG = StreamCodec.of(FriendlyByteBuf::writeVarLong, FriendlyByteBuf::readVarLong);
    public static final StreamCodec<FriendlyByteBuf, Float> FLOAT = StreamCodec.of(FriendlyByteBuf::writeFloat, FriendlyByteBuf::readFloat);
    public static final StreamCodec<FriendlyByteBuf, Double> DOUBLE = StreamCodec.of(FriendlyByteBuf::writeDouble, FriendlyByteBuf::readDouble);

    private ByteBufCodecs() {
    }

    /** A string of at most {@code maxLength} characters (longer ones throw on both sides). */
    public static StreamCodec<FriendlyByteBuf, String> stringUtf8(int maxLength) {
        return StreamCodec.of((buf, value) -> buf.writeUtf(value, maxLength), buf -> buf.readUtf(maxLength));
    }

    public static <B extends FriendlyByteBuf, V> StreamCodec<B, Optional<V>> optional(StreamCodec<? super B, V> codec) {
        return StreamCodec.of((buf, value) -> {
            buf.writeBoolean(value.isPresent());
            value.ifPresent(v -> codec.encode(buf, v));
        }, buf -> buf.readBoolean() ? Optional.of(codec.decode(buf)) : Optional.empty());
    }

    public static <T> StreamCodec<FriendlyByteBuf, T> idMapper(IntFunction<T> byId, ToIntFunction<T> toId) {
        return StreamCodec.of((buf, value) -> buf.writeVarInt(toId.applyAsInt(value)), buf -> byId.apply(buf.readVarInt()));
    }

    /** A collection of at most {@code maxSize} elements: a bigger count from the wire throws before anything is allocated. */
    public static <B extends FriendlyByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(IntFunction<C> factory,
                                                                                                       StreamCodec<? super B, V> codec,
                                                                                                       int maxSize) {
        return StreamCodec.of((buf, value) -> {
            if (value.size() > maxSize) {
                throw new io.netty.handler.codec.EncoderException("Collection of " + value.size() + " is over " + maxSize);
            }
            buf.writeVarInt(value.size());
            for (V v : value) {
                codec.encode(buf, v);
            }
        }, buf -> {
            int size = buf.readVarInt();
            if (size < 0 || size > maxSize) {
                throw new DecoderException("Collection of " + size + " is over " + maxSize);
            }
            C result = factory.apply(Math.min(size, 65536));
            for (int i = 0; i < size; i++) {
                result.add(codec.decode(buf));
            }
            return result;
        });
    }

    public static <B extends FriendlyByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(IntFunction<C> factory,
                                                                                                       StreamCodec<? super B, V> codec) {
        return collection(factory, codec, Integer.MAX_VALUE);
    }

    public static <B extends FriendlyByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list() {
        return codec -> collection(ArrayList::new, codec);
    }

    public static <B extends FriendlyByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list(int maxSize) {
        return codec -> collection(ArrayList::new, codec, maxSize);
    }
}
