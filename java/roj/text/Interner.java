package roj.text;

import roj.collect.LFUCache;

import static roj.collect.IntMap.UNDEFINED;

/**
 * 字符串intern缓存，目前仅为非Visitor模式的mapping key启用，来防止无意义的对于常量intern池的污染
 * Visitor模式可能也需要对于value执行此操作，但不是必须的
 * 目前使用LFU缓存，也许LRU是更好的选择
 * @author Roj234
 * @since 2024/7/16 0016 1:13
 */
public final class Interner extends LFUCache<CharSequence,String> {
	private static final ThreadLocal<Interner> instances = ThreadLocal.withInitial(Interner::new);

	private Interner() {super(256, 1);}

	public static String intern(CharSequence input) {return input.length() > 127 ? input.toString() : instances.get().doIntern(input);}
	public static String intern(CharSequence input, int start, int end) {return instances.get().doIntern(input.subSequence(start, end));}

	// 可能有人正在进行拒绝服务攻击
	private int treeifyCounter;
	@Override
	protected boolean acceptTreeNode() {++treeifyCounter;return false;}

	private String doIntern(CharSequence input) {
		var entry = getOrCreateEntry(input);
		if (entry.k != UNDEFINED) return entry.getValue();

		if (treeifyCounter >= 10) {
			treeifyCounter = 0;
			clear();
			entry = getOrCreateEntry(input);
		}

		String intern = input.toString().intern();
		entry.k = intern;
		entry.setValue(intern);
		size++;

		return intern;
	}
}