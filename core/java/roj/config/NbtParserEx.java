package roj.config;

import roj.io.XDataInput;
import roj.io.XDataInputStream;
import roj.text.FastCharset;
import roj.util.ArrayUtil;

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
public final class NbtParserEx implements Parser {
	public static final byte X_UTF16LE_STRING = 13, X_LATIN1_STRING = 14, X_NULL = 15, X_DEDUP_LIST = 16;

	public static final NbtParserEx INSTANCE = new NbtParserEx();
	private NbtParserEx() {}

	@Override
	public void parse(InputStream in, ValueEmitter emitter) throws IOException {Parse(XDataInputStream.wrap(in), emitter);}

	public static void Parse(XDataInput in, ValueEmitter cc) throws IOException {
		byte type = in.readByte();
		if (type == 0) return;

		char n = in.readChar();
		if (n != 0) throw new IOException("根节点不应该有名称");
		parse(in, type, cc);
	}

	private static void parse(XDataInput in, byte type, ValueEmitter cc) throws IOException {
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
				int length = in.readInt();
				var arr = ArrayUtil.newUninitializedByteArray(length);
				in.readFully(arr);
				cc.emit(arr);
			}
			case STRING -> cc.emit(in.readUTF());
			case X_UTF16LE_STRING -> cc.emit(in.readVUIStr(in.readVUInt(), FastCharset.UTF16LE()));
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
					cc.emitKey(in.readUTF());
					parse(in, type, cc);
				}
				cc.pop();
			}
			case INT_ARRAY -> {
				int length = in.readInt();
				var arr = ArrayUtil.newUninitializedIntArray(length);
				for (int i = 0; i < arr.length; i++) arr[i] = in.readInt();
				cc.emit(arr);
			}
			case LONG_ARRAY -> {
				int length = in.readInt();
				var arr = ArrayUtil.newUninitializedLongArray(length);
				for (int i = 0; i < arr.length; i++) arr[i] = in.readLong();
				cc.emit(arr);
			}
		}
	}

	private static void useKey(XDataInput in, ValueEmitter cc, String[] mapKey) throws IOException {
		cc.emitMap(mapKey.length);
		for (String key : mapKey) {
			cc.emitKey(key);

			byte type = in.readByte();
			parse(in, type, cc);
		}
		cc.pop();
	}
}