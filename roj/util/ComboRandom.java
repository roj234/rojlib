package roj.util;

import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * 长种子随机数
 *
 * @author Roj233
 * @since 2021/7/10 14:16
 */
public class ComboRandom extends Random {
	private final long[] seeds;
	private int i = 0;

	private static final long multiplier = 0x5DEECE66DL;
	private static final long addend = 0xBL;
	private static final long mask = (1L << 48) - 1;

	public ComboRandom(long[] randoms) {
		this.seeds = randoms;
	}

	@Override
	protected int next(int bits) {
		long seed = (seeds[i == seeds.length ? i = 0 : i] * multiplier + addend) & mask;
		seeds[i++] = seed;
		return (int) (seed >>> (48 - bits));
	}

	public static ComboRandom from(String keys) {
		return from(keys.getBytes(StandardCharsets.UTF_8));
	}
	public static ComboRandom from(byte[] b) {
		return from(ByteList.wrap(b));
	}
	public static ComboRandom from(byte[] b, int off, int len) {
		return from(ByteList.wrap(b, off, len));
	}

	public static ComboRandom from(DynByteBuf buf) {
		if (!buf.isReadable()) throw new IllegalStateException("Empty buffer");

		long[] seed = new long[(buf.readableBytes() >> 3) + ((buf.readableBytes() & 7) != 0 ? 1 : 0)];
		int i = 0;
		while (buf.readableBytes() >= 8) seed[i++] = buf.readLong();
		while (buf.isReadable()) {
			seed[i] = (seed[i] << 8) | buf.readUnsignedByte();
		}
		return new ComboRandom(seed);
	}
}
