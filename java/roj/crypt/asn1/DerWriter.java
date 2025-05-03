package roj.crypt.asn1;

import roj.collect.IntList;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.reflect.Unaligned;
import roj.util.*;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * 不像java的DerOutputStream把byte[]拷来拷去，这个（大概）高性能的实现选择至多拷贝一次
 * @author Roj234
 * @since 2024/3/25 3:22
 */
public final class DerWriter {
	private final ByteList T = new ByteList();
	private final IntList L = new IntList();
	private final SimpleList<Object> V = new SimpleList<>();
	private final IntList stack = new IntList();
	//private final byte[] TLPool = new byte[512];

	public void begin(int type) {
		T.put(type);
		L.add(0);
		V.add(null);
		stack.add(T.wIndex());
	}

	public void end() {
		int prev = stack.pop();
		// T.wIndex() - prev 是type的额外长度
		int total = (T.wIndex() - prev);
		SimpleList<Object> acc = new SimpleList<>();

		// NEVER copy byte[]
		for (int i = prev; i < T.wIndex(); i++) {
			int length = L.get(i);
			int metalen = getLength(length);

			total += length + metalen;

			ByteList typeAndLength = new ByteList(metalen+1).put(T.get(i));
			writeLength(metalen, typeAndLength, length);
			acc.add(typeAndLength);

			Object value = V.get(i);
			if (value instanceof SimpleList<?> list) acc.addAll(list);
			else acc.add(value);
		}

		V.removeRange(prev, T.wIndex());
		T.wIndex(prev);
		L.setSize(prev);

		L.set(prev-1, total);
		V.set(prev-1, acc);
	}

	public void sort() {
		int prev = stack.get(stack.size()-1);
		TimSortForEveryone.sort(prev, T.wIndex(), (refLeft, offLeft, offRight) -> {
			int objectIdL = Unaligned.U.getInt(refLeft, offLeft);
			int objectIdR = Unaligned.U.getInt(offRight);

			ByteList dataLeft = combine(L.get(objectIdL));
			ByteList dataRight = combine(L.get(objectIdR));
			return Arrays.compare(dataLeft.list, dataLeft.relativeArrayOffset(), dataLeft.readableBytes(), dataRight.list, dataRight.relativeArrayOffset(), dataRight.readableBytes());
		}, NativeArray.objectArray(V.getInternalArray()), NativeArray.primitiveArray(T.list), NativeArray.primitiveArray(L.getRawArray()));
	}
	private static ByteList combine(Object o) {
		if (o instanceof ByteList bl) return bl;
		SimpleList<ByteList> o1 = Helpers.cast(o);
		ByteList tmp = new ByteList();
		for (int i = 0; i < o1.size(); i++) tmp.put(o1.get(i));
		return tmp;
	}

	private static int getLength(int length) {
		int metalen;
		if (length <= 0x7F) {
			metalen = 1;
		} else if (length <= 0xFF) {
			metalen = 2;
		} else if (length <= 0xFFFF) {
			metalen = 3;
		} else if (length <= 0xFFFFFF) {
			metalen = 4;
		} else if (length <= 0xFFFFFFFFL) {
			metalen = 5;
		} else {
			throw new UnsupportedOperationException("length too large");
		}
		return metalen;
	}

	private static void writeLength(int metalen, DynByteBuf typeAndLength, int length) {
		switch (metalen) {
			case 1 -> typeAndLength.put(length);
			case 2 -> typeAndLength.put(0x81).put(length);
			case 3 -> typeAndLength.put(0x82).putShort(length);
			case 4 -> typeAndLength.put(0x83).putMedium(length);
			case 5 -> typeAndLength.put(0x84).putInt(length);
		}
	}

	public void writeInt(BigInteger data) {write(DerValue.INTEGER, data.toByteArray());}
	public void writeBits(byte[] data, int bits) {
		assert ((bits - (data.length << 3)) & ~7) == 0 : "invalid bit count "+bits+" for length["+data.length+"]";

		DynByteBuf buf = IOUtil.getSharedByteBuf().put((8 - (bits&7)) & 7).put(data);
		write(DerValue.BIT_STRING, buf.toByteArray());
	}
	public void writeBytes(byte[] data) {write(DerValue.OCTET_STRING, data);}
	public void writeOid(int... oid) {
		int first = oid[0]*40 + oid[1];
		var buf = IOUtil.getSharedByteBuf();
		writeDerOidEntry(buf, first);
		for (int i = 2; i < oid.length; i++) writeDerOidEntry(buf, oid[i]);
		write(DerValue.OID, buf.toByteArray());
	}
	private final byte[] tmp = new byte[5];
	private void writeDerOidEntry(DynByteBuf buf, int i) {
		var bits = tmp;
		int j = 5;
		do {
			bits[--j] = (byte) ((i & 0x7F) | 0x80);
			i >>>= 7;
		} while (i != 0);
		bits[4] &= 0x7F;
		buf.put(bits, j, 5-j);
	}
	public void writeIso(int type, String iso) {write(type, IOUtil.getSharedByteBuf().putAscii(iso).toByteArray());}
	public void writeUTF(String utf) {write(DerValue.UTF8_STRING, IOUtil.getSharedByteBuf().putUTFData(utf).toByteArray());}
	public void writeNull() {write(DerValue.NULL, ByteList.EMPTY);}

	public void write(int type, byte[] data) {write(type, DynByteBuf.wrap(data));}
	/**
	 * 写入一个DerTag的内容，类型和长度手动提供
	 */
	public void write(int type, DynByteBuf data) {
		T.put(type);
		L.add(data.readableBytes());
		V.add(data);
	}

	/**
	 * 写入一个完整的DerTag，类型和长度都已包含在data中
	 */
	public void write(DynByteBuf data) {
		T.put(data.readByte());
		try {
			L.add(DerReader.readLength1(data));
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		V.add(data);
	}

	/**
	 * 将根DerTag写入out
	 */
	public void flush(DynByteBuf out) {
		if (stack.size() != 0) throw new IllegalStateException("stack depth != 0");
		for (int i = 0; i < T.wIndex(); i++) {
			out.put(T.get(i));

			int len = L.get(i);
			writeLength(getLength(len), out, len);

			Object value = V.get(i);
			if (value instanceof DynByteBuf buf) out.put(buf);
			else {
				SimpleList<DynByteBuf> list = Helpers.cast(value);
				for (DynByteBuf buf : list) out.put(buf);
			}
		}
	}
}