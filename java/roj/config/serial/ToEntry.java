package roj.config.serial;

import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.data.*;
import roj.text.Interner;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/15 0015 2:07
 */
public class ToEntry implements CVisitor {
	private final SimpleList<CEntry> stack = new SimpleList<>();
	private CEntry stackTop, stackBottom;

	private String key;

	private int[] states = new int[1];
	private byte state;

	private int maxDepth = 100;
	private byte flag;

	public final void value(boolean l) { add(CEntry.valueOf(l)); }
	public final void value(byte l) { add(CEntry.valueOf(l)); }
	public final void value(short l) { add(CEntry.valueOf(l)); }
	public final void value(int l) { add(CEntry.valueOf(l)); }
	public final void value(long l) { add(CEntry.valueOf(l)); }
	public final void value(float l) { add(CEntry.valueOf(l)); }
	public final void value(double l) { add(CEntry.valueOf(l));}
	public final void value(String l) { add(CEntry.valueOf(l)); }
	public final void valueNull() { add(CNull.NULL); }
	public final void valueDate(long value) {add(new CDate(value));}
	public final void valueTimestamp(long value) {add(new CTimestamp(value));}
	public final boolean supportArray() {return true;}
	public final void value(byte[] ba) { add(new CByteArray(ba)); }
	public final void value(int[] ia) { add(new CIntArray(ia)); }
	public final void value(long[] la) { add(new CLongArray(la)); }

	private void add(CEntry v) {
		if (pendingComment != null) {
			if (stackBottom.getType() == Type.MAP) {
				CMap map = stackBottom.asMap().withComments();
				stackBottom = map;
				map.putComment(key, pendingComment);
			} else if (stackBottom.getType() == Type.LIST) {
				CList list = stackBottom.asList().withComments();
				stackBottom = list;
				list.putComment(list.size(), pendingComment);
			}
			pendingComment = null;
		}
		switch (state) {
			case 0:
				stackTop = v;
				if (!v.getType().isContainer()) state = 1;
				break;
			case 1: throw new IllegalStateException(stack.getLast().getType()+" 不是容器类型");
			case 2:
				if (key == null) throw new IllegalStateException("映射缺少键: "+v);
				stackBottom.asMap().put(key, v);
				key = null;
				break;
			case 3: stackBottom.asList().add(v); break;
		}
	}

	public final void key(String key) {
		if (state != 2) throw new IllegalStateException("栈顶不是映射: "+stackBottom.getType());
		if (this.key != null) throw new IllegalStateException("映射缺少值: 在键 "+this.key+" 后立即输入了键 "+key);
		this.key = Interner.intern(key);
	}

	public final void valueList() { push(new CList(), 3); }
	public final void valueList(int size) { push(new CList(size), 3); }

	public final void valueMap() { push(createMap(8), 2); }
	public final void valueMap(int size) { push(createMap(size), 2); }
	private CMap createMap(int size) {
		MyHashMap<String, CEntry> core = (flag & 1) == 0 ? new MyHashMap<>(size) : new LinkedMyHashMap<>(size);
		return new CMap(core);
	}

	private String pendingComment;
	public void comment(String comment) { if ((flag&2) != 0) pendingComment = comment; }

	private void push(CEntry e, int state) {
		add(e);

		int level = stack.size();
		if (level == maxDepth) throw new IllegalStateException("栈溢出: 最大深度是 "+maxDepth);
		stack.add(stackBottom = e);
		this.state = (byte) state;

		int i = level >>> 4;
		int lsh = (level & 15) << 1;

		int[] arr = states;
		if (arr.length <= i) states = arr = Arrays.copyOf(arr, i+1);

		arr[i] = (arr[i] & ~(3 << lsh)) | (state << lsh);
	}
	public final void pop() {
		if (key != null) throw new IllegalStateException("映射缺少值: 在键 "+this.key+" 后未输入值");
		if (stack.size() <= 1) {
			if (stack.isEmpty()) throw new IllegalStateException("栈是空的");
			stack.remove(0);
			return;
		}
		stack.pop();
		stackBottom = stack.getLast();

		int depth = stack.size()-1;
		int i = depth >>> 4;
		int lsh = (depth & 15) << 1;

		state = (byte) ((states[i] >>> lsh) & 3);
	}

	public final boolean isEnded() {return stack.isEmpty() && stackTop != null;}
	public final CEntry get() {
		if (!stack.isEmpty()) throw new IllegalStateException("栈不是空的，请先pop");
		else if (stackTop == null) throw new IllegalStateException("未输入任何值");
		return stackTop;
	}

	@Override
	public void setProperty(String k, Object v) {
		switch (k) {
			case "entry:max_depth" -> maxDepth = (int) v;
			case "centry:linked_map" -> {
				boolean v1 = ((boolean) v);
				if (v1) flag |= 1;
				else flag &= ~1;
			}
			case "centry:comment" -> {
				boolean v1 = ((boolean) v);
				if (v1) flag |= 2;
				else {
					flag &= ~2;
					pendingComment = null;
				}
			}
		}
	}

	public final ToEntry reset() {
		stack.clear();
		stackTop = stackBottom = null;
		state = 0;
		key = null;
		return this;
	}
}