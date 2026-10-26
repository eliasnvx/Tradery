package dev.eliasnvx.tradery.network;

import com.mojang.datafixers.util.Function10;
import com.mojang.datafixers.util.Function11;
import com.mojang.datafixers.util.Function12;
import com.mojang.datafixers.util.Function7;
import com.mojang.datafixers.util.Function8;
import com.mojang.datafixers.util.Function9;
import dev.eliasnvx.tradery.network.codec.StreamCodec;

import java.util.function.Function;

/** {@code StreamCodec.composite} for 7 to 12 fields: 1.21.1 stops at 6 (26.x goes further). */
public final class PortCodecs {
    private PortCodecs() {
    }

    public static <B, C, T1, T2, T3, T4, T5, T6, T7> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, Function<C, T6> g6,
            StreamCodec<? super B, T7> c7, Function<C, T7> g7,
            Function7<T1, T2, T3, T4, T5, T6, T7, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 v1 = c1.decode(buf);
                T2 v2 = c2.decode(buf);
                T3 v3 = c3.decode(buf);
                T4 v4 = c4.decode(buf);
                T5 v5 = c5.decode(buf);
                T6 v6 = c6.decode(buf);
                T7 v7 = c7.decode(buf);
                return factory.apply(v1, v2, v3, v4, v5, v6, v7);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, g1.apply(value));
                c2.encode(buf, g2.apply(value));
                c3.encode(buf, g3.apply(value));
                c4.encode(buf, g4.apply(value));
                c5.encode(buf, g5.apply(value));
                c6.encode(buf, g6.apply(value));
                c7.encode(buf, g7.apply(value));
            }
        };
    }

    public static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, Function<C, T6> g6,
            StreamCodec<? super B, T7> c7, Function<C, T7> g7,
            StreamCodec<? super B, T8> c8, Function<C, T8> g8,
            Function8<T1, T2, T3, T4, T5, T6, T7, T8, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 v1 = c1.decode(buf);
                T2 v2 = c2.decode(buf);
                T3 v3 = c3.decode(buf);
                T4 v4 = c4.decode(buf);
                T5 v5 = c5.decode(buf);
                T6 v6 = c6.decode(buf);
                T7 v7 = c7.decode(buf);
                T8 v8 = c8.decode(buf);
                return factory.apply(v1, v2, v3, v4, v5, v6, v7, v8);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, g1.apply(value));
                c2.encode(buf, g2.apply(value));
                c3.encode(buf, g3.apply(value));
                c4.encode(buf, g4.apply(value));
                c5.encode(buf, g5.apply(value));
                c6.encode(buf, g6.apply(value));
                c7.encode(buf, g7.apply(value));
                c8.encode(buf, g8.apply(value));
            }
        };
    }

    public static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, Function<C, T6> g6,
            StreamCodec<? super B, T7> c7, Function<C, T7> g7,
            StreamCodec<? super B, T8> c8, Function<C, T8> g8,
            StreamCodec<? super B, T9> c9, Function<C, T9> g9,
            Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 v1 = c1.decode(buf);
                T2 v2 = c2.decode(buf);
                T3 v3 = c3.decode(buf);
                T4 v4 = c4.decode(buf);
                T5 v5 = c5.decode(buf);
                T6 v6 = c6.decode(buf);
                T7 v7 = c7.decode(buf);
                T8 v8 = c8.decode(buf);
                T9 v9 = c9.decode(buf);
                return factory.apply(v1, v2, v3, v4, v5, v6, v7, v8, v9);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, g1.apply(value));
                c2.encode(buf, g2.apply(value));
                c3.encode(buf, g3.apply(value));
                c4.encode(buf, g4.apply(value));
                c5.encode(buf, g5.apply(value));
                c6.encode(buf, g6.apply(value));
                c7.encode(buf, g7.apply(value));
                c8.encode(buf, g8.apply(value));
                c9.encode(buf, g9.apply(value));
            }
        };
    }

    public static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, Function<C, T6> g6,
            StreamCodec<? super B, T7> c7, Function<C, T7> g7,
            StreamCodec<? super B, T8> c8, Function<C, T8> g8,
            StreamCodec<? super B, T9> c9, Function<C, T9> g9,
            StreamCodec<? super B, T10> c10, Function<C, T10> g10,
            Function10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 v1 = c1.decode(buf);
                T2 v2 = c2.decode(buf);
                T3 v3 = c3.decode(buf);
                T4 v4 = c4.decode(buf);
                T5 v5 = c5.decode(buf);
                T6 v6 = c6.decode(buf);
                T7 v7 = c7.decode(buf);
                T8 v8 = c8.decode(buf);
                T9 v9 = c9.decode(buf);
                T10 v10 = c10.decode(buf);
                return factory.apply(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, g1.apply(value));
                c2.encode(buf, g2.apply(value));
                c3.encode(buf, g3.apply(value));
                c4.encode(buf, g4.apply(value));
                c5.encode(buf, g5.apply(value));
                c6.encode(buf, g6.apply(value));
                c7.encode(buf, g7.apply(value));
                c8.encode(buf, g8.apply(value));
                c9.encode(buf, g9.apply(value));
                c10.encode(buf, g10.apply(value));
            }
        };
    }

    public static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, Function<C, T6> g6,
            StreamCodec<? super B, T7> c7, Function<C, T7> g7,
            StreamCodec<? super B, T8> c8, Function<C, T8> g8,
            StreamCodec<? super B, T9> c9, Function<C, T9> g9,
            StreamCodec<? super B, T10> c10, Function<C, T10> g10,
            StreamCodec<? super B, T11> c11, Function<C, T11> g11,
            Function11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 v1 = c1.decode(buf);
                T2 v2 = c2.decode(buf);
                T3 v3 = c3.decode(buf);
                T4 v4 = c4.decode(buf);
                T5 v5 = c5.decode(buf);
                T6 v6 = c6.decode(buf);
                T7 v7 = c7.decode(buf);
                T8 v8 = c8.decode(buf);
                T9 v9 = c9.decode(buf);
                T10 v10 = c10.decode(buf);
                T11 v11 = c11.decode(buf);
                return factory.apply(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, g1.apply(value));
                c2.encode(buf, g2.apply(value));
                c3.encode(buf, g3.apply(value));
                c4.encode(buf, g4.apply(value));
                c5.encode(buf, g5.apply(value));
                c6.encode(buf, g6.apply(value));
                c7.encode(buf, g7.apply(value));
                c8.encode(buf, g8.apply(value));
                c9.encode(buf, g9.apply(value));
                c10.encode(buf, g10.apply(value));
                c11.encode(buf, g11.apply(value));
            }
        };
    }

    public static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, Function<C, T6> g6,
            StreamCodec<? super B, T7> c7, Function<C, T7> g7,
            StreamCodec<? super B, T8> c8, Function<C, T8> g8,
            StreamCodec<? super B, T9> c9, Function<C, T9> g9,
            StreamCodec<? super B, T10> c10, Function<C, T10> g10,
            StreamCodec<? super B, T11> c11, Function<C, T11> g11,
            StreamCodec<? super B, T12> c12, Function<C, T12> g12,
            Function12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buf) {
                T1 v1 = c1.decode(buf);
                T2 v2 = c2.decode(buf);
                T3 v3 = c3.decode(buf);
                T4 v4 = c4.decode(buf);
                T5 v5 = c5.decode(buf);
                T6 v6 = c6.decode(buf);
                T7 v7 = c7.decode(buf);
                T8 v8 = c8.decode(buf);
                T9 v9 = c9.decode(buf);
                T10 v10 = c10.decode(buf);
                T11 v11 = c11.decode(buf);
                T12 v12 = c12.decode(buf);
                return factory.apply(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12);
            }

            @Override
            public void encode(B buf, C value) {
                c1.encode(buf, g1.apply(value));
                c2.encode(buf, g2.apply(value));
                c3.encode(buf, g3.apply(value));
                c4.encode(buf, g4.apply(value));
                c5.encode(buf, g5.apply(value));
                c6.encode(buf, g6.apply(value));
                c7.encode(buf, g7.apply(value));
                c8.encode(buf, g8.apply(value));
                c9.encode(buf, g9.apply(value));
                c10.encode(buf, g10.apply(value));
                c11.encode(buf, g11.apply(value));
                c12.encode(buf, g12.apply(value));
            }
        };
    }

}
