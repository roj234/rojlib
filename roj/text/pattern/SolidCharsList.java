package roj.text.pattern;

import roj.collect.TrieTreeSet;
import roj.math.MutableInt;
import roj.text.CharList;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2022/10/23 0023 4:26
 */
final class SolidCharsList extends MyPattern {
	private final TrieTreeSet val;

	SolidCharsList(List<String> list) {
		val = new TrieTreeSet();
		val.addAll(list);
	}

	@Override
	public boolean match(CharSequence s, MutableInt i, Map<String, String> ctx, CharList output) {
		int match = val.longestMatches(s, i.getValue(), s.length());
		if (match <= 0) return false;
		i.add(match);
		return true;
	}

	@Override
	public String toString() {
		return "Solids("+val+")";
	}
}
