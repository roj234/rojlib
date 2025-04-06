package roj.config;

import roj.config.serial.CVisitor;
import roj.io.CorruptedInputException;
import roj.io.MyDataInput;
import roj.io.MyDataInputStream;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2025/4/24 15:50
 */
public class MsgPackParser implements BinaryParser {
	@Override
	public final <T extends CVisitor> T parse(InputStream in, int flag, T out) throws IOException { element(MyDataInputStream.wrap(in), out); return out; }
	public final <T extends CVisitor> T parse(DynByteBuf buf, int flag, T out) throws IOException { element(buf, out); return out; }

	public final ConfigMaster format() { return ConfigMaster.MSGPACK; }

	private static final byte[] LOOKUP = new byte[256];
	private static final int FAKE_FIXMAP = 0xBD, FAKE_FIXARR = 0xBE, FAKE_FIXSTR = 0xBF;
	public static final int
			FIXMAP_PREFIX = 0x80,
			FIXARRAY_PREFIX = 0x90,
			FIXSTR_PREFIX = 0xA0,
			NULL = 0xC0,
			STREAM = 0xC1, // 扩展类型
			FALSE = 0xC2,
			TRUE = 0xC3,
			BIN8 = 0xC4,
			BIN16 = 0xC5,
			BIN32 = 0xC6,
			EXT8 = 0xC7,
			EXT16 = 0xC8,
			EXT32 = 0xC9,
			FLOAT32 = 0xCA,
			FLOAT64 = 0xCB,
			UINT8 = 0xCC,
			UINT16 = 0xCD,
			UINT32 = 0xCE,
			UINT64 = 0xCF,
			INT8 = 0xD0,
			INT16 = 0xD1,
			INT32 = 0xD2,
			INT64 = 0xD3,
			FIXEXT_PREFIX = 0xD4,
			STR8 = 0xD9,
			STR16 = 0xDA,
			STR32 = 0xDB,
			ARRAY16 = 0xDC,
			ARRAY32 = 0xDD,
			MAP16 = 0xDE,
			MAP32 = 0xDF;

	static {
		for (int i = 0x00; i <= 0x7F; i++) LOOKUP[i] = (byte) i;
		for (int i = 0x80; i <= 0x8F; i++) LOOKUP[i] = (byte) FAKE_FIXMAP;
		for (int i = 0x90; i <= 0x9F; i++) LOOKUP[i] = (byte) FAKE_FIXARR;
		for (int i = 0xA0; i <= 0xBF; i++) LOOKUP[i] = (byte) FAKE_FIXSTR;
		for (int i = 0xC0; i <= 0xFF; i++) LOOKUP[i] = (byte) i;
	}
	public final void element(MyDataInput in, CVisitor out) throws IOException {
		int tagByte = in.readUnsignedByte();
		switch (LOOKUP[tagByte]&0xFF) {
			// [\x80 - \xBF}范围是安全的
			default      -> out.value((byte) tagByte);					// fixInt [\x00 - \x7F] | [\xE0 - \xFF]
			case NULL    -> out.valueNull();
			case STREAM  -> stream(in, out);
			case FALSE   -> out.value(false);
			case TRUE    -> out.value(true);

			case BIN8    -> out.value(in.readBytes(in.readUnsignedByte()));
			case BIN16   -> out.value(in.readBytes(in.readUnsignedShort()));
			case BIN32   -> out.value(readBytes(in, readInt(in)));

			case FIXEXT_PREFIX, 0xD5, 0xD6, 0xD7, 0xD8 ->				// fixExt 1 - 16
					custom(in, out, 1 << (tagByte - FIXEXT_PREFIX));
			case EXT8    -> custom(in, out, in.readUnsignedByte());
			case EXT16   -> custom(in, out, in.readUnsignedShort());
			case EXT32   -> custom(in, out, readInt(in));

			case FLOAT32 -> out.value(in.readFloat());
			case FLOAT64 -> out.value(in.readDouble());
			case UINT8   -> out.value(in.readUnsignedByte());
			case UINT16  -> out.value(in.readChar());
			case UINT32  -> out.value(in.readUInt());
			case UINT64  -> out.value(readLong(in));
			case INT8    -> out.value(in.readByte());
			case INT16   -> out.value(in.readShort());
			case INT32   -> out.value(in.readInt());
			case INT64   -> out.value(in.readLong());

			case FAKE_FIXSTR -> out.value(in.readUTF(tagByte & 0x1F));
			case STR8    -> out.value(in.readUTF(in.readUnsignedByte()));
			case STR16   -> out.value(in.readUTF(in.readUnsignedShort()));
			case STR32   -> out.value(readUTF(in, readInt(in)));

			case FAKE_FIXARR -> list(in, out, tagByte&0x0F);
			case ARRAY16 -> list(in, out, in.readUnsignedShort());
			case ARRAY32 -> list(in, out, readInt(in));

			case FAKE_FIXMAP -> map(in, out, tagByte&0x0F);
			case MAP16   -> map(in, out, in.readUnsignedShort());
			case MAP32   -> map(in, out, readInt(in));
		}
	}
	private static int readInt(MyDataInput in) throws IOException {
		int len = in.readInt();
		if (len < 0) throw new IOException("数据长度超出Java限制！ 0x"+Integer.toHexString(len));
		return len;
	}
	private static long readLong(MyDataInput in) throws IOException {
		long len = in.readLong();
		if (len < 0) throw new IOException("数据长度超出Java限制！ 0x"+Long.toHexString(len));
		return len;
	}
	private static byte[] readBytes(MyDataInput in, int len) throws IOException {
		if (len <= 0xFFFF || in instanceof DynByteBuf) return in.readBytes(len);

		var buf = new ByteList();
		int r = buf.readStream((MyDataInputStream) in, len);
		if (r < len) throw new EOFException("没有 "+len+" 字节可用");
		byte[] v = buf.toByteArray();
		buf._free();

		return v;
	}
	private static String readUTF(MyDataInput in, int len) throws IOException {
		if (len <= 0xFFFF || in instanceof DynByteBuf) return in.readUTF(len);

		var buf = new ByteList();
		int r = buf.readStream((MyDataInputStream) in, len);
		if (r < len) throw new EOFException("没有 "+len+" 字节可用");
		String v = buf.readUTF(buf.readableBytes());
		buf._free();
		return v;
	}

	private byte[][] objectPool;
	//Proposal: support dynamic length arrays and maps for data streaming
	//https://github.com/msgpack/msgpack/issues/270
	private void stream(MyDataInput in, CVisitor out) throws IOException {
		int tag = in.readUnsignedByte();
		switch (tag) {
			// 结构终止 0xC1 0x00
			case 0x00 -> throw new CorruptedInputException("非法的StreamEnd");
			// 流式数组 0xC1 0x01
			case 0x01 -> {
				out.valueList();
				while (!streamEnd(in)) {
					element(in, out);
				}
				out.pop();
			}
			// 流式映射 0xC1 0x02
			case 0x02 -> {
				out.valueMap();
				while (!streamEnd(in)) {
					mapKey(in, out);
					element(in, out);
				}
				out.pop();
			}

			// Proposal: Addition of 4 Predefined Extension Types to MessagePack to improve on-demand forward reading and storage efficiency
			//https://github.com/msgpack/msgpack/issues/330
			// Deduplication Array Container
			case 0x03 -> {
				int size = readArrayIndex(in);
				objectPool = new byte[size][];
				for (int i = 0; i < size; i++) {
					objectPool[i] = readBytes(in, readArrayIndex(in));
				}
			}
			// Deduplication Reference
			// * maybe parsed if CEntry?
			case 0x04 -> {
				int ptr = readArrayIndex(in);
				element(DynByteBuf.wrap(objectPool[ptr]), out);
			}
			// Predefined Map
			case 0x05 -> {
				int ptr = readArrayIndex(in);
				var keys = DynByteBuf.wrap(objectPool[ptr]);
				out.valueMap(keys.readUnsignedByte());
				// fill ordered key (might be string?)
				while (keys.isReadable() && !streamEnd(in)) {
					mapKey(keys, out);
					element(in, out);
				}
				// rest is nil
				while (keys.isReadable()) {
					element(keys, out);
					out.valueNull();
				}
				out.pop();
			}

			// NDArray
			/*case 0x06 -> {
				int dimensions = in.readUnsignedByte();
				int[] dimensionSizes = new int[dimensions];
				long elementCount = 1;
				for (int i = 0; i < dimensions; i++) {
					int count = readArrayIndex(in);
					dimensionSizes[i] = count;
					elementCount = Math.multiplyExact(elementCount, count);
				}
				out.ndByteArray(in, dimensionSizes, elementCount);
			}*/
		}
	}
	private static boolean streamEnd(MyDataInput in) throws IOException {
		in.mark(2);
		if (in.readUnsignedShort() == 0xC100) return true;
		in.unread(2);
		return false;
	}

	private void list(MyDataInput in, CVisitor out, int size) throws IOException {
		out.valueList(size);
		for (int i = 0; i < size; i++) element(in, out);
		out.pop();
	}
	private void map(MyDataInput in, CVisitor out, int size) throws IOException {
		out.valueMap(size);
		for (int i = 0; i < size; i++) {
			mapKey(in, out);
			element(in, out);
		}
		out.pop();
	}
	private static void mapKey(MyDataInput in, CVisitor out) throws IOException {
		int tagByte = in.readUnsignedByte();
		switch (LOOKUP[tagByte]&0xFF) {
			case UINT8   -> out.intKey(in.readUnsignedByte());
			case UINT16  -> out.intKey(in.readChar());
			case INT8    -> out.intKey(in.readByte());
			case INT16   -> out.intKey(in.readShort());
			case INT32   -> out.intKey(in.readInt());

			case FAKE_FIXSTR -> out.key(in.readUTF(tagByte & 0x1F));
			case STR8 -> out.key(in.readUTF(in.readUnsignedByte()));
			case STR16 -> out.key(in.readUTF(in.readUnsignedShort()));
			case STR32 -> out.key(readUTF(in, readInt(in)));

			default -> {
				if (tagByte > 0x7F && tagByte <= 0xDF) throw new IOException("键必须是字符串或整数: 0x"+Integer.toHexString(tagByte));
				out.intKey((byte)tagByte);
			}
		}
	}
	private static int readArrayIndex(MyDataInput in) throws IOException {
		int tagByte = in.readUnsignedByte();
		return switch (tagByte) {
			default   -> tagByte;
			case 0xFD -> in.readUnsignedByte();
			case 0xFE -> in.readUnsignedShort();
			case 0xFF -> in.readInt();
		};
	}

	protected void custom(MyDataInput in, CVisitor visitor, int dataLen) throws IOException {
		int extType = in.readByte();
		switch (extType) {
			case -1 -> timestamp(in, visitor, dataLen);
			case -2 -> uuid(in, visitor, dataLen);
			default ->
				//in.skipBytes(dataLen);
					throw new CorruptedInputException("不支持的自定义类型: 0x" + Integer.toHexString(extType));
		}
	}
	protected final void timestamp(MyDataInput in, CVisitor out, int dataLen) throws IOException {
		switch (dataLen) {
			case 4 -> out.valueTimestamp(in.readUInt() * 1000L); // unix second timestamp
			case 8 -> {
				long data = in.readLong();
				int nanos = (int) (data >>> 34);
				long seconds = data & 0x3ffffffffL;
				out.valueTimestamp(seconds, nanos);
			}
			case 12 -> {
				int nanos = in.readInt();
				long seconds = in.readLong();
				out.valueTimestamp(seconds, nanos);
			}
			default -> throw new IOException("时间戳长度错误: "+dataLen);
		}
	}
	protected final void uuid(MyDataInput in, CVisitor out, int dataLen) throws IOException {
		if (dataLen != 16) throw new IOException("时间戳长度错误: " + dataLen);
		out.valueUUID(in.readLong(), in.readLong());
	}
}