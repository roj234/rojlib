package roj.collect;

import roj.math.MutableInt;
import roj.text.ReverseOf;

import java.util.List;
import java.util.Map;

/**
 * 后缀树，TT是前缀树
 *
 * @author Roj233
 * @since 2021/8/13 14:02
 */
public final class SuffixTree<V> extends TrieTree<V> {
	public SuffixTree() {}

	public SuffixTree(Map<CharSequence, V> map) {
		super(map);
	}

	@Override
	public boolean startsWith_R(CharSequence s, int i, int len) {
		return super.startsWith_R(ReverseOf.reverseOf(s, i, len), i, len);
	}

	@Override
	public int match(CharSequence s, int i, int len, MyHashMap.Entry<MutableInt, V> feed) {
		return super.match(ReverseOf.reverseOf(s, i, len), i, len, feed);
	}

	@Override
	public List<Map.Entry<String, V>> entryMatches(CharSequence s, int i, int len, int limit) {
		return super.entryMatches(ReverseOf.reverseOf(s, i, len), i, len, limit);
	}

	@Override
	public List<V> valueMatches(CharSequence s, int i, int len, int limit) {
		return super.valueMatches(ReverseOf.reverseOf(s, i, len), i, len, limit);
	}

	@Override
	Entry<V> entryForPut(CharSequence s, int i, int len) {
		return super.entryForPut(ReverseOf.reverseOf(s, i, len), i, len);
	}

	@Override
	Entry<V> getEntry1(CharSequence s, int i, int len, int kind) {
		return super.getEntry1(ReverseOf.reverseOf(s, i, len), i, len, kind);
	}

	@Override
	public V remove(CharSequence s, int i, int len, Object except) {
		return super.remove(ReverseOf.reverseOf(s, i, len), i, len, except);
	}

}
