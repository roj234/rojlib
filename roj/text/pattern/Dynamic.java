package roj.text.pattern;

import roj.math.MutableInt;
import roj.text.CharList;

import java.util.Map;

/**
 * @author Roj234
 * @since 2022/10/23 0023 4:26
 */
final class Dynamic extends MyPattern {
	private final String id;

	Dynamic(String id) {
		this.id = id;
		this.length = 1;
	}

	@Override
	boolean match(CharSequence s, MutableInt i, Map<String, String> ctx, CharList out) {
		String result = ctx.get(id);
		int avl = s.length() - i.getValue();

		if (result == null) {
			if (avl < length) return false;
			CharSequence seq = s.subSequence(i.getValue(), i.getValue() + length);
			if ((flag & DEOBF) != 0) seq = deobfuscate(seq);
			ctx.put(id, result = seq.toString());
			i.add(length);
		} else {
			if (avl < result.length()) return false;
			int off = i.getAndAdd(result.length());
			if ((flag & DEOBF) != 0) {
				for (int j = 0; j < result.length(); j++) {
					if (deobfuscate(s.charAt(off++)) != result.charAt(j)) return false;
				}
			} else {
				for (int j = 0; j < result.length(); j++) {
					if (s.charAt(off++) != result.charAt(j)) return false;
				}
			}
		}
		if ((flag & OUT) != 0) out.append(result);
		return true;
	}

	@Override
	public String toString() {
		return "Identity(" + id + ")";
	}
}
