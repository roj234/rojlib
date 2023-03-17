package roj.text.pattern;

import roj.collect.MyBitSet;
import roj.math.MutableInt;
import roj.text.CharList;

import java.util.Map;

/**
 * @author Roj234
 * @since 2022/10/23 0023 4:26
 */
public final class Set extends MyPattern {
	MyBitSet val;

	public Set(String val) {
		this.val = MyBitSet.from(val);
	}

	@Override
	public boolean match(CharSequence s, MutableInt i, Map<String, String> ctx, CharList output) {
		return false;
	}

	@Override
	public String toString() {
		return "Chars("+val+")";
	}
}
