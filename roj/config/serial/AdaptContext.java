package roj.config.serial;

import roj.collect.IntBiMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

/**
 * @author Roj234
 * @since 2023/3/19 0019 19:16
 */
final class AdaptContext implements CAdapter<Object> {
	private final Adapter root;
	Adapter curr;

	private State stack, bin;

	static final class State {
		MyBitSet fieldStateExtra;
		int fieldState;
		int fieldId;
		Object ref;
		String serCtx;
		Adapter owner;

		State stk;

		State() {}
		State push(AdaptContext ctx) {
			this.fieldId = ctx.fieldId;
			this.fieldState = ctx.fieldState;
			this.fieldStateExtra = ctx.fieldStateEx;

			this.ref = ctx.ref;
			this.serCtx = ctx.serCtx;

			this.owner = ctx.curr;

			this.stk = ctx.stack;

			ctx.fieldId = -2;
			ctx.fieldState = 0;
			ctx.fieldStateEx = null;

			ctx.ref = null;
			ctx.serCtx = null;
			return this;
		}
		void pop(AdaptContext ctx) {
			ctx.fieldId = fieldId;
			ctx.fieldState = fieldState;
			ctx.fieldStateEx = fieldStateExtra;

			ctx.ref = ref;
			ctx.serCtx = serCtx;

			ctx.curr = owner;

			ctx.stack = stk;
		}

		@Override
		public String toString() {
			return "{" + owner.getClass().getSimpleName() + "@" + Integer.toBinaryString(fieldState)+":"+fieldId+", " + ref + '}';
		}
	}

	public MyBitSet fieldStateEx;
	int fieldState;

	int fieldId;

	Object ref;
	String serCtx;

	public AdaptContext(Adapter root) {
		this.root = root;
		if (root.fieldCount() > 32) fieldStateEx = new MyBitSet(root.fieldCount()-32);
		reset();
	}

	public final void value(boolean l) {curr.read(this,l);}
	public final void value(int l) {curr.read(this,l);}
	public final void value(long l) {curr.read(this,l);}
	public final void value(float l) {curr.read(this,l);}
	public final void value(double l) {curr.read(this,l);}
	public final void value(String l) {curr.read(this,l);}
	public final void valueNull() {
		if (fieldId == -2) popd(true);
		else curr.read(this, (Object) null);
	}
	public final void value(byte[] ba) {curr.read(this,ba);}
	public final void value(int[] ia) {curr.read(this,ia);}
	public final void value(long[] la) {curr.read(this,la);}

	public final void valueMap() {curr.map(this,-1);}
	public final void valueMap(int size) {curr.map(this,size);}
	public final void valueList() {curr.list(this,-1);}
	public final void valueList(int size) {curr.list(this,size);}

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
		int remain = curr.fieldCount() - Integer.bitCount(fst) + (fieldStateEx == null ? 0 : fieldStateEx.size());

		if (remain != 0) {
			if (!(curr instanceof GenAdapter)) throw new IllegalStateException(curr+"缺少"+remain+"个字段");

			CharList sb = new CharList().append(curr).append("缺少这些字段:");
			IntBiMap<String> fieldId = ((GenAdapter) curr).fieldNames();
			for (int i = 0; i < curr.fieldCount(); i++) {
				boolean has = i < 32 ? (fst & (1<<i)) != 0 : fieldStateEx.contains(i);
				if (!has) sb.append(fieldId.get(i)).append(", ");
			}
			throw new IllegalStateException(sb.toString());
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
			if ((fieldState&bit) != 0) throw new IllegalStateException("字段 "+id+" 已存在");
			fieldState |= bit;
		} else {
			if (!fieldStateEx.add(id-32)) throw new IllegalStateException("字段 "+id+" 已存在");
		}
	}
	public final void setKeyHook(int id) {
		if (id < 0) throw new IllegalStateException("未知的字段ID");
		if (fieldId != -1) throw new IllegalStateException("期待值而非名称");
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

		if (s.fieldCount() > 32) fieldStateEx = new MyBitSet(s.fieldCount()-32);
		curr = s;
		s.push(this);
	}

	private ByteList buf;
	final DynByteBuf buffer() {
		if (buf == null) buf = new ByteList(128);
		else buf.clear();
		return buf;
	}
	private SimpleList<SimpleList<Object>> lists;
	final SimpleList<Object> objBuffer() {
		if (lists == null) lists = new SimpleList<>();
		else if (!lists.isEmpty()) {
			SimpleList<Object> b = lists.pop();
			b.clear();
			return b;
		}
		return new SimpleList<>();
	}
	final void releaseBuffer(SimpleList<?> list) {
		list.clear();
		lists.add(Helpers.cast(list));
	}

	private boolean finished;
	public final Object result() {
		if (!finished) throw new IllegalStateException("Not finished");
		return ref;
	}
	public final boolean finished() { return finished; }
	public final void reset() {
		fieldId = -2;
		fieldState = 0;
		if (fieldStateEx != null) fieldStateEx.clear();

		ref = null;
		serCtx = null;

		stack = null;

		finished = false;
		curr = root;
	}

	public final void write(CVisitor c, Object o) { root.write(c, o); }
}
