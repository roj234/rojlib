package roj.config;

import roj.config.serial.CVisitor;
import roj.reflect.Unaligned;
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
public final class NBTParser implements BinaryParser {
	public static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

	@Override
	public <T extends CVisitor> T parse(InputStream in, int flag, T cc) throws IOException { parse((DataInput) (in instanceof DataInput ? in : new DataInputStream(in)), cc); return cc; }
	public <T extends CVisitor> T parse(DynByteBuf buf, int flag, T cc) throws IOException { parse(buf, cc); return cc; }

	public ConfigMaster format() { return ConfigMaster.NBT; }

	public static void parse(DataInput in, CVisitor cc) throws IOException {
		byte type = in.readByte();
		if (type == 0) return;

		char n = in.readChar();
		if (n != 0) throw new IOException("根节点不应该有名称");

		if (cc.supportArray()) parseSA(in, type, cc);
		else parse(in, type, cc);
	}

	private static void parse(DataInput in, byte type, CVisitor cc) throws IOException {
		switch (type) {
			default -> throw new IOException("Corrupted NBT");
			case BYTE -> cc.value(in.readByte());
			case SHORT -> cc.value(in.readShort());
			case INT -> cc.value(in.readInt());
			case LONG -> cc.value(in.readLong());
			case FLOAT -> cc.value(in.readFloat());
			case DOUBLE -> cc.value(in.readDouble());
			case BYTE_ARRAY -> {
				int len = in.readInt();
				cc.valueList(len);
				for (int i = 0; i < len; i++) cc.value(in.readByte());
				cc.pop();
			}
			case STRING -> cc.value(in.readUTF());
			case LIST -> {
				type = in.readByte();
				int len = in.readInt();
				cc.valueList(len);
				while (len-- > 0) parse(in, type, cc);
				cc.pop();
			}
			case COMPOUND -> {
				cc.valueMap();
				while (true) {
					type = in.readByte();
					if (type == 0) break;
					cc.key(in.readUTF());
					parse(in, type, cc);
				}
				cc.pop();
			}
			case INT_ARRAY -> {
				int len = in.readInt();
				cc.valueList(len);
				for (int i = 0; i < len; i++) cc.value(in.readInt());
				cc.pop();
			}
			case LONG_ARRAY -> {
				int len = in.readInt();
				for (int i = 0; i < len; i++) cc.value(in.readLong());
				cc.pop();
			}
		}
	}
	private static void parseSA(DataInput in, byte type, CVisitor cc) throws IOException {
		switch (type) {
			default -> throw new IOException("Corrupted NBT");
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
			case LIST -> {
				type = in.readByte();
				int len = in.readInt();
				cc.valueList(len);
				while (len-- > 0) parseSA(in, type, cc);
				cc.pop();
			}
			case COMPOUND -> {
				cc.valueMap();
				while (true) {
					type = in.readByte();
					if (type == 0) break;
					cc.key(in.readUTF());
					parseSA(in, type, cc);
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
}