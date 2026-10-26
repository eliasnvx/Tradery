package dev.eliasnvx.tradery.network.codec;

/** Reads a value from a buffer (see {@link StreamCodec}). */
@FunctionalInterface
public interface StreamDecoder<I, T> {
    T decode(I buf);
}
