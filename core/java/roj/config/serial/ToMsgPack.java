package roj.config.serial;

import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Arrays;

import static roj.config.MsgPackParser.*;

/**
 * @author Roj234
 * @since 2025/4/24 18:40
 */
public class ToMsgPack implements CVisitor {
	public static class Compressed extends ToMsgPack {
		public Compressed() {}
		public Compressed(DynByteBuf buf) { super(buf); }

		public final void value(short i) {
			onValue();

			if (i == (byte) i) {
				if ((byte) i >= -32) ob.put((byte) i);
				else ob.put(INT8).put((byte) i);
				return;
			}

			ob.put(INT16).putShort(i);
		}
		public final void value(int i) {
			onValue();

			if ((byte)i == i) {
				if (i >= -32) ob.put((byte) i);
				else ob.put(INT8).put(i);
			} else if ((short)i == i) ob.put(INT16).putShort(i);
			else ob.put(INT32).putInt(i);
		}
		public final void value(long i) {
			if ((int) i == i) {
				value((int) i);
				return;
			}

			onValue();
			ob.put(INT64).putLong(i);
		}
	}

	public ToMsgPack() {}
	public ToMsgPack(DynByteBuf buf) { this.ob = buf; }

	protected DynByteBuf ob;
	public DynByteBuf buffer() { return ob; }
	public ToMsgPack buffer(DynByteBuf buf) { ob = buf; return this; }
	public ToMsgPack setLenientFieldCountHandling(boolean enable) {
		this.ignoreContainerSize = enable;
		return this;
	}

	private boolean ignoreContainerSize;
	private boolean exceptKey = true;

	private byte state;
	private int size;

	private int[] states = new int[4];
	private int stateLen;

	public final void value(boolean b) {onValue();ob.write(b ? TRUE : FALSE);}
	public final void value(byte i) {
		onValue();

		if (i >= -32) ob.put(i);
		else ob.put(INT8).put(i);
	}
	public void value(short i) {onValue();ob.put(INT16).putShort(i);}
	public void value(char i) {onValue();ob.put(UINT16).putShort(i);}
	public void value(int i) {onValue();ob.put(INT32).putInt(i);}
	public void value(long i) {onValue();ob.put(INT64).putLong(i);}
	public final void value(float i) {onValue();ob.put(FLOAT32).putFloat(i);}
	public final void value(double i) {onValue();ob.put(FLOAT64).putDouble(i);}
	public final void value(String s) {
		onValue();
		writeUTF(s);
	}
	private void writeUTF(String s) {
		int len = DynByteBuf.byteCountUTF8(s);

		// 根据长度选择编码方式
		if (len <= 31) {
			ob.put(FIXSTR_PREFIX | len);
		} else if (len <= 0xFF) {
			ob.put(STR8).put(len);
		} else if (len <= 0xFFFF) {
			ob.put(STR16).putShort(len);
		} else {
			ob.put(STR32).putInt(len);
		}

		// prealloc write
		ob.putUTFData0(s, len);
	}
	public final void valueNull() {onValue();ob.write(NULL);}

	public final boolean supportArray() {return true;}
	public void value(byte[] array) {
		onValue();
		int len = array.length;

		if (len <= 0xFF) {
			ob.put(BIN8).put(len);
		} else if (len <= 0xFFFF) {
			ob.put(BIN16).putShort(len);
		} else {
			ob.put(BIN32).putInt(len);
		}
		ob.put(array);
	}
	public final void value(int[] array) {
		onExt(-8, array.length);
		for (int i : array) ob.putInt(i);
	}
	public final void value(long[] array) {
		onExt(-4, array.length);
		for (long i : array) ob.putLong(i);
	}

	public final void valueMap() {
		if (ob.isReal()) ob.put(MAP16).putShort(0); // 占位符，后续填充实际数量
		else onExt(-5, 1); // 不支持随机访问的话就用流式扩展
		push((byte) 1, -1);
	}
	public final void valueMap(int size) {
		if (ignoreContainerSize) {valueMap();return;}

		if (size <= 0xF) {
			ob.put(FIXMAP_PREFIX | size);
		} else if (size < 0xFFFF) {
			ob.put(MAP16).putShort(size);
		} else {
			ob.put(MAP32).putInt(size);
		}
		push((byte) 1, size);
	}
	public final void key(String key) {
		onKey();
		writeUTF(key);
	}
	public final void intKey(int key) {
		onKey();

		if ((byte)key == key) {
			if (key >= -32) ob.put((byte) key);
			else ob.put(INT8).put(key);
		} else if ((short)key == key) ob.put(INT16).putShort(key);
		else ob.put(INT32).putInt(key);
	}

	public final void valueList() {
		if (ob.isReal()) ob.put(ARRAY16).putShort(0); // 占位符
		else onExt(-5, 2); // 不支持随机访问的话就用流式扩展
		push((byte) 2, -1);
	}
	public final void valueList(int size) {
		if (ignoreContainerSize) {valueList();return;}

		if (size <= 0xF) {
			ob.put(FIXARRAY_PREFIX | size);
		} else if (size < 0xFFFF) {
			ob.put(ARRAY16).putShort(size);
		} else {
			ob.put(ARRAY32).putInt(size);
		}
		push((byte) 2, size);
	}

	// 辅助方法：保存容器状态
	private void push(byte type, int count/* positive or 0+negative */) {
		onValue();

		int[] arr = states;
		if (arr.length <= stateLen+3) states = arr = Arrays.copyOf(arr, stateLen+3);

		arr[stateLen++] = size;
		if (count < 0) {
			arr[stateLen++] = state | 4;
			arr[stateLen++] = ob.wIndex() - 2;
		} else {
			arr[stateLen++] = state;
			arr[stateLen++] = count;
		}

		state = type;
		size = 0;
	}

	public final void pop() {
		if (!exceptKey) throw new IllegalStateException("期待值|pop");
		if (stateLen == 0) throw new IllegalStateException("Stack underflow");

		int prevData  = states[--stateLen];
		int prevState = states[--stateLen];

		if ((prevState & 4) != 0) {
			if (ob.isReal()) ob.putShort(prevData, size);
			else ob.put(STREAM);
		} else {
			if (size != prevData) throw new IllegalStateException("预期大小("+prevData+"),实际大小("+size+")");
		}

		state = (byte) (prevState&3);
		size = states[--stateLen];
	}

	// public visibility for ext
	public final void onKey() {
		if (state != 1 || !exceptKey) throw new IllegalStateException("期待值|key|"+state);
		exceptKey = false;
	}
	public final void onValue() {
		if (state == 0) return;
		size++;
		if (state == 1) {
			if (exceptKey) throw new IllegalStateException("期待键");
			exceptKey = true;
		}
	}
	public final DynByteBuf onExt(int type, int length) {
		if (length <= 16 && Integer.lowestOneBit(length) == length) {
			int i = FIXEXT_PREFIX + 31 - Integer.numberOfLeadingZeros(length);
			ob.put(i);
		} else if (length <= 0xFF) {
			ob.put(EXT8).put(length);
		} else if (length <= 0xFFFF) {
			ob.put(EXT16).putShort(length);
		} else {
			ob.put(EXT32).putInt(length);
		}
		return ob.put(type);
	}

	@Override
	public final void valueTimestamp(long mills) {
		long seconds = mills / 1000;
		valueTimestamp(seconds, (int)(mills - seconds * 1000) * 1000000);
	}

	@Override
	public final void valueTimestamp(long seconds, int nanos) {
		onValue();
		if ((seconds >>> 34) == 0) {
			long data64 = ((long)nanos << 34) | seconds;
			if ((data64 & 0xffffffff00000000L) == 0) {
				ob.putShort(0xD6FF).putInt((int) data64);
			} else {
				ob.putShort(0xD7FF).putLong(data64);
			}
		} else {
			ob.putMedium(0xC712FF).putInt(nanos).putLong(seconds);
		}
	}

	public ToMsgPack reset() {
		state = 0;
		stateLen = 0;
		exceptKey = true;
		return this;
	}

	@Override
	public void close() throws IOException { if (ob != null) ob.close(); }
}