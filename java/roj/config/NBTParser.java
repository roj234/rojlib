package roj.config;

import roj.config.serial.CVisitor;
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
	public <T extends CVisitor> T parse(InputStream in, int flag, T cc) throws IOException { root((DataInput) (in instanceof DataInput ? in : new DataInputStream(in)), cc); return cc; }
	public <T extends CVisitor> T parse(DynByteBuf buf, int flag, T cc) throws IOException { root(buf, cc); return cc; }

	public ConfigMaster format() { return ConfigMaster.NBT; }

	public static void root(DataInput in, CVisitor cc) throws IOException {
		byte type = in.readByte();
		if (type == 0) return;

		char n = in.readChar();
		if (n != 0) throw new IOException("根节点不应该有名称");
		element(in, type, cc);
	}

	private static void element(DataInput in, byte type, CVisitor cc) throws IOException {
		switch (type) {
			default -> throw new IOException("Corrupted NBT");
			case BYTE -> cc.value(in.readByte());
			case SHORT -> cc.value(in.readShort());
			case INT -> cc.value(in.readInt());
			case LONG -> cc.value(in.readLong());
			case FLOAT -> cc.value(in.readFloat());
			case DOUBLE -> cc.value(in.readDouble());
			case BYTE_ARRAY -> {
				byte[] ba = new byte[in.readInt()];
				in.readFully(ba);
				cc.value(ba);
			}
			case STRING -> cc.value(in.readUTF());
			case LIST -> {
				byte listType = in.readByte();
				int len = in.readInt();
				cc.valueList(len);
				while (len-- > 0) element(in, listType, cc);
				cc.pop();
			}
			case COMPOUND -> {
				cc.valueMap();
				while (true) {
					byte flg = in.readByte();
					if (flg == 0) break;
					cc.key(in.readUTF());
					element(in, flg, cc);
				}
				cc.pop();
			}
			case INT_ARRAY -> {
				int[] ia = new int[in.readInt()];
				for (int i = 0; i < ia.length; i++) ia[i] = in.readInt();
				cc.value(ia);
			}
			case LONG_ARRAY -> {
				long[] la = new long[in.readInt()];
				for (int i = 0; i < la.length; i++) la[i] = in.readLong();
				cc.value(la);
			}
		}
	}
}