package roj.ebook;

import roj.collect.Int2IntMap;
import roj.config.auto.Optional;
import roj.util.TreeNodeImpl;
import roj.text.CharList;
import roj.text.ChinaNumeric;

/**
 * @author Roj234
 * @since 2023/6/26 21:29
 */
@Optional
public class Chapter extends TreeNodeImpl<Chapter> {
	private static final Int2IntMap CP_NUMBER = new Int2IntMap();
	static {
		CP_NUMBER.putAll(ChinaNumeric.NUMBER_MAP);
		CP_NUMBER.putInt('十', -2);
		CP_NUMBER.putInt('百', -2);
		CP_NUMBER.putInt('千', -2);
		CP_NUMBER.putInt('万', -2);
		CP_NUMBER.putInt('亿', -2);
		CP_NUMBER.putInt('序', 0);
	}

	public static long parseChapterNo(char[] cbuf, int off, int len) {
		long out = 0;

		int i = off;
		while (i < len) {
			char c = cbuf[i++];
			int num = CP_NUMBER.getOrDefaultInt(c, -1);
			if (num < 0) {
				if (num == -1) return -1;
				return ChinaNumeric.parse(cbuf, off, len);
			}

			out *= 10;
			out += num;
		}

		return out;
	}

	@Deprecated
	public String matches;

	public int start, end;
	public String customName;
	public boolean hasCustomName;

	// retain from matches
	public long index = -1;
	public char type = '章';
	public String name;

	public CharList text;

	public CharSequence getText(CharList base) {return text != null ? text : base.subSequence(start, end);}

	public String toString() {
		String s = "("+index+") "+(customName !=null ? customName : matches)+" | "+(text == null ? end-start : text.length())+"字";
		if (children != null) s += " +"+children.size();
		return s;
	}
}