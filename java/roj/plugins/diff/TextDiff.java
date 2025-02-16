package roj.plugins.diff;

import roj.collect.SimpleList;
import roj.config.Tokenizer;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * TODO wip
 * @author Roj234
 * @since 2024/3/5 0005 2:49
 */
public class TextDiff {
	public static final byte SAME = 0, CHANGE = 1, INSERT = 2, DELETE = 3;
	public final byte type;
	public final int leftOff, rightOff, len;
	public int advance;

	public static TextDiff link(TextDiff a, TextDiff next) {
		a.next = next;
		next.prev = a;
		return next;
	}

	private TextDiff(byte type, int leftOff, int rightOff, int len) {
		this.type = type;
		this.leftOff = leftOff;
		this.rightOff = rightOff;
		this.len = len;
	}

	public static TextDiff same(int leftOff, int rightOff, int len) { return new TextDiff(SAME, leftOff, rightOff, len); }
	public static TextDiff change(int leftOff, int rightOff, int len) { return new TextDiff(CHANGE, leftOff, rightOff, len); }
	public static TextDiff insert(int rightOff, int len) { return new TextDiff(INSERT, -1, rightOff, len); }
	public static TextDiff delete(int leftOff, int len) { return new TextDiff(DELETE, leftOff, -1, len); }

	TextDiff prev, next;

	public List<TextDiff> getDiff(byte[] right) {
		TextDiff head = TextDiff.insert(0,0), tail = head;



		return toRealDiff(right, head.next);
	}
	private List<TextDiff> toRealDiff(byte[] right, TextDiff in) {
		// todo merge nearby diff and insert SAME diff
		SimpleList<TextDiff> list = new SimpleList<>();

		return list;
	}
	public void toMarkdown(byte[] left, byte[] right, List<TextDiff> diffs, CharList sb) throws IOException {
		Charset cs = Charset.forName("GB18030");

		System.out.println(diffs.size());
		long l = 0;
		for (TextDiff diff : diffs) {
			l += diff.len;
		}
		System.out.println(TextUtil.scaledNumber(l) + "B");

		ByteList buf1 = new ByteList(), buf2 = new ByteList();
		int type = TextDiff.SAME;
		for (TextDiff diff : diffs) {
			if (diff.type != type) {
				finishBlock(sb, buf1, buf2, type, cs);
				type = diff.type;
			}

			switch (diff.type) {
				default: buf1.put(left, diff.leftOff, diff.len); break;
				case TextDiff.CHANGE:
					buf1.put(left, diff.leftOff, diff.len);
					buf2.put(right, diff.rightOff, diff.len);
					break;
				case TextDiff.INSERT: buf1.put(right, diff.rightOff, diff.len); break;
			}
		}

		finishBlock(sb, buf1, buf2, type, cs);
	}
	private static void finishBlock(CharList sb, ByteList buf1, ByteList buf2, int type, Charset cs) throws IOException {
		switch (type) {
			default: case TextDiff.SAME: sb.append(new String(buf1.list, 0, buf1.length(), cs)); break;
			case TextDiff.CHANGE: sb.append("<i title=\"").append(Tokenizer.addSlashes(new String(buf1.list, 0, buf1.length(), cs))).append("\">")
									.append(new String(buf2.list, 0, buf2.length(), cs)).append("</i>"); break;
			case TextDiff.INSERT: sb.append("<b>").append(new String(buf1.list, 0, buf1.length(), cs)).append("</b>"); break;
			case TextDiff.DELETE: sb.append("<del>").append(new String(buf1.list, 0, buf1.length(), cs)).append("</del>"); break;
		}
		buf1.clear();
		buf2.clear();
	}
}