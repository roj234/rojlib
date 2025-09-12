package roj.config;

import roj.text.FastCharset;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Arrays;

import static roj.config.NbtParser.*;
import static roj.config.NbtParserEx.*;

/**
 * @author Roj234
 * @since 2023/3/27 22:38
 */
public final class NbtEncoder implements ValueEmitter {
	private boolean XNbt = false;

	public NbtEncoder() {}
	public NbtEncoder(DynByteBuf buf) { this.out = buf; }

	public NbtEncoder setXNbt(boolean XNBT_CHECK) {this.XNbt = XNBT_CHECK;return this;}

	private DynByteBuf out;
	public DynByteBuf buffer() { return out; }
	public NbtEncoder buffer(DynByteBuf buf) { out = buf; return this; }

	private String key;

	private byte state = -1;
	private int sizeOffset, size;

	private int[] states = new int[4];
	private int stateLen;

	public final void emit(boolean b) { emit((byte)(b ?1:0)); }
	public final void emit(byte i) {
		onValue(BYTE);
		out.write(i);
	}
	public final void emit(short i) {
		onValue(SHORT);
		out.writeShort(i);
	}
	public final void emit(int i) {
		onValue(INT);
		out.writeInt(i);
	}
	public final void emit(long i) {
		onValue(LONG);
		out.writeLong(i);
	}
	public final void emit(float i) {
		onValue(FLOAT);
		out.writeFloat(i);
	}
	public final void emit(double i) {
		onValue(DOUBLE);
		out.writeDouble(i);
	}
	public final void emit(String s) {
		if (XNbt) {
			if (s == null) {
				emitNull();return;}
			if (TextUtil.isLatin1(s)) {
				onValue(X_LATIN1_STRING);
				out.putVUInt(s.length()).putAscii(s);
				return;
			}

			int utfExtra = s.length() * 2 / 3;
			int numCn = 0;
			for (int i = 0; i < s.length(); i++) {
				if (FastCharset.GB18030().encodeSize(s.charAt(i)) == 2) {
					if (++numCn > utfExtra) {
						onValue(X_GB18030_STRING);
						out.putVUIGB(s);
						return;
					}
				}
			}
		}

		onValue(STRING);
		out.writeUTF(s);
	}
	public final void emitNull() {
		if (!XNbt) throw new NullPointerException("NBT不支持Null (请使用XNBT)");
		onValue(X_NULL);
	}
	public final boolean supportArray() {return true;}
	public final void emit(byte[] array) {
		onValue(BYTE_ARRAY);
		out.writeInt(array.length);
		out.write(array);
	}
	public final void emit(int[] array) {
		onValue(INT_ARRAY);
		out.writeInt(array.length);
		for (int i : array) out.writeInt(i);
	}
	public final void emit(long[] array) {
		onValue(LONG_ARRAY);
		out.writeInt(array.length);
		for (long l : array) out.writeLong(l);
	}

	@SuppressWarnings("fallthrough")
	public final void onValue(byte type) {
		switch (state) {
			case -1:
				//if (type != COMPOUND) throw new IllegalStateException("NBT开头必须是COMPOUND");
				out.put(type).writeShort(0);
				state = (byte) (type == COMPOUND ? 1 : type == LIST ? 2 : 0);
				break;
			case 0: return;
			case 1:
				if (key == null) throw new IllegalStateException("期待键|"+type);
				out.put(type).writeUTF(key);
				key = null;
				break;
			case 2:
				state = (byte) (type+2);
				sizeOffset = out.put(XNbt ? 0 : type).wIndex();
				if (size < 0) {
					out.writeInt(-size);
					sizeOffset = -1;
				} else {
					out.writeInt(0);
				}
			default:
				if (XNbt) out.put(type);
				else if (state != type+2) throw new IllegalStateException("NBT列表的每项类型必须相同(使用XNbt避免此限制)/at="+stateLen+":"+size+"/type="+(type+2)+",exceptType="+state);
				size++;
				break;
		}
	}

	public final void key(String key) {
		if (state != 1) throw new IllegalStateException("状态不是COMPOUND "+state);
		if (this.key != null) throw new IllegalStateException("期待"+this.key+"的值|"+key);
		this.key = key;
	}

	public final void emitList() { emitList(-1); }
	public final void emitList(int size) {
		onValue(LIST);
		push(2);
		this.size = size < 0 ? 0 : -size;
		this.sizeOffset = 0;
	}

	public final void emitMap() {
		onValue(COMPOUND);
		push(1);
	}

	private void push(int newState) {
		int slot = stateLen++;

		if (state == 2) stateLen += 2;

		int[] arr = states;
		if (arr.length <= slot) states = arr = Arrays.copyOf(arr, stateLen);

		if (state == 2) {
			arr[slot++] = sizeOffset;
			arr[slot++] = size;
		}
		arr[slot] = state;

		state = (byte) newState;
		size = 0;
	}
	public final void pop() {
		if (key != null) throw new IllegalStateException("期待"+key+"的值|pop");
		if (stateLen == 0) throw new IllegalStateException("Stack underflow");

		// map
		switch (state) {
			case 0 -> throw new IllegalStateException("未预料的pop");
			case 1 -> out.write(END);
			case 2 -> {
				if (sizeOffset > 0) {
					out.putInt(sizeOffset, size);
				} else {
					if (size != 0) throw new IllegalStateException("距离预定的LIST大小还有"+ -size +"个项目");
					// 空列表
					if (sizeOffset == 0) out.put(END).writeInt(0);
					// else 预定大小
				}
			}
		}

		int data = states[--stateLen];

		if ((state = (byte) data) == 2) {
			size = states[--stateLen];
			sizeOffset = states[--stateLen];
		} else {
			size = 0;
			sizeOffset = 0;
		}
	}

	public final NbtEncoder reset() {
		state = -1;
		stateLen = 0;
		size = 0;
		sizeOffset = 0;
		key = null;
		return this;
	}

	@Override
	public void close() throws IOException { if (out != null) out.close(); }
}