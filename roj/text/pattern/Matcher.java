package roj.text.pattern;

import roj.collect.MyHashMap;
import roj.math.MutableInt;
import roj.text.CharList;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2022/10/23 0023 4:25
 */
public class Matcher {
	final List<MyPattern> patterns;
	Map<String, String> ctx = new MyHashMap<>();
	CharList out = new CharList();
	MutableInt mi = new MutableInt();
	int outCnt;

	public Matcher(List<MyPattern> patterns, int value) {
		this.patterns = patterns;
		this.outCnt = value;
	}

	public Matcher copy() {
		return new Matcher(patterns, outCnt);
	}

	public Object[] match(CharSequence s, int i, Object[] buf) {
		List<MyPattern> patterns = this.patterns;
		Map<String, String> ctx = this.ctx;
		CharList out = this.out; out.clear();
		MutableInt mi = this.mi;

		if (buf == null || buf.length < 3 + outCnt) buf = new Object[3 + outCnt];

		while (i < s.length()) {
			mi.setValue(i);
			check:
			{
				int k = 0;
				for (int j = 0; j < patterns.size(); j++) {
					MyPattern p = patterns.get(j);
					if ((p.flag & MyPattern.LINE_BEGIN) != 0) {
						if (i > 0 && s.charAt(i - 1) != '\n') {
							int pos = MyPattern.nextLine(s, i);
							if (pos == -1) return null;
							i = pos & 0x3FFFFFFF;
						}
					}
					if (!p.match(s, mi, ctx, out)) break check;
					if ((p.flag & MyPattern.OUT) != 0) {
						buf[3 + k++] = out.toString();
						out.clear();
					}
				}

				buf[0] = i;
				buf[1] = mi.getValue();
				buf[2] = new MyHashMap<>(ctx);

				ctx.clear();
				return buf;
			}

			ctx.clear();
			i++;
		}
		return null;
	}

	public void matchAll(CharSequence s, int i, List<Object[]> list) {
		while (i < s.length()) {
			Object[] arr = match(s, i, null);
			if (arr == null) return;
			list.add(arr);
			i = (int) arr[1];
		}
	}

	@Override
	public String toString() {
		return "Matcher{" + "patterns=" + patterns + '}';
	}
}
