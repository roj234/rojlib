package roj.crypt.asn1;

import roj.collect.IntList;
import roj.collect.SimpleList;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/3/25 0025 3:22
 */
public class DerWriter {
	private final ByteList T = new ByteList();
	private final IntList L = new IntList();
	private final SimpleList<Object> V = new SimpleList<>();
	private final IntList stack = new IntList();

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

		T.wIndex(prev);
		L.setSize(prev);
		V.removeRange(prev, T.wIndex());

		L.set(prev-1, total);
		V.set(prev-1, acc);
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

	public void write(int type, DynByteBuf data) {
		T.put(type);
		L.add(data.readableBytes());
		V.add(data);
	}

	public void write(DynByteBuf data) {
		T.put(data.readByte());
		// TODO readDerLength
		L.add(data.readableBytes());
		V.add(data);
	}

	public void flush(DynByteBuf out) throws IOException {
		if (stack.size() != 0) throw new IOException("stack depth != 0");
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