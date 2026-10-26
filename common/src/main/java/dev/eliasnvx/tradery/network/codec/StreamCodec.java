package dev.eliasnvx.tradery.network.codec;

import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The 1.20.5+ {@code dev.eliasnvx.tradery.network.codec.StreamCodec} for 1.20.1, which writes packets by hand: the same
 * methods, so packet and menu data code reads like the other branches. Buffers are {@code FriendlyByteBuf}.
 */
public interface StreamCodec<B, V> extends StreamDecoder<B, V>, StreamEncoder<B, V> {
    static <B, V> StreamCodec<B, V> of(StreamEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return decoder.decode(buf);
            }

            @Override
            public void encode(B buf, V value) {
                encoder.encode(buf, value);
            }
        };
    }

    /** Writes nothing, reads {@code value}. */
    static <B, V> StreamCodec<B, V> unit(V value) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return value;
            }

            @Override
            public void encode(B buf, V written) {
                if (!Objects.equals(written, value)) {
                    throw new IllegalStateException("Can't encode '" + written + "', expected '" + value + "'");
                }
            }
        };
    }

    default <O> StreamCodec<B, O> apply(CodecOperation<B, V, O> operation) {
        return operation.apply(this);
    }

    default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> to, Function<? super O, ? extends V> from) {
        StreamCodec<B, V> self = this;
        return of((buf, value) -> self.encode(buf, from.apply(value)), buf -> to.apply(self.decode(buf)));
    }

    static <B, C, T1> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1, Function<T1, C> factory) {
        return of((buf, value) -> c1.encode(buf, g1.apply(value)), buf -> factory.apply(c1.decode(buf)));
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
                                                      StreamCodec<? super B, T2> c2, Function<C, T2> g2,
                                                      BiFunction<T1, T2, C> factory) {
        return of((buf, value) -> {
            c1.encode(buf, g1.apply(value));
            c2.encode(buf, g2.apply(value));
        }, buf -> {
            T1 v1 = c1.decode(buf);
            T2 v2 = c2.decode(buf);
            return factory.apply(v1, v2);
        });
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
                                                          StreamCodec<? super B, T2> c2, Function<C, T2> g2,
                                                          StreamCodec<? super B, T3> c3, Function<C, T3> g3,
                                                          Function3<T1, T2, T3, C> factory) {
        return of((buf, value) -> {
            c1.encode(buf, g1.apply(value));
            c2.encode(buf, g2.apply(value));
            c3.encode(buf, g3.apply(value));
        }, buf -> {
            T1 v1 = c1.decode(buf);
            T2 v2 = c2.decode(buf);
            T3 v3 = c3.decode(buf);
            return factory.apply(v1, v2, v3);
        });
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
                                                              StreamCodec<? super B, T2> c2, Function<C, T2> g2,
                                                              StreamCodec<? super B, T3> c3, Function<C, T3> g3,
                                                              StreamCodec<? super B, T4> c4, Function<C, T4> g4,
                                                              Function4<T1, T2, T3, T4, C> factory) {
        return of((buf, value) -> {
            c1.encode(buf, g1.apply(value));
            c2.encode(buf, g2.apply(value));
            c3.encode(buf, g3.apply(value));
            c4.encode(buf, g4.apply(value));
        }, buf -> {
            T1 v1 = c1.decode(buf);
            T2 v2 = c2.decode(buf);
            T3 v3 = c3.decode(buf);
            T4 v4 = c4.decode(buf);
            return factory.apply(v1, v2, v3, v4);
        });
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
                                                                  StreamCodec<? super B, T2> c2, Function<C, T2> g2,
                                                                  StreamCodec<? super B, T3> c3, Function<C, T3> g3,
                                                                  StreamCodec<? super B, T4> c4, Function<C, T4> g4,
                                                                  StreamCodec<? super B, T5> c5, Function<C, T5> g5,
                                                                  Function5<T1, T2, T3, T4, T5, C> factory) {
        return of((buf, value) -> {
            c1.encode(buf, g1.apply(value));
            c2.encode(buf, g2.apply(value));
            c3.encode(buf, g3.apply(value));
            c4.encode(buf, g4.apply(value));
            c5.encode(buf, g5.apply(value));
        }, buf -> {
            T1 v1 = c1.decode(buf);
            T2 v2 = c2.decode(buf);
            T3 v3 = c3.decode(buf);
            T4 v4 = c4.decode(buf);
            T5 v5 = c5.decode(buf);
            return factory.apply(v1, v2, v3, v4, v5);
        });
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(StreamCodec<? super B, T1> c1, Function<C, T1> g1,
                                                                      StreamCodec<? super B, T2> c2, Function<C, T2> g2,
                                                                      StreamCodec<? super B, T3> c3, Function<C, T3> g3,
                                                                      StreamCodec<? super B, T4> c4, Function<C, T4> g4,
                                                                      StreamCodec<? super B, T5> c5, Function<C, T5> g5,
                                                                      StreamCodec<? super B, T6> c6, Function<C, T6> g6,
                                                                      Function6<T1, T2, T3, T4, T5, T6, C> factory) {
        return of((buf, value) -> {
            c1.encode(buf, g1.apply(value));
            c2.encode(buf, g2.apply(value));
            c3.encode(buf, g3.apply(value));
            c4.encode(buf, g4.apply(value));
            c5.encode(buf, g5.apply(value));
            c6.encode(buf, g6.apply(value));
        }, buf -> {
            T1 v1 = c1.decode(buf);
            T2 v2 = c2.decode(buf);
            T3 v3 = c3.decode(buf);
            T4 v4 = c4.decode(buf);
            T5 v5 = c5.decode(buf);
            T6 v6 = c6.decode(buf);
            return factory.apply(v1, v2, v3, v4, v5, v6);
        });
    }

    /** Turns a codec of one value into a codec of something built from it (a list, an optional). */
    @FunctionalInterface
    interface CodecOperation<B, S, T> {
        StreamCodec<B, T> apply(StreamCodec<B, S> codec);
    }
}
