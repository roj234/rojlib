package roj.plugins.minecraft.server.util;

import roj.collect.BitArray;
import roj.util.OperationDone;
import roj.config.JSONParser;
import roj.config.serial.ToNBT;
import roj.io.IOUtil;
import roj.math.Vec3i;
import roj.plugins.minecraft.server.MinecraftServer;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.function.IntConsumer;

/**
 * @author Roj234
 * @since 2024/3/20 3:55
 */
public class Utils {
	public static byte[] constantize(String path) {
		try {
			ToNBT ser = new ToNBT(IOUtil.getSharedByteBuf());
			return new JSONParser().parse(MinecraftServer.INSTANCE.getResource(path), 0, ser).buffer().toByteArray();
		} catch (Exception e) {
			return Helpers.athrow2(e);
		}
	}

	public static long pos2long(int x, int y, int z) {
		long pos = 0L;
		// MSB X Z Y LSB
		pos |= (y & 4095L) << 0;
		pos |= (z & 67108863L) << 12;
		pos |= (x & 67108863L) << 38; // 12 + 26
		return pos;
	}
	public static Vec3i readBlockPos(DynByteBuf in) {
		long pos = in.readLong();
		return new Vec3i((int) (pos >> 38), (int) (pos << 64-12 >> 64-12), (int) (pos << 64-38 >> 64-26));
	}

	@Deprecated
	public static void writeFakeLongArray2(DynByteBuf buf, int[] data) {
		int i = 1;
		while (i < data.length) {
			buf.putInt(data[i]).putInt(data[i-1]);
			i += 2;
		}
		if (i == data.length) buf.putInt(0).putInt(data[i-1]);
	}

	public static void writeUncompressedLongArray(ByteList buf, BitArray palette) {
		int bits = palette.bits();
		int size = palette.length();

		var elementsPerLong = (char)(64 / bits);
		int arrayLength = (size + elementsPerLong - 1) / elementsPerLong;

		var writer = new IntConsumer() {
			long data = 0;
			int index;
			int written;

			@Override
			public void accept(int value) {
				data |= (long) value << (index * bits);
				if (++index == elementsPerLong) {
					index = 0;
					written++;
					buf.putLong(data);
					data = 0;
				}
			}
		};

		buf.putVarInt(arrayLength);
		palette.getAll(0, palette.length(), writer);
		if (writer.index != 0) {
			buf.putLong(writer.data);
			writer.written++;
		}
	}
}