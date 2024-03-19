package roj.minecraft.server.util;

import roj.config.JSONParser;
import roj.config.serial.ToNBT;
import roj.io.IOUtil;
import roj.math.Vec3i;
import roj.minecraft.server.MinecraftServer;
import roj.util.DynByteBuf;
import roj.util.Helpers;

/**
 * @author Roj234
 * @since 2024/3/20 0020 3:55
 */
public class Utils {
	public static byte[] constantize(String path) {
		try {
			ToNBT ser = new ToNBT(IOUtil.getSharedByteBuf());
			return new JSONParser().parse(MinecraftServer.INSTANCE.getResource(path), 0, ser).buffer().toByteArray();
		} catch (Exception e) {
			Helpers.athrow(e);
			return Helpers.nonnull();
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

	public static void writeFakeLongArray(DynByteBuf buf, int[] data) {
		buf.putVarInt((data.length+1) / 2);
		writeFakeLongArray2(buf, data);
	}
	public static void writeFakeLongArray2(DynByteBuf buf, int[] data) {
		int i = 1;
		while (i < data.length) {
			buf.putInt(data[i]).putInt(data[i-1]);
			i += 2;
		}
		if (i == data.length) buf.putInt(0).putInt(data[i-1]);
	}
}