package roj.text.pattern;

import roj.math.MutableInt;
import roj.text.CharList;

import java.util.Map;

/**
 * @author Roj234
 * @since 2022/10/23 0023 4:26
 */
final class Any extends MyPattern {
	Any() {
		length = 1;
	}

	@Override
	boolean match(CharSequence s, MutableInt i, Map<String, String> ctx, CharList out) {
		int len;
		if ((flag & LINE_END) == 0) {
			len = length;
			int avl = s.length() - i.getValue();
			if (avl < len) return false;
			if ((flag & OUT) != 0) out.append(s, i.getValue(), i.getValue() + len);
		} else {
			int pos = nextLine(s, i.getValue());
			if (pos == -1) return false;
			int crlf = pos >>> 30;
			len = (pos & 0x3FFFFFFF) - i.getValue();
			if ((flag & OUT) != 0) out.append(s, i.getValue(), i.getValue() + len - crlf);
		}
		i.add(len);
		return true;
	}

	@Override
	public String toString() {
		return "Any(" + ((flag & LINE_END) == 0 ? length : "\\n") + ")";
	}
}
