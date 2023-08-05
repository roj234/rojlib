package roj.config.serial;

import roj.util.DynByteBuf;

import java.util.Arrays;

import static roj.config.NBTParser.*;

/**
 * @author Roj234
 * @since 2023/3/27 22:38
 */
public class ToNBT implements CVisitor {
	public ToNBT(DynByteBuf buf) {
		this.ob = buf;
		this.state = -1;
	}

	private DynByteBuf ob;
	public DynByteBuf buffer() { return ob; }
	public ToNBT buffer(DynByteBuf buf) { ob = buf; return this; }

	private String key;

	private byte state;
	private int sizeOffset, size;

	private long[] states = new long[4];
	private int stateLen;

	public final void value(boolean l) { value((byte)(l?1:0)); }
	public final void value(byte l) {
		onValue(BYTE);
		ob.write(l);
	}
	public final void value(short l) {
		onValue(SHORT);
		ob.writeShort(l);
	}
	public final void value(int l) {
		onValue(INT);
		ob.writeInt(l);
	}
	public final void value(String l) {
		onValue(STRING);
		ob.writeUTF(l);
	}
	public final void value(long l) {
		onValue(LONG);
		ob.writeLong(l);
	}
	public final void value(float l) {
		onValue(FLOAT);
		ob.writeFloat(l);
	}
	public final void value(double l) {
		onValue(DOUBLE);
		ob.writeDouble(l);
	}
	public final void valueNull() { throw new NullPointerException("NBT不支持Null"); }
	public final void value(byte[] ba) {
		onValue(BYTE_ARRAY);
		ob.writeInt(ba.length);
		ob.write(ba);
	}
	public final void value(int[] ia) {
		onValue(INT_ARRAY);
		ob.writeInt(ia.length);
		for (int i : ia) ob.writeInt(i);
	}
	public final void value(long[] la) {
		onValue(LONG_ARRAY);
		ob.writeInt(la.length);
		for (long l : la) ob.writeLong(l);
	}

	private void onValue(byte type) {
		switch (state) {
			case -1:
				//if (type != COMPOUND) throw new IllegalStateException("NBT开头必须是COMPOUND");
				ob.put(type).writeShort(0);
				state = (byte) (type == COMPOUND ? 1 : type == LIST ? 2 : 0);
				break;
			case 0: return;
			case 1:
				if (key == null) throw new IllegalStateException("期待键|"+type);
				ob.put(type).writeUTF(key);
				key = null;
				break;
			case 2:
				state = (byte) (type+2);
				sizeOffset = ob.put(type).wIndex();
				if (size < 0) {
					ob.writeInt(-size);
					sizeOffset = -1;
					size++;
				} else {
					ob.writeInt(0);
					size = 1;
				}
				break;
			default:
				if (state != type+2) throw new IllegalStateException("NBT列表的每项类型必须相同/at="+stateLen+":"+size);
				size++;
				break;
		}
	}

	public final void key(String key) {
		if (state != 1) throw new IllegalStateException("状态不是COMPOUND");
		if (this.key != null) throw new IllegalStateException("期待"+this.key+"的值|"+key);
		this.key = key;
	}

	public final void valueList() { valueList(-1); }
	public final void valueList(int size) {
		onValue(LIST);
		push(2);
		this.size = size < 0 ? 0 : -size;
	}

	public final void valueMap() {
		onValue(COMPOUND);
		push(1);
	}

	private void push(int state1) {
		int depth = stateLen++;

		long[] arr = states;
		if (arr.length <= depth) states = arr = Arrays.copyOf(arr, depth+1);

		if (size > 0xFFFFFFFL) throw new IllegalStateException("天哪你这是哪家的列表");

		arr[depth] = ((long) state << 60) | ((long) sizeOffset << 28) | (size &0xFFFFFFFL);

		state = (byte) state1;
		size = 0;
	}
	public final void pop() {
		if (key != null) throw new IllegalStateException("期待"+key+"的值|pop");
		if (stateLen == 0) throw new IllegalStateException("Stack underflow");

		// map
		if (state == 0) throw new IllegalStateException("未预料的pop");
		else if (state == 1) ob.write(END);
		else {
			// has content
			if (sizeOffset > 0) {
				ob.putInt(sizeOffset, size);
			} else if (sizeOffset < 0) {
				if (size != 0) throw new IllegalStateException("距离预定的LIST大小还有"+ -size +"个项目");
				// pre-sized
			} else if (size == 0) {
				// empty
				ob.put(END).writeInt(0);
			}
		}

		long data = states[--stateLen];

		state = (byte) (data >>> 60);
		sizeOffset = (int) (data >>> 28);
		size = (int) data;
	}

	public final void reset() {
		state = -1;
		stateLen = 0;
		size = 0;
		sizeOffset = 0;
		key = null;
	}
}
