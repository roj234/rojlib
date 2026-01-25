package roj.config;

import roj.util.ArrayUtil;
import roj.util.DynByteBuf;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * NBT IO class
 *
 * @see <a href="https://github.com/udoprog/c10t/blob/master/docs/NBT.txt">Online NBT specification</a>
 */
public final class NbtParser implements Parser {
	public static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

	public static final NbtParser INSTANCE = new NbtParser();
	private NbtParser() {}

	@Override
	public void parse(InputStream in, ValueEmitter emitter) throws IOException {Parse((DataInput) (in instanceof DataInput ? in : new DataInputStream(in)), emitter);}
	public void parse(DynByteBuf buf, ValueEmitter emitter) throws IOException {Parse(buf, emitter);}

	public static void Parse(DataInput in, ValueEmitter emitter) throws IOException {
		byte type = in.readByte();
		if (type == 0) return;

		char n = in.readChar();
		if (n != 0) throw new IOException("根节点不应该有名称");

		parse(in, type, emitter);
	}

	private static void parse(DataInput in, byte type, ValueEmitter cc) throws IOException {
		switch (type) {
			default -> throw new IOException("Corrupted NBT");
			case BYTE -> cc.emit(in.readByte());
			case SHORT -> cc.emit(in.readShort());
			case INT -> cc.emit(in.readInt());
			case LONG -> cc.emit(in.readLong());
			case FLOAT -> cc.emit(in.readFloat());
			case DOUBLE -> cc.emit(in.readDouble());
			case BYTE_ARRAY -> {
				int len = in.readInt();
				if (cc.allowArray()) {
					var arr = ArrayUtil.newUninitializedByteArray(len);
					for (int i = 0; i < arr.length; i++) arr[i] = in.readByte();
					cc.emit(arr);
				} else {
					cc.emitList(len);
					for (int i = 0; i < len; i++) cc.emit(in.readByte());
					cc.pop();
				}
			}
			case STRING -> cc.emit(in.readUTF());
			case LIST -> {
				type = in.readByte();
				int len = in.readInt();
				cc.emitList(len);
				while (len-- > 0) parse(in, type, cc);
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
				int len = in.readInt();
				if (cc.allowArray()) {
					var arr = ArrayUtil.newUninitializedIntArray(len);
					for (int i = 0; i < arr.length; i++) arr[i] = in.readInt();
					cc.emit(arr);
				} else {
					cc.emitList(len);
					for (int i = 0; i < len; i++) cc.emit(in.readInt());
					cc.pop();
				}
			}
			case LONG_ARRAY -> {
				int len = in.readInt();
				if (cc.allowArray()) {
					var arr = ArrayUtil.newUninitializedLongArray(len);
					for (int i = 0; i < arr.length; i++) arr[i] = in.readLong();
					cc.emit(arr);
				} else {
					cc.emitList(len);
					for (int i = 0; i < len; i++) cc.emit(in.readLong());
					cc.pop();
				}
			}
		}
	}
}