package roj.config;

import roj.config.serial.CVisitor;
import roj.io.MyDataInput;
import roj.io.MyDataInputStream;
import roj.reflect.Unaligned;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;

import static roj.config.NBTParser.*;

/**
 * Extended NBT
 * 对中文的一些优化
 * 同时因为使用MyDataInput（也许）具有更好的性能
 * @author Roj234
 * @since 2024/4/30 20:17
 */
public final class XNBTParser implements BinaryParser {
	public static final byte X_GB18030_STRING = 13, X_LATIN1_STRING = 14, X_NULL = 15, X_SAME_LIST = 16;

	@Override
	public <T extends CVisitor> T parse(InputStream in, int flag, T cc) throws IOException { root(MyDataInputStream.wrap(in), cc); return cc; }
	public <T extends CVisitor> T parse(DynByteBuf buf, int flag, T cc) throws IOException { root(buf, cc); return cc; }

	public ConfigMaster format() { return ConfigMaster.XNBT; }

	public static void root(MyDataInput in, CVisitor cc) throws IOException {
		byte type = in.readByte();
		if (type == 0) return;

		char n = in.readChar();
		if (n != 0) throw new IOException("根节点不应该有名称");
		element(in, type, cc);
	}

	private static void element(MyDataInput in, byte type, CVisitor cc) throws IOException {
		switch (type) {
			default -> throw new IOException("Corrupted NBT(invalid id "+type+")");
			case X_NULL -> cc.valueNull();
			case BYTE -> cc.value(in.readByte());
			case SHORT -> cc.value(in.readShort());
			case INT -> cc.value(in.readInt());
			case LONG -> cc.value(in.readLong());
			case FLOAT -> cc.value(in.readFloat());
			case DOUBLE -> cc.value(in.readDouble());
			case BYTE_ARRAY -> {
				var arr = (byte[]) Unaligned.U.allocateUninitializedArray(byte.class, in.readInt());
				in.readFully(arr);
				cc.value(arr);
			}
			case STRING -> cc.value(in.readUTF());
			case X_GB18030_STRING -> cc.value(in.readGB(in.readVUInt()));
			case X_LATIN1_STRING -> cc.value(in.readAscii(in.readVUInt()));
			case X_SAME_LIST -> {
				int mapKeySize = in.readUnsignedByte();
				String[] mapKey = new String[mapKeySize];
				for (int i = 0; i < mapKeySize; i++)
					mapKey[i] = in.readUTF();

				int len = in.readInt();
				while (len-- > 0) useKey(in, cc, mapKey);
			}
			case LIST -> {
				type = in.readByte();
				int len = in.readInt();
				cc.valueList(len);
				while (len-- > 0) element(in, type != 0 ? type : in.readByte(), cc);
				cc.pop();
			}
			case COMPOUND -> {
				cc.valueMap();
				while (true) {
					type = in.readByte();
					if (type == 0) break;
					cc.key(in.readUTF());
					element(in, type, cc);
				}
				cc.pop();
			}
			case INT_ARRAY -> {
				var arr = (int[]) Unaligned.U.allocateUninitializedArray(int.class, in.readInt());
				for (int i = 0; i < arr.length; i++) arr[i] = in.readInt();
				cc.value(arr);
			}
			case LONG_ARRAY -> {
				var arr = (long[]) Unaligned.U.allocateUninitializedArray(long.class, in.readInt());
				for (int i = 0; i < arr.length; i++) arr[i] = in.readLong();
				cc.value(arr);
			}
		}
	}

	private static void useKey(MyDataInput in, CVisitor cc, String[] mapKey) throws IOException {
		cc.valueMap(mapKey.length);
		for (String key : mapKey) {
			cc.key(key);

			byte type = in.readByte();
			element(in, type, cc);
		}
		cc.pop();
	}
}