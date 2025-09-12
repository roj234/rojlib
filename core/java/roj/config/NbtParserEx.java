package roj.config;

import roj.io.MyDataInput;
import roj.io.MyDataInputStream;
import roj.reflect.Unaligned;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;

import static roj.config.NbtParser.*;

/**
 * Extended NBT
 * 对中文的一些优化
 * 同时因为使用MyDataInput（也许）具有更好的性能
 * @author Roj234
 * @since 2024/4/30 20:17
 */
public final class NbtParserEx implements BinaryParser {
	public static final byte X_GB18030_STRING = 13, X_LATIN1_STRING = 14, X_NULL = 15, X_DEDUP_LIST = 16;

	@Override
	public <T extends ValueEmitter> T parse(InputStream in, int flag, T emitter) throws IOException { parse(MyDataInputStream.wrap(in), emitter); return emitter; }
	public <T extends ValueEmitter> T parse(DynByteBuf buf, int flag, T emitter) throws IOException { parse(buf, emitter); return emitter; }

	public static void parse(MyDataInput in, ValueEmitter cc) throws IOException {
		byte type = in.readByte();
		if (type == 0) return;

		char n = in.readChar();
		if (n != 0) throw new IOException("根节点不应该有名称");
		parse(in, type, cc);
	}

	private static void parse(MyDataInput in, byte type, ValueEmitter cc) throws IOException {
		switch (type) {
			default -> throw new IOException("Corrupted NBT(invalid id "+type+")");
			case X_NULL -> cc.emitNull();
			case BYTE -> cc.emit(in.readByte());
			case SHORT -> cc.emit(in.readShort());
			case INT -> cc.emit(in.readInt());
			case LONG -> cc.emit(in.readLong());
			case FLOAT -> cc.emit(in.readFloat());
			case DOUBLE -> cc.emit(in.readDouble());
			case BYTE_ARRAY -> {
				var arr = (byte[]) Unaligned.U.allocateUninitializedArray(byte.class, in.readInt());
				in.readFully(arr);
				cc.emit(arr);
			}
			case STRING -> cc.emit(in.readUTF());
			case X_GB18030_STRING -> cc.emit(in.readGB(in.readVUInt()));
			case X_LATIN1_STRING -> cc.emit(in.readAscii(in.readVUInt()));
			case X_DEDUP_LIST -> {
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
				cc.emitList(len);
				while (len-- > 0) parse(in, type != 0 ? type : in.readByte(), cc);
				cc.pop();
			}
			case COMPOUND -> {
				cc.emitMap();
				while (true) {
					type = in.readByte();
					if (type == 0) break;
					cc.key(in.readUTF());
					parse(in, type, cc);
				}
				cc.pop();
			}
			case INT_ARRAY -> {
				var arr = (int[]) Unaligned.U.allocateUninitializedArray(int.class, in.readInt());
				for (int i = 0; i < arr.length; i++) arr[i] = in.readInt();
				cc.emit(arr);
			}
			case LONG_ARRAY -> {
				var arr = (long[]) Unaligned.U.allocateUninitializedArray(long.class, in.readInt());
				for (int i = 0; i < arr.length; i++) arr[i] = in.readLong();
				cc.emit(arr);
			}
		}
	}

	private static void useKey(MyDataInput in, ValueEmitter cc, String[] mapKey) throws IOException {
		cc.emitMap(mapKey.length);
		for (String key : mapKey) {
			cc.key(key);

			byte type = in.readByte();
			parse(in, type, cc);
		}
		cc.pop();
	}
}