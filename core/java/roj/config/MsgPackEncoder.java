package roj.config;

import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Arrays;

import static roj.config.MsgPackParser.*;

/**
 * @author Roj234
 * @since 2025/4/24 18:40
 */
public class MsgPackEncoder implements ValueEmitter {
	public static class Compressed extends MsgPackEncoder {
		public Compressed() {}
		public Compressed(DynByteBuf buf) { super(buf); }

		public final void emit(short i) {
			onValue();

			if (i == (byte) i) {
				if ((byte) i >= -32) out.put((byte) i);
				else out.put(INT8).put((byte) i);
				return;
			}

			out.put(INT16).putShort(i);
		}
		public final void emit(int i) {
			onValue();

			if ((byte)i == i) {
				if (i >= -32) out.put((byte) i);
				else out.put(INT8).put(i);
			} else if ((short)i == i) out.put(INT16).putShort(i);
			else out.put(INT32).putInt(i);
		}
		public final void emit(long i) {
			if ((int) i == i) {
				this.emit((int) i);
				return;
			}

			onValue();
			out.put(INT64).putLong(i);
		}
	}

	public MsgPackEncoder() {}
	public MsgPackEncoder(DynByteBuf buf) { this.out = buf; }

	protected DynByteBuf out;
	public DynByteBuf buffer() { return out; }
	public MsgPackEncoder buffer(DynByteBuf buf) { out = buf; return this; }
	public MsgPackEncoder setLenientFieldCountHandling(boolean enable) {
		this.ignoreContainerSize = enable;
		return this;
	}

	private boolean ignoreContainerSize;
	private boolean exceptKey = true;

	private byte state;
	private int size;

	private int[] states = new int[4];
	private int stateLen;

	public final void emit(boolean b) {onValue();out.write(b ? TRUE : FALSE);}
	public final void emit(byte i) {
		onValue();

		if (i >= -32) out.put(i);
		else out.put(INT8).put(i);
	}
	public void emit(short i) {onValue();out.put(INT16).putShort(i);}
	public void emit(char i) {onValue();out.put(UINT16).putShort(i);}
	public void emit(int i) {onValue();out.put(INT32).putInt(i);}
	public void emit(long i) {onValue();out.put(INT64).putLong(i);}
	public final void emit(float i) {onValue();out.put(FLOAT32).putFloat(i);}
	public final void emit(double i) {onValue();out.put(FLOAT64).putDouble(i);}
	public final void emit(String s) {
		onValue();
		writeUTF(s);
	}
	private void writeUTF(String s) {
		int len = DynByteBuf.byteCountUTF8(s);

		// 根据长度选择编码方式
		if (len <= 31) {
			out.put(FIXSTR_PREFIX | len);
		} else if (len <= 0xFF) {
			out.put(STR8).put(len);
		} else if (len <= 0xFFFF) {
			out.put(STR16).putShort(len);
		} else {
			out.put(STR32).putInt(len);
		}

		// prealloc write
		out.putUTFData0(s, len);
	}
	public final void emitNull() {onValue();out.write(NULL);}

	public final boolean supportArray() {return true;}
	public void emit(byte[] array) {
		onValue();
		int len = array.length;

		if (len <= 0xFF) {
			out.put(BIN8).put(len);
		} else if (len <= 0xFFFF) {
			out.put(BIN16).putShort(len);
		} else {
			out.put(BIN32).putInt(len);
		}
		out.put(array);
	}
	public final void emit(int[] array) {
		onExt(-8, array.length);
		for (int i : array) out.putInt(i);
	}
	public final void emit(long[] array) {
		onExt(-4, array.length);
		for (long i : array) out.putLong(i);
	}

	public final void emitMap() {
		if (out.isReal()) out.put(MAP16).putShort(0); // 占位符，后续填充实际数量
		else onExt(-5, 1); // 不支持随机访问的话就用流式扩展
		push((byte) 1, -1);
	}
	public final void emitMap(int size) {
		if (ignoreContainerSize) {
			emitMap();return;}

		if (size <= 0xF) {
			out.put(FIXMAP_PREFIX | size);
		} else if (size < 0xFFFF) {
			out.put(MAP16).putShort(size);
		} else {
			out.put(MAP32).putInt(size);
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
			if (key >= -32) out.put((byte) key);
			else out.put(INT8).put(key);
		} else if ((short)key == key) out.put(INT16).putShort(key);
		else out.put(INT32).putInt(key);
	}

	public final void emitList() {
		if (out.isReal()) out.put(ARRAY16).putShort(0); // 占位符
		else onExt(-5, 2); // 不支持随机访问的话就用流式扩展
		push((byte) 2, -1);
	}
	public final void emitList(int size) {
		if (ignoreContainerSize) {
			emitList();return;}

		if (size <= 0xF) {
			out.put(FIXARRAY_PREFIX | size);
		} else if (size < 0xFFFF) {
			out.put(ARRAY16).putShort(size);
		} else {
			out.put(ARRAY32).putInt(size);
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
			arr[stateLen++] = out.wIndex() - 2;
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
			if (out.isReal()) out.putShort(prevData, size);
			else out.put(STREAM);
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
			out.put(i);
		} else if (length <= 0xFF) {
			out.put(EXT8).put(length);
		} else if (length <= 0xFFFF) {
			out.put(EXT16).putShort(length);
		} else {
			out.put(EXT32).putInt(length);
		}
		return out.put(type);
	}

	@Override
	public final void emitTimestamp(long millis) {
		long seconds = millis / 1000;
		emitTimestamp(seconds, (int)(millis - seconds * 1000) * 1000000);
	}

	@Override
	public final void emitTimestamp(long seconds, int nanos) {
		onValue();
		if ((seconds >>> 34) == 0) {
			long data64 = ((long)nanos << 34) | seconds;
			if ((data64 & 0xffffffff00000000L) == 0) {
				out.putShort(0xD6FF).putInt((int) data64);
			} else {
				out.putShort(0xD7FF).putLong(data64);
			}
		} else {
			out.putMedium(0xC712FF).putInt(nanos).putLong(seconds);
		}
	}

	public MsgPackEncoder reset() {
		state = 0;
		stateLen = 0;
		exceptKey = true;
		return this;
	}

	@Override
	public void close() throws IOException { if (out != null) out.close(); }
}