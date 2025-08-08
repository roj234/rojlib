package roj.config.auto;

import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.collect.IntBiMap;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

/**
 * @author Roj234
 * @since 2023/3/19 19:16
 */
class AdaptContext implements Serializer<Object> {
	private final Adapter root;
	Adapter curr;

	private State stack, bin;

	static final class State {
		BitSet fieldStateExtra;
		int fieldState;
		int fieldId;
		Object ref, ref2;
		Adapter owner;

		State stk;

		State() {}
		State push(AdaptContext ctx) {
			this.fieldId = ctx.fieldId;
			this.fieldState = ctx.fieldState;
			this.fieldStateExtra = ctx.fieldStateEx;

			this.ref = ctx.ref;
			this.ref2 = ctx.ref2;

			this.owner = ctx.curr;

			this.stk = ctx.stack;

			ctx.fieldId = -2;
			ctx.fieldState = 0;
			ctx.fieldStateEx = null;

			ctx.ref = null;
			ctx.ref2 = null;
			return this;
		}
		void pop(AdaptContext ctx) {
			ctx.fieldId = fieldId;
			ctx.fieldState = fieldState;
			ctx.fieldStateEx = fieldStateExtra;

			ctx.ref = ref;
			ctx.ref2 = ref2;

			ctx.curr = owner;

			ctx.stack = stk;
		}

		@Override
		public String toString() {
			return "{" + owner.getClass().getSimpleName() + "@" + Integer.toBinaryString(fieldState)+":"+fieldId+", " + ref + '}';
		}
	}

	public BitSet fieldStateEx;
	int fieldState;

	int fieldId;

	Object ref, ref2;

	public AdaptContext(Adapter root, boolean allowDeser) {
		this.root = root;
		if (!allowDeser) root = Adapter.NO_DESERIALIZE;
		this.curr = root;
		if (root.fieldCount() > 32) fieldStateEx = new BitSet(root.fieldCount()-32);

		fieldId = -2;
	}

	void setRef(Object o) {ref = o;}

	public final void value(boolean b) {curr.read(this, b);}
	public final void value(int i) {curr.read(this, i);}
	public final void value(long i) {curr.read(this, i);}
	public final void value(float i) {curr.read(this, i);}
	public final void value(double i) {curr.read(this, i);}
	public final void value(String s) {curr.read(this, s);}
	public final void valueNull() {curr.read(this, (Object) null);}
	public final boolean supportArray() {return true;}
	public final void value(byte[] array) {curr.read(this, array);}
	public final void value(int[] array) {curr.read(this, array);}
	public final void value(long[] array) {curr.read(this, array);}

	public final void valueMap() {curr.map(this,-1);}
	public final void valueMap(int size) {curr.map(this,size);}
	public final void valueList() {curr.list(this,-1);}
	public final void valueList(int size) {curr.list(this,size);}

	public final void intKey(int key) {curr.key(this,key);}
	public final void key(String key) {curr.key(this,key);}
	public final void pop() {
		while (fieldId == -2)
			popd(false);

		// hint
		if (curr.fieldCount() < 0) {
			popd(false);
			return;
		}
		curr.pop(this);

		int fst = curr.plusOptional(fieldState, fieldStateEx);
		int remain = curr.fieldCount() - Integer.bitCount(fst) - (fieldStateEx == null ? 0 : fieldStateEx.size());

		if (remain != 0) {
			if (!(curr instanceof GA)) throw new IllegalStateException(curr+"缺少"+remain+"个字段");

			CharList sb = new CharList().append(curr).append("缺少这些字段:");
			IntBiMap<String> fieldId = ((GA) curr).fn();
			for (int i = 0; i < curr.fieldCount(); i++) {
				boolean has = i < 32 ? (fst & (1<<i)) != 0 : fieldStateEx.contains(i-32);
				if (!has) sb.append(fieldId.get(i)).append(", ");
			}
			throw new IllegalStateException(sb.toStringAndFree());
		}
		popd(true);
	}
	// pop direct (not checking field count)
	final void popd(boolean sendVal) {
		if (stack == null) {
			finished = true;
			return;
		}

		Object obj = ref;
		State prev = stack;
		prev.pop(this);

		// linked list
		prev.stk = bin;
		bin = prev;

		if (sendVal) curr.read(this, obj);
	}

	@Override
	public String toString() {
		return "AdaptContext{" + "Stack=" + stack + ", State=" + fieldState + ", Id=" + fieldId + ", Ref=" + ref + '}';
	}

	public final void setFieldHook() {
		int id = fieldId;
		if (id < 0) throw new IllegalStateException("期待名称而非值");
		fieldId = -1;

		if (id < 32) {
			int bit = 1<<id;
			if ((fieldState&bit) != 0) throwDupField(id);
			fieldState |= bit;
		} else {
			if (!fieldStateEx.add(id-32)) throwDupField(id);
		}
	}
	private void throwDupField(int id) {throw new IllegalStateException("字段 "+fieldName(id)+" 已存在, state="+fieldState);}
	private String fieldName(int fieldId) {return curr instanceof GA ga ? ga.fn().get(fieldId) : "0x"+Integer.toHexString(fieldId);}
	public final void ofIllegalType(Adapter adapter) {throw new IllegalStateException(adapter+"("+ref+")的字段"+fieldName(fieldId)+"的类型不兼容！");}

	public final void setKeyHook(int id) {
		if (id < 0) throw new IllegalStateException("未知的字段ID");
		if (fieldId != -1) {
			if (!(curr instanceof GA)) throw new IllegalStateException("在设置字段 "+fieldId+" 时设置 "+id);
			IntBiMap<String> map = ((GA) curr).fn();
			throw new IllegalStateException("在设置字段 "+map.get(fieldId)+" 时设置 "+map.get(id));
		}
		fieldId = id;
	}
	public final void pushHook(int id, Adapter d1) {
		setKeyHook(id);
		push(d1);
	}
	public final void push(Adapter s) {
		State state = bin;
		if (state == null) state = new State();
		else bin = bin.stk;

		stack = state.push(this);

		replace(s);
		s.push(this);
	}
	public final void replace(Adapter s) {
		if (s.fieldCount() > 32) fieldStateEx = new BitSet(s.fieldCount()-32);
		curr = s;
	}

	private ByteList buf;
	final DynByteBuf buffer() {
		if (buf == null) buf = new ByteList(128);
		else buf.clear();
		return buf;
	}
	private ArrayList<ArrayList<Object>> lists;
	final ArrayList<Object> objBuffer() {
		if (lists == null) lists = new ArrayList<>();
		else if (!lists.isEmpty()) {
			ArrayList<Object> b = lists.pop();
			b.clear();
			return b;
		}
		return new ArrayList<>();
	}
	final void releaseBuffer(ArrayList<?> list) {
		list.clear();
		lists.add(Helpers.cast(list));
	}

	private boolean finished;
	public final Object get() {
		if (!finished) throw new IllegalStateException("Not finished");
		return ref;
	}
	public final boolean finished() { return finished; }
	public Serializer<Object> reset() {
		fieldId = -2;
		fieldState = 0;
		if (fieldStateEx != null) fieldStateEx.clear();

		ref = null;
		ref2 = null;

		stack = null;

		finished = false;
		if (curr != Adapter.NO_DESERIALIZE) curr = root;
		return this;
	}

	public void write(CVisitor c, Object o) { root.write(c, o); }
}