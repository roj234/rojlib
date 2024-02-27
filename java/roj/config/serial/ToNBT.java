package roj.config.serial;

import roj.compiler.plugins.asm.ASM;
import roj.text.GB18030;
import roj.text.J9String;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Arrays;

import static roj.config.NBTParser.*;
import static roj.config.XNBTParser.*;

/**
 * @author Roj234
 * @since 2023/3/27 22:38
 */
public class ToNBT implements CVisitor {
	private boolean XNbt = false;

	public ToNBT() {}
	public ToNBT(DynByteBuf buf) { this.ob = buf; }

	public ToNBT setXNbt(boolean XNBT_CHECK) {this.XNbt = XNBT_CHECK;return this;}

	private DynByteBuf ob;
	public DynByteBuf buffer() { return ob; }
	public ToNBT buffer(DynByteBuf buf) { ob = buf; return this; }

	private String key;

	private byte state = -1;
	private int sizeOffset, size;

	private int[] states = new int[4];
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
	public final void value(String l) {
		if (XNbt) {
			if (l == null) {valueNull();return;}
			if (ASM.TARGET_JAVA_VERSION > 8 && J9String.isLatin1(l)) {
				onValue(X_LATIN1_STRING);
				ob.putVUInt(l.length()).putAscii(l);
				return;
			}

			int utfExtra = l.length() * 2 / 3;
			int numCn = 0;
			for (int i = 0; i < l.length(); i++) {
				if (GB18030.isTwoByte(l.charAt(i))) {
					if (++numCn > utfExtra) {
						onValue(X_GB18030_STRING);
						ob.putVUIGB(l);
						return;
					}
				}
			}
		}

		onValue(STRING);
		ob.writeUTF(l);
	}
	public final void valueNull() {
		if (!XNbt) throw new NullPointerException("NBT不支持Null (请使用XNBT)");
		onValue(X_NULL);
	}
	public final boolean supportArray() {return true;}
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

	public final void onValue(byte type) {
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
				sizeOffset = ob.put(XNbt ? 0 : type).wIndex();
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
				if (XNbt) ob.putShort(type);
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

	public final void valueList() { valueList(-1); }
	public final void valueList(int size) {
		onValue(LIST);
		push(2);
		this.size = size < 0 ? 0 : -size;
		this.sizeOffset = 0;
	}

	public final void valueMap() {
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
			case 1 -> ob.write(END);
			case 2 -> {
				if (sizeOffset > 0) {
					ob.putInt(sizeOffset, size);
				} else {
					if (size != 0) throw new IllegalStateException("距离预定的LIST大小还有"+ -size +"个项目");
					// 空列表
					if (sizeOffset == 0) ob.put(END).writeInt(0);
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

	public final ToNBT reset() {
		state = -1;
		stateLen = 0;
		size = 0;
		sizeOffset = 0;
		key = null;
		return this;
	}

	@Override
	public void close() throws IOException { if (ob != null) ob.close(); }
}