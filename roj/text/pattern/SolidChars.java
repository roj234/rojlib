package roj.text.pattern;

import roj.math.MutableInt;
import roj.text.CharList;

import java.util.Map;

/**
 * @author Roj234
 * @since 2022/10/23 0023 4:25
 */
final class SolidChars extends MyPattern {
	private final String val;

	SolidChars(String val) {
		this.val = val;
		this.length = val.length();
	}

	@Override
	public boolean match(CharSequence s, MutableInt i, Map<String, String> ctx, CharList output) {
		int avl = s.length() - i.getValue();
		if (avl < length) return false;

		int off = i.getValue();
		if ((flag & DEOBF) != 0) {
			for (int j = 0; j < val.length(); j++) {
				if (deobfuscate(s.charAt(off++)) != val.charAt(j)) return false;
			}
		} else {
			for (int j = 0; j < val.length(); j++) {
				if (s.charAt(off++) != val.charAt(j)) return false;
			}
		}
		i.add(length);
		return true;
	}

	@Override
	public String toString() {
		return "Solid(" + val + ")";
	}
}
