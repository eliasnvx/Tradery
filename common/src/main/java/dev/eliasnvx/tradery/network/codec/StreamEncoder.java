package dev.eliasnvx.tradery.network.codec;

/** Writes a value to a buffer (see {@link StreamCodec}). */
@FunctionalInterface
public interface StreamEncoder<O, T> {
    void encode(O buf, T value);
}
