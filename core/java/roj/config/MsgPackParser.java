package roj.config;

import roj.io.ByteInput;
import roj.io.ByteInputStream;
import roj.io.CorruptedInputException;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import static roj.reflect.Unsafe.U;

/**
 * @author Roj234
 * @since 2025/4/24 15:50
 */
public class MsgPackParser implements BinaryParser {
	public static final int EXT_STREAM_DATA = 0x00;

	@Override
	public final void parse(InputStream in, int flag, ValueEmitter emitter) throws IOException {parse(ByteInputStream.wrap(in), emitter);}
	public final void parse(DynByteBuf buf, int flag, ValueEmitter emitter) throws IOException {parse(buf, emitter);}

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

	public final void parse(ByteInput in, ValueEmitter out) throws IOException {parse(in, out, in.readUnsignedByte());}
	private void parse(ByteInput in, ValueEmitter out, int tagByte) throws IOException {
		switch (LOOKUP[tagByte]&0xFF) {
			// [\x80 - \xBF}范围是安全的
			default      -> out.emit((byte) tagByte);					// fixInt [\x00 - \x7F] | [\xE0 - \xFF]
			case NULL    -> out.emitNull();
			case STREAM  -> throw new CorruptedInputException("意外的流终止记号");
			case FALSE   -> out.emit(false);
			case TRUE    -> out.emit(true);

			case BIN8    -> out.emit(in.readBytes(in.readUnsignedByte()));
			case BIN16   -> out.emit(in.readBytes(in.readUnsignedShort()));
			case BIN32   -> out.emit(readBytes(in, readInt(in)));

			case FIXEXT_PREFIX, 0xD5, 0xD6, 0xD7, 0xD8 ->				// fixExt 1 - 16
					ext(in, out, 1 << (tagByte - FIXEXT_PREFIX));
			case EXT8    -> ext(in, out, in.readUnsignedByte());
			case EXT16   -> ext(in, out, in.readUnsignedShort());
			case EXT32   -> ext(in, out, readInt(in));

			case FLOAT32 -> out.emit(in.readFloat());
			case FLOAT64 -> out.emit(in.readDouble());
			case UINT8   -> out.emit(in.readUnsignedByte());
			case UINT16  -> out.emit(in.readChar());
			case UINT32  -> out.emit(in.readUnsignedInt());
			case UINT64  -> out.emit(readLong(in));
			case INT8    -> out.emit(in.readByte());
			case INT16   -> out.emit(in.readShort());
			case INT32   -> out.emit(in.readInt());
			case INT64   -> out.emit(in.readLong());

			case FAKE_FIXSTR -> out.emit(in.readUTF(tagByte & 0x1F));
			case STR8    -> out.emit(in.readUTF(in.readUnsignedByte()));
			case STR16   -> out.emit(in.readUTF(in.readUnsignedShort()));
			case STR32   -> out.emit(readUTF(in, readInt(in)));

			case FAKE_FIXARR -> list(in, out, tagByte&0x0F);
			case ARRAY16 -> list(in, out, in.readUnsignedShort());
			case ARRAY32 -> list(in, out, readInt(in));

			case FAKE_FIXMAP -> map(in, out, tagByte&0x0F);
			case MAP16   -> map(in, out, in.readUnsignedShort());
			case MAP32   -> map(in, out, readInt(in));
		}
	}
	private static int readInt(ByteInput in) throws IOException {
		int len = in.readInt();
		if (len < 0) throw new IOException("数据范围超出Java限制！ 0x"+Integer.toHexString(len));
		return len;
	}
	private static long readLong(ByteInput in) throws IOException {
		long len = in.readLong();
		if (len < 0) throw new IOException("数据范围超出Java限制！ 0x"+Long.toHexString(len));
		return len;
	}
	private static byte[] readBytes(ByteInput in, int len) throws IOException {
		if (len <= 0xFFFF || in instanceof DynByteBuf) return in.readBytes(len);

		var buf = new ByteList();
		int r = buf.readStream((ByteInputStream) in, len);
		if (r < len) throw new EOFException("没有 "+len+" 字节可用");
		byte[] v = buf.toByteArray();
		buf.release();

		return v;
	}
	private static String readUTF(ByteInput in, int len) throws IOException {
		if (len <= 0xFFFF || in instanceof DynByteBuf) return in.readUTF(len);

		var buf = new ByteList();
		int r = buf.readStream((ByteInputStream) in, len);
		if (r < len) throw new EOFException("没有 "+len+" 字节可用");
		String v = buf.readUTF(buf.readableBytes());
		buf.release();
		return v;
	}

	private void list(ByteInput in, ValueEmitter out, int size) throws IOException {
		out.emitList(size);
		for (int i = 0; i < size; i++) parse(in, out);
		out.pop();
	}
	private void map(ByteInput in, ValueEmitter out, int size) throws IOException {
		out.emitMap(size);
		for (int i = 0; i < size; i++) {
			mapKey(in, out);
			parse(in, out);
		}
		out.pop();
	}
	private static void mapKey(ByteInput in, ValueEmitter out) throws IOException {mapKey(in, out, in.readUnsignedByte());}
	private static void mapKey(ByteInput in, ValueEmitter out, int tagByte) throws IOException {
		switch (LOOKUP[tagByte]&0xFF) {
			case UINT8   -> out.emitKey(in.readUnsignedByte());
			case UINT16  -> out.emitKey(in.readChar());
			case INT8    -> out.emitKey(in.readByte());
			case INT16   -> out.emitKey(in.readShort());
			case INT32   -> out.emitKey(in.readInt());

			case FAKE_FIXSTR -> out.emitKey(in.readUTF(tagByte & 0x1F));
			case STR8 -> out.emitKey(in.readUTF(in.readUnsignedByte()));
			case STR16 -> out.emitKey(in.readUTF(in.readUnsignedShort()));
			case STR32 -> out.emitKey(readUTF(in, readInt(in)));

			default -> {
				if (tagByte > 0x7F && tagByte <= 0xDF) throw new IOException("键必须是字符串或整数: 0x"+Integer.toHexString(tagByte));
				out.emitKey((byte)tagByte);
			}
		}
	}

	protected void ext(ByteInput in, ValueEmitter visitor, int dataLen) throws IOException {
		int extType = in.readByte();
		switch (extType) {
			case -1  -> timestamp(in, visitor, dataLen);  // 0b111 (reserved for array type)
			case -2  -> vlongArray(in, visitor, dataLen); // 0b110
			case -3  -> ulongArray(in, visitor, dataLen); // 0b101
			case -4  -> longArray(in, visitor, dataLen);  // 0b100
			case -5  -> stream(in, visitor, dataLen);	  // 0b011 (reserved for array type)
			case -6  -> vintArray(in, visitor, dataLen);  // 0b010
			case -7  -> uintArray(in, visitor, dataLen);  // 0b001
			case -8  -> intArray(in, visitor, dataLen);   // 0b000
			case -9  -> dedupData(in, visitor, dataLen);
			case -10 -> dedupRef(in, visitor, dataLen);
			case -11 -> predefinedMap(in, visitor, dataLen);
			default -> throw new CorruptedInputException("不支持的自定义类型: 0x"+Integer.toHexString(extType));
		}
	}

	private void timestamp(ByteInput in, ValueEmitter out, int dataLen) throws IOException {
		switch (dataLen) {
			case 4 -> out.emitTimestamp(in.readUnsignedInt() * 1000L); // unix second timestamp
			case 8 -> {
				long data = in.readLong();
				int nanos = (int) (data >>> 34);
				long seconds = data & 0x3ffffffffL;
				out.emitTimestamp(seconds, nanos);
			}
			case 12 -> {
				int nanos = in.readInt();
				long seconds = in.readLong();
				out.emitTimestamp(seconds, nanos);
			}
			default -> throw new CorruptedInputException("时间戳长度错误: "+dataLen);
		}
	}
	//Proposal: support dynamic length arrays and maps for data streaming
	//https://github.com/msgpack/msgpack/issues/270
	private void stream(ByteInput in, ValueEmitter out, int dataLen) throws IOException {
		if (dataLen == 1) { // 流式映射 (使用场景较多)
			out.emitMap();
			while (true) {
				int tagByte = in.readUnsignedByte();
				if (tagByte == STREAM) break;
				mapKey(in, out, tagByte);
				parse(in, out);
			}
			out.pop();
		} else if (dataLen == 2) { // 流式数组 (使用场景狭窄)
			out.emitList();
			while (true) {
				int tagByte = in.readUnsignedByte();
				if (tagByte == STREAM) break;
				parse(in, out, tagByte);
			}
			out.pop();
		} else {
			throw new CorruptedInputException("数据错误："+dataLen);
		}
	}
	private void intArray(ByteInput in, ValueEmitter visitor, int dataLen) throws IOException {
		int[] array = (int[]) U.allocateUninitializedArray(int.class, dataLen);
		for (int i = 0; i < array.length; i++) array[i] = in.readInt();
		visitor.emit(array);
	}
	private void uintArray(ByteInput in, ValueEmitter visitor, int dataLen) throws IOException {
		int[] array = (int[]) U.allocateUninitializedArray(int.class, dataLen);
		for (int i = 0; i < array.length; i++) array[i] = in.readVUInt();
		visitor.emit(array);
	}
	private void vintArray(ByteInput in, ValueEmitter visitor, int dataLen) throws IOException {
		int[] array = (int[]) U.allocateUninitializedArray(int.class, dataLen);
		for (int i = 0; i < array.length; i++) array[i] = ByteInput.zag(in.readVUInt());
		visitor.emit(array);
	}
	private void longArray(ByteInput in, ValueEmitter visitor, int dataLen) throws IOException {
		long[] array = (long[]) U.allocateUninitializedArray(long.class, dataLen);
		for (int i = 0; i < array.length; i++) array[i] = in.readLong();
		visitor.emit(array);
	}
	private void ulongArray(ByteInput in, ValueEmitter visitor, int dataLen) throws IOException {
		long[] array = (long[]) U.allocateUninitializedArray(long.class, dataLen);
		for (int i = 0; i < array.length; i++) array[i] = in.readVULong();
		visitor.emit(array);
	}
	private void vlongArray(ByteInput in, ValueEmitter visitor, int dataLen) throws IOException {
		long[] array = (long[]) U.allocateUninitializedArray(long.class, dataLen);
		for (int i = 0; i < array.length; i++) array[i] = ByteInput.zag(in.readVULong());
		visitor.emit(array);
	}

	private byte[][] objectPool;
	// Proposal: Addition of 4 Predefined Extension Types to MessagePack to improve on-demand forward reading and storage efficiency
	//https://github.com/msgpack/msgpack/issues/330
	private void dedupData(ByteInput in, ValueEmitter out, int dataLen) throws IOException {
		// Deduplication Array Container
		objectPool = new byte[dataLen][];
		for (int i = 0; i < dataLen; i++) {
			objectPool[i] = readBytes(in, readArrayIndex(in));
		}
	}
	private void dedupRef(ByteInput in, ValueEmitter out, int index) throws IOException {
		// Deduplication Reference
		parse(DynByteBuf.wrap(objectPool[index]), out);
	}
	private void predefinedMap(ByteInput in, ValueEmitter out, int index) throws IOException {
		// Predefined Map
		var keys = DynByteBuf.wrap(objectPool[index]);
		out.emitMap(keys.readUnsignedByte());
		// 按顺序填充数据
		while (keys.isReadable()) {
			int tagByte = in.readUnsignedByte();
			if (tagByte == STREAM) break;
			mapKey(keys, out, tagByte);
			parse(in, out);
		}
		// 缺失的值填充为null
		while (keys.isReadable()) {
			parse(keys, out);
			out.emitNull();
		}
		out.pop();
	}

	private static int readArrayIndex(ByteInput in) throws IOException {return Math.toIntExact(in.readVULong());}
}