package roj.opengl;

import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.opengl.text.TextRenderer;
import roj.opengl.util.Util;
import roj.opengl.util.VboUtil;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;
import roj.text.CharList;

import javax.annotation.Nullable;

import static org.lwjgl.opengl.GL11.*;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class SimpleInputBox {
	public static final int ERROR_COLOR = 0xFFE62E00;
	public static final int NORMAL_COLOR = 0xFFFFFFFF;
	public static final int INVALID_CHAR_COLOR = 0xFF4400FF;
	private static final int BLING_TIME = 500;

	protected static long cursorTicker;

	private String prevText;
	private int prevSel;

	protected String text, placeholder;
	private int selectionStart, selectionEnd;
	private int scrollChar;
	private boolean focused, enabled;
	protected int color, maxLength;

	int xPos, yPos, width, height;

	protected TextRenderer fr = Game.instance.fr;

	private static final int MAX_UNDO = 99;
	private SimpleList<String>
		undo = new SimpleList<>(),
		redo = new SimpleList<>();

	public SimpleInputBox(int x, int y, int w, int h, @Nullable String text) {
		xPos = x;
		yPos = y;
		width = w;
		height = h;
		enabled = true;
		maxLength = 127;
		setText(text);
	}

	protected void onChange(String value) {}
	protected void onKeyDown(String value) {}

	protected final boolean checkMouseOver(int mouseX, int mouseY) {
		return mouseX >= xPos && mouseX < xPos + width && mouseY >= yPos && mouseY < yPos + height;
	}

	public void mouseDown(int x, int y, int button) {
		if (!checkMouseOver(x, y)) {
			setFocused(false);
			return;
		}

		setFocused(true);
		setSelection(prevSel = getSelectionPos(x));
	}

	public void mouseDrag(int x, int y, int button, long time) {
		int pos = getSelectionPos(x);
		if (pos <= prevSel) {
			setSelection(pos, prevSel);
		} else {
			setSelection(prevSel, pos);
		}
	}

	private int getSelectionPos(int x) {
		float x1 = x - xPos;
		String text = this.text;
		int i = scrollChar;
		for (; i < text.length(); i++) {
			float w = fr.getCharWidthFloat(text.charAt(i));
			if (x1 < w) break;
			x1 -= w;
		}

		return i;
	}

	public void keyTyped(char letter, int keyCode) {
		if (!focused) return;
		cursorTicker = System.currentTimeMillis();

		switch (letter) {
			case 1: // ctrl-a
				setSelection(0, text.length());
				return;
			case 3: // ctrl-c
				//GuiScreen.setClipboardString(getSelectedText());
				return;
			case 22: // ctrl-v
				//insert(GuiScreen.getClipboardString());
				return;
		}

		switch (keyCode) {
			case 1: // esc
				setFocused(false);
				return;
			case 28: // enter
				if (isValidText(text)) {
					if (!text.equals(prevText)) {
						onChange(text);
						prevText = text;
					}
				}
				return;
			case 203: // left arrow
				setSelection(selectionStart - 1);
				return;
			case 205: // right arrow
				setSelection(selectionEnd + 1);
				return;
			case 14:
			case 211:
				if (text.isEmpty()) return;

				CharList tmp = IOUtil.getSharedCharBuf();
				text = tmp.append(text, 0, selectionStart - 1).append(text, selectionEnd, text.length()).toString();
				setSelection(selectionStart - 1);
				checkValid();

				addUndo(text);
				return;
		}

		if (letter < 32 || text.length() >= maxLength || !isValidChar(letter, keyCode)) {
			color = INVALID_CHAR_COLOR;
			return;
		}

		CharList tmp = IOUtil.getSharedCharBuf();
		text = tmp.append(text, 0, selectionStart).append(letter).append(text, selectionEnd, text.length()).toString();
		setSelection(selectionStart + 1);
		checkValid();

		addUndo(text);
		onKeyDown(text);
	}

	protected final void addUndo(String text) {
		undo.add(text);
		redo.clear();
		if (undo.size() >= MAX_UNDO)
			undo.removeRange(0, undo.size()-MAX_UNDO);
	}

	protected boolean history(boolean _undo) {
		SimpleList<String> listA, listB;
		listA = _undo? undo:redo;
		listB = _undo? redo:undo;

		if (listA.isEmpty()) return false;
		String text = listA.remove(listA.size()-1);
		listB.add(text);

		setTextDirect(text);
		return true;
	}

	public void render(int mouseX, int mouseY) {
		VertexBuilder vb = Util.sharedVertexBuilder;
		vb.begin(VertexFormat.POSITION_COLOR);
		Util.drawBar(vb, xPos, yPos, width, height, color);
		VboUtil.drawCPUVertexes(GL_QUADS, vb);

		String text = this.text;

		if (text.isEmpty() && !focused) {
			drawPlaceholder();
			return;
		}

		TextRenderer fr = this.fr;
		float w = width;
		int rightBound = scrollChar;
		while (rightBound < text.length()) {
			float w1 = fr.getCharWidthFloat(text.charAt(rightBound)) + 1;
			if (w < w1) break;
			w -= w1;
			rightBound++;
		}
		String tmpText = text.substring(scrollChar, rightBound);

		float v = (float) (height - 2) / fr.lineHeight;
		if (v > 1.25) {
			glPushMatrix();
			glTranslatef(xPos + 2, yPos + 1 / v * (height - fr.lineHeight) / 2f, 0f);
			glScalef(1, v, 1);
			fr.renderStringWithShadow(tmpText, 0, 0, color);
			glPopMatrix();
		} else {
			fr.renderStringWithShadow(tmpText, xPos + 2, yPos + (height - fr.lineHeight) / 2f, color);
		}

		if (focused && selectionStart <= rightBound && selectionEnd >= scrollChar) {
			int end = selectionStart;

			int i = scrollChar;
			int x = 0;
			while (i < end) {
				x += fr.getCharWidthFloat(text.charAt(i++)) + 1;
			}

			w = 1;
			end = selectionEnd;
			while (i < end) {
				w += fr.getCharWidthFloat(text.charAt(i++)) + 1;
			}

			if (w == 1 && ((int) (System.currentTimeMillis() - cursorTicker) % BLING_TIME) > (BLING_TIME / 2)) return;
			w = Math.min(w, width - x);

			glDisable(GL_TEXTURE_2D);

			glEnable(GL_COLOR_LOGIC_OP);
			glLogicOp(GL_OR_REVERSE);

			vb.begin(VertexFormat.POSITION_COLOR);
			Util.drawBar(vb, xPos + 2 + x, yPos, (int) w, height, color ^ 0xFFFFFF);
			VboUtil.drawCPUVertexes(GL_QUADS, vb);

			glDisable(GL_COLOR_LOGIC_OP);

			glEnable(GL_TEXTURE_2D);
		}
	}

	protected void drawPlaceholder() {
		if (placeholder != null) {
			fr.renderStringWithShadow(placeholder, xPos + 2, yPos + (height - fr.lineHeight) / 2f, color);
		}
	}

	/*******************************************************************************************************************
	 * Utilities                                                                                                        *
	 *******************************************************************************************************************/

	protected final void insert(CharSequence seq) {
		CharList tmp = IOUtil.getSharedCharBuf();
		text = tmp.append(text, 0, selectionStart).append(seq, 0, Math.min(seq.length(), maxLength - text.length())).append(text, selectionEnd, text.length()).toString();
		setSelection(selectionStart + seq.length());
		checkValid();

		addUndo(text);
	}

	protected final void checkValid() {
		color = isValidText(text) ? NORMAL_COLOR : ERROR_COLOR;
	}

	protected boolean isValidText(String text) {
		return true;
	}

	protected boolean isValidChar(char letter, int keyCode) {
		return true;
	}

	/*******************************************************************************************************************
	 * Accessors/Mutators                                                                                              *
	 *******************************************************************************************************************/

	public int getSelectionStart() {
		return selectionStart;
	}

	public int getSelectionEnd() {
		return selectionEnd;
	}

	public String getSelectedText() {
		return selectionStart == selectionEnd ? text : text.substring(selectionStart, selectionEnd);
	}

	public void setSelection(int pos) {
		setSelection(pos, pos);
	}

	public void setSelection(int start, int end) {
		if (start > end) {
			int t = start;
			start = end;
			end = t;
		}
		if (start < 0) start = 0;
		else if (start > text.length()) start = text.length();
		if (end < 0) end = 0;
		else if (end > text.length()) end = text.length();
		selectionStart = start;
		selectionEnd = end;

		TextRenderer fr = this.fr;
		if (fr != null) {
			if (start == prevSel) start = end;
			scrollChar = Math.min(text.length(), scrollChar);

			String text = this.text;
			float beforeW = 0;
			for (int i = scrollChar; i < start; i++) {
				beforeW += fr.getCharWidthFloat(text.charAt(i)) + 1;
			}

			int i = scrollChar;
			float w = this.width;
			// 触发距离
			if (w - beforeW < 12) {
				// 显示距离
				while (beforeW > 12 && i < text.length()) {
					beforeW -= fr.getCharWidthFloat(text.charAt(i++)) + 1;
				}
			} else if (beforeW < 12) {
				while (w - beforeW > 12 && i > 0) {
					beforeW += fr.getCharWidthFloat(text.charAt(--i)) + 1;
				}
			}
			scrollChar = i;
		}
	}

	public int getMaxLength() {
		return maxLength;
	}

	public void setMaxLength(int maxLength) {
		if (text.length() > maxLength) setTextDirect(text.substring(0, maxLength));
		this.maxLength = maxLength;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		if (text == null) text = "";
		else if (maxLength < text.length()) text = text.substring(0, maxLength);

		setTextDirect(text);
		addUndo(text);
	}

	protected void setTextDirect(String text) {
		this.text = text;
		this.selectionStart = selectionEnd = 0;
		this.scrollChar = 0;
		this.color = NORMAL_COLOR;
	}

	public String getPlaceholder() {
		return placeholder;
	}

	public void setPlaceholder(String placeholder) {
		this.placeholder = placeholder;
	}

	public int getColor() {
		return color;
	}

	public void setColor(int color) {
		this.color = color;
	}

	public boolean isFocused() {
		return focused;
	}

	public void setFocused(boolean focused) {
		if (!enabled) focused = false;
		cursorTicker = System.currentTimeMillis();

		if (this.focused == focused) return;
		this.focused = focused;
		if (!focused) {
			if (isValidText(text)) {
				if (!text.equals(prevText)) {
					onChange(text);
				}
			} else {
				color = NORMAL_COLOR;
				text = prevText;
				scrollChar = selectionStart = selectionEnd = 0;
			}
		} else {
			prevText = text;
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (!enabled) setFocused(false);
		this.enabled = enabled;
	}
}
