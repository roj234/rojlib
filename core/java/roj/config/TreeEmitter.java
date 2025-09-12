package roj.config;

import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.config.node.*;
import roj.util.TypedKey;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/15 2:07
 */
public class TreeEmitter implements ValueEmitter {
	/**
	 * Enable or disable comment
	 */
	public static final TypedKey<Boolean> COMMENT = new TypedKey<>("toEntry:comment");

	private final ArrayList<ConfigValue> stack = new ArrayList<>();
	private ConfigValue stackTop, stackBottom;

	private String key;

	private int[] states = new int[1];
	private byte state;

	private int maxDepth = 100;
	private byte flag;

	public final void emit(boolean b) { add(ConfigValue.valueOf(b)); }
	public final void emit(byte i) { add(ConfigValue.valueOf(i)); }
	public final void emit(short i) { add(ConfigValue.valueOf(i)); }
	public final void emit(int i) { add(ConfigValue.valueOf(i)); }
	public final void emit(long i) { add(ConfigValue.valueOf(i)); }
	public final void emit(float i) { add(ConfigValue.valueOf(i)); }
	public final void emit(double i) { add(ConfigValue.valueOf(i));}
	public final void emit(String s) { add(ConfigValue.valueOf(s)); }
	public final void emitNull() { add(NullValue.NULL); }
	public final void emitDate(long millis) {add(new DateValue(millis));}
	public final void emitTimestamp(long millis) {add(new TimestampValue(millis));}
	public final boolean supportArray() {return true;}
	public final void emit(byte[] array) { add(new ByteArrayValue(array)); }
	public final void emit(int[] array) { add(new IntArrayValue(array)); }
	public final void emit(long[] array) { add(new LongArrayValue(array)); }

	private void add(ConfigValue v) {
		if (pendingComment != null) {
			if (stackBottom.getType() == Type.MAP) {
				MapValue map = stackBottom.asMap().toCommentable();
				stackBottom = map;
				map.setComment(key, pendingComment);
			} else if (stackBottom.getType() == Type.LIST) {
				ListValue list = stackBottom.asList().toCommentable();
				stackBottom = list;
				list.setComment(list.size(), pendingComment);
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
		this.key = key.intern();
	}

	public final void emitList() { push(new ListValue(), 3); }
	public final void emitList(int size) { push(new ListValue(size), 3); }

	public final void emitMap() { push(createMap(8), 2); }
	public final void emitMap(int size) { push(createMap(size), 2); }
	private MapValue createMap(int size) {
		HashMap<String, ConfigValue> core = (flag & 1) == 0 ? new HashMap<>(size) : new LinkedHashMap<>(size);
		return new MapValue(core);
	}

	private String pendingComment;
	public void comment(String comment) { if ((flag&2) != 0) pendingComment = comment; }

	private void push(ConfigValue e, int state) {
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
	public final ConfigValue get() {
		if (!stack.isEmpty()) throw new IllegalStateException("栈不是空的，请先pop");
		else if (stackTop == null) throw new IllegalStateException("未输入任何值");
		return stackTop;
	}

	@Override
	public <T> void setProperty(TypedKey<T> k, T v) {
		switch (k.name) {
			case "generic:maxDepth" -> maxDepth = (int) v;
			case "generic:orderedMap" -> {
				boolean v1 = ((boolean) v);
				if (v1) flag |= 1;
				else flag &= ~1;
			}
			case "generic:comment" -> {
				boolean v1 = ((boolean) v);
				if (v1) flag |= 2;
				else {
					flag &= ~2;
					pendingComment = null;
				}
			}
		}
	}

	public final TreeEmitter reset() {
		stack.clear();
		stackTop = stackBottom = null;
		state = 0;
		key = null;
		return this;
	}
}