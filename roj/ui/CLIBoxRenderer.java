package roj.ui;

import roj.collect.IntSet;
import roj.collect.SimpleList;
import roj.text.CharList;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/11/19 0019 15:28
 */
public class CLIBoxRenderer {
	public static final CLIBoxRenderer DEFAULT = new CLIBoxRenderer("┌─┬┐\n│ ├┼┤└┴┘");
	// https://symbl.cc/cn/unicode/blocks/box-drawing/
	public static final CLIBoxRenderer BOLD = new CLIBoxRenderer("┏━┳┓\n┃ ┣╋┫┗┻┛");

	private static final int
		LU = 0, HORIZONTAL_SEPARATOR = 1, TOP_SEPARATOR = 2, RU = 3, LF = 4,
		VERITAL_SEPARATOR = 5, SPACE = 6,
		LEFT_SEPARATOR = 7, ALL_SEPARATOR = 8, RIGHT_SEPARATOR = 9,
		LD = 10, BOTTOM_SEPARATOR = 11, RD = 12;

	private final char[] NPR;
	private final CharList sb = new CharList(256);
	private int[] boxWidth = new int[8];
	private final IntSet boxOffset = new IntSet();
	private int realWidth;

	public CLIBoxRenderer(String npr) {
		if (npr.length() != 13) throw new IllegalStateException("Illegal NPR");
		NPR = npr.toCharArray();
	}

	public void render(String[][] table) { render(CLIUtil.windowWidth, table); }
	public void render(int _width, String[][] table) {
		sb.clear();

		int lrCount = table[0].length - 1;
		if (boxWidth.length < lrCount) boxWidth = new int[lrCount];
		else for (int i = 0; i < lrCount; i++) boxWidth[i] = 0;

		for (int i = 0; i < table.length; i++) {
			String[] box = table[i];
			int off = i==0?1:0;
			for (int j = off; j < box.length; j++) {
				int w = CLIUtil.getStringWidth(box[j]);
				boxWidth[j-off] = Math.max(boxWidth[j-off], w);
			}
		}

		double totalWidth = 0;
		for (int i = 0; i < lrCount; i++) totalWidth += boxWidth[i];

		int off = 0;
		int width = _width-4-3*(lrCount-1);
		if (width < 0) width = _width;
		for (int i = 0; i < lrCount; i++) {
			int normalizedWidth = (int) (width * (boxWidth[i] / totalWidth));
			if (normalizedWidth < boxWidth[i]/10) normalizedWidth = boxWidth[i]/10;
			else if (normalizedWidth < 6 && boxWidth[i] > 6) normalizedWidth = 6;

			boxWidth[i] = normalizedWidth;
			boxOffset.add(off);
			off += normalizedWidth+3;
		}
		realWidth = off-3;

		writeTop(table[0][0]);
		for (int i = 0; i < table.length;) {
			writeBox(table[i], i==0?1:0);
			if (++i == table.length) break;
			writeSeparator();
		}

		writeBottom();

		System.out.println(sb);
	}

	private void writeBox(String[] box, int i1) {
		List<List<String>> totalLines = new SimpleList<>();
		for (int i = i1; i < box.length; i++) {
			// -1来强制边距..算了,纯英文开比较好
			List<String> lines = CLIUtil.splitByWidth(box[i], boxWidth[i-i1]);
			for (int j = 0; j < lines.size(); j++) {
				String line = lines.get(j);
				if (totalLines.size() <= j) totalLines.add(new SimpleList<>());
				List<String> list = totalLines.get(j);
				while (list.size() < i-i1) list.add("");
				list.add(line);
			}
		}

		for (List<String> line : totalLines) {
			int vOffset = 0;
			for (int i = 0; i < boxOffset.size(); i++) {
				String str = i >= line.size() ? "" : line.get(i);
				sb.append(NPR[VERITAL_SEPARATOR]).append(NPR[SPACE]).append(str);
				int width = CLIUtil.getStringWidth(str);
				vOffset += width+2;
				while (width < boxWidth[i]) {
					sb.append(NPR[SPACE]);
					width++;
				}
			}
			sb.append(NPR[VERITAL_SEPARATOR]).append(NPR[LF]);
		}
	}
	private void writeTop(String head) {
		int width = CLIUtil.getStringWidth(head)+3;
		int half = (realWidth-width)/2;
		int vOffset = 1;

		sb.append(NPR[LU]);
		for (int i = 0; i < half; i++) {
			sb.append(NPR[boxOffset.contains(++vOffset) ? TOP_SEPARATOR : HORIZONTAL_SEPARATOR]);
		}

		sb.append("  ").append(head).append(' ');
		vOffset += width; // visual offset

		while (vOffset <= realWidth)
			sb.append(NPR[boxOffset.contains(++vOffset) ? TOP_SEPARATOR : HORIZONTAL_SEPARATOR]);

		sb.append(NPR[RU]).append(NPR[LF]);
	}
	private void writeSeparator() { writeLine(LEFT_SEPARATOR, ALL_SEPARATOR, RIGHT_SEPARATOR); }
	private void writeBottom() { writeLine(LD, BOTTOM_SEPARATOR, RD); }
	private void writeLine(int left, int special, int right) {
		int vLen = realWidth;
		int vOffset = 1;
		sb.append(NPR[left]);
		for (int i = 0; i < vLen; i++) {
			sb.append(NPR[boxOffset.contains(++vOffset) ? special : HORIZONTAL_SEPARATOR]);
		}
		sb.append(NPR[right]).append(NPR[LF]);
	}
}
