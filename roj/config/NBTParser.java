package roj.config;

import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.config.serial.CVisitor;
import roj.config.serial.ToEntry;
import roj.config.serial.ToNBT;
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
	public static final int DONT_FOLLOW_MINECRAFT = 1;
	public static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

	public static CMapping parse(InputStream is) throws IOException {
		return parse((DataInput) (is instanceof DataInput ? is : new DataInputStream(is)));
	}
	public static CMapping parse(DataInput in) throws IOException {
		ToEntry copy = new ToEntry();
		root(copy, in, 0);
		return copy.get().asMap();
	}
	public static CEntry parseAny(DataInput in) throws IOException {
		ToEntry copy = new ToEntry();
		root(copy, in, DONT_FOLLOW_MINECRAFT);
		return copy.get();
	}

	@Override
	public <T extends CVisitor> T parseRaw(InputStream in, T cc, int flag) throws IOException {
		root(cc, (DataInput) (in instanceof DataInput ? in : new DataInputStream(in)), flag);
		return cc;
	}
	public <T extends CVisitor> T parseRaw(DynByteBuf buf, T cc, int flag) throws IOException {
		root(cc, buf, flag);
		return cc;
	}

	public static void root(CVisitor cc, DataInput in, int flag) throws IOException {
		byte type = in.readByte();
		if ((flag & DONT_FOLLOW_MINECRAFT) == 0 && type != COMPOUND) throw new IOException("根据MC的要求,根节点必须是COMPOUND");
		if (type == 0) return;

		char n = in.readChar();
		if (n != 0) throw new IOException("根节点不应该有名称");
		element(cc, in, type);
	}

	private static void element(CVisitor cc, DataInput in, byte type) throws IOException {
		switch (type) {
			case END:
			default: throw new IOException("Corrupted NBT");
			case BYTE: cc.value(in.readByte()); break;
			case SHORT: cc.value(in.readShort()); break;
			case INT: cc.value(in.readInt()); break;
			case LONG: cc.value(in.readLong()); break;
			case FLOAT: cc.value(in.readFloat()); break;
			case DOUBLE: cc.value(in.readDouble()); break;
			case BYTE_ARRAY:
				byte[] ba = new byte[in.readInt()];
				in.readFully(ba);
				cc.value(ba);
				break;
			case STRING: cc.value(in.readUTF()); break;
			case LIST:
				byte listType = in.readByte();
				int len = in.readInt();
				cc.valueList(len);
				while (len-- > 0) element(cc, in, listType);
				cc.pop();
				break;
			case COMPOUND:
				cc.valueMap();
				do {
					byte flg = in.readByte();
					if (flg == 0) break;
					cc.key(in.readUTF());
					element(cc, in, flg);
				} while (true);
				cc.pop();
				break;
			case INT_ARRAY:
				int[] ia = new int[in.readInt()];
				for (int i = 0; i < ia.length; i++) ia[i] = in.readInt();
				cc.value(ia);
				break;
			case LONG_ARRAY:
				long[] la = new long[in.readInt()];
				for (int i = 0; i < la.length; i++) la[i] = in.readLong();
				cc.value(la);
				break;
		}
	}

	public void serialize(CEntry entry, DynByteBuf out) throws IOException {
		if (entry.getNBTType() != COMPOUND) throw new IOException("根据MC的要求,根节点必须是COMPOUND");
		entry.forEachChild(new ToNBT(out));
	}

	public String format() { return "NBT"; }
}
