package roj.config.serial;

import roj.collect.SimpleList;
import roj.config.data.*;
import roj.config.exch.*;

import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2022/11/15 0015 2:07
 */
public class ToEntry implements CVisitor {
	private final List<CEntry> stack = new SimpleList<>();
	private CEntry stackTop, stackBottom;

	private String key;

	private int[] states = new int[1];
	private byte state;

	private int maxDepth = 100;

	public void setMaxDepth(int maxDepth) {
		this.maxDepth = maxDepth;
	}

	public final void value(byte l) { add(TByte.valueOf(l)); }
	public final void value(short l) { add(TShort.valueOf(l)); }
	public final void value(float l) { add(TFloat.valueOf(l)); }
	public final void value(String l) { add(l==null?CNull.NULL:CString.valueOf(l)); }
	public final void value(int l) { add(CInteger.valueOf(l)); }
	public final void value(long l) { add(CLong.valueOf(l)); }
	public final void value(double l) { add(CDouble.valueOf(l));}
	public final void value(boolean l) { add(l ? CBoolean.TRUE : CBoolean.FALSE); }
	public final void valueNull() { add(CNull.NULL); }
	public final void value(int[] ia) { add(new TIntArray(ia)); }
	public final void value(byte[] ba) { add(new TByteArray(ba)); }
	public final void value(long[] la) { add(new TLongArray(la)); }

	private void add(CEntry v) {
		switch (state) {
			case 0:
				stackTop = v;
				if (!v.getType().isContainer()) {
					stack.add(v);
					state = 1;
				}
				break;
			case 1: throw new IllegalStateException("Container " + stack.get(stack.size()-1).getType() + " is not appendable");
			case 2:
				if (key == null) throw new IllegalStateException("Key missing for " + v);
				stackBottom.asMap().put(key, v);
				key = null;
				break;
			case 3: stackBottom.asList().add(v); break;
		}
	}

	public final void key(String key) {
		if (state != 2) throw new IllegalStateException("bottom is " + stackBottom.getType() + " not MAP");
		if (this.key != null) throw new IllegalStateException("Duplicate key " + key + " and " + this.key);
		this.key = key;
	}

	public final void valueList() { push(new CList(), 3); }
	public final void valueList(int size) { push(new CList(size), 3); }

	public final void valueMap() { push(new CMapping(), 2); }
	public final void valueMap(int size) { push(new CMapping(size), 2); }

	private void push(CEntry e, int state) {
		add(e);

		int level = stack.size();
		if (level == maxDepth) throw new IllegalStateException("Max object depth "+maxDepth+" exceeded");
		stack.add(stackBottom = e);
		this.state = (byte) state;

		int i = level >>> 4;
		int lsh = (level & 15) << 1;

		int[] arr = states;
		if (arr.length <= i) states = arr = Arrays.copyOf(arr, i+1);

		arr[i] = (arr[i] & ~(3 << lsh)) | (state << lsh);
	}
	public final void pop() {
		if (key != null) throw new IllegalStateException("Value missing for " + key);
		if (stack.size() <= 1) {
			if (stack.isEmpty()) throw new IllegalStateException("Stack underflow");
			stack.remove(0);
			return;
		}
		CEntry entry = stack.remove(stack.size()-1);
		stackBottom = stack.get(stack.size()-1);

		int depth = stack.size()-1;
		int i = depth >>> 4;
		int lsh = (depth & 15) << 1;

		state = (byte) ((states[i] >>> lsh) & 3);
	}

	public final CEntry get() {
		return stackTop;
	}

	public final ToEntry reset() {
		stack.clear();
		stackTop = stackBottom = null;
		state = 0;
		key = null;
		return this;
	}
}
