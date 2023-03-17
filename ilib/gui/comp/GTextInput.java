package ilib.gui.comp;

import ilib.ClientProxy;
import ilib.client.RenderUtils;
import ilib.gui.IGui;
import ilib.gui.util.ComponentListener;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.GlStateManager.LogicOp;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class GTextInput extends SimpleComponent {
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

	protected FontRenderer fr;

	private static final int MAX_UNDO = 99;
	private SimpleList<String>
		undo = new SimpleList<>(),
		redo = new SimpleList<>();

	public GTextInput(IGui parent, int x, int y, int w, int h, @Nullable String text) {
		super(parent, x, y, w, h);
		fr = ClientProxy.mc.fontRenderer;
		enabled = true;
		maxLength = 127;
		setText(text);
	}

	protected void onChange(String value) {
		if (listener != null) listener.actionPerformed(this, ComponentListener.TEXT_CHANGED);
	}

	protected void onKeyDown(String value) {}

	@Override
	public void mouseDown(int x, int y, int button) {
		if (!checkMouseOver(x, y)) {
			setFocused(false);
			return;
		}

		super.mouseDown(x, y, button);

		setFocused(true);
		setSelection(prevSel = getSelectionPos(x));
	}

	@Override
	public void mouseDrag(int x, int y, int button, long time) {
		int pos = getSelectionPos(x);
		if (pos <= prevSel) {
			setSelection(pos, prevSel);
		} else {
			setSelection(prevSel, pos);
		}
	}

	private int getSelectionPos(int x) {
		x -= xPos;
		String text = this.text;
		int i = scrollChar;
		for (; i < text.length(); i++) {
			int w = fr.getCharWidth(text.charAt(i));
			if (x < w) break;
			x -= w;
		}

		return i;
	}

	public boolean isMouseOver(int mouseX, int mouseY) {
		return enabled && (focused || checkMouseOver(mouseX, mouseY));
	}

	@Override
	public void keyTyped(char letter, int keyCode) {
		if (!focused) return;
		cursorTicker = System.currentTimeMillis();

		super.keyTyped(letter, keyCode);

		switch (letter) {
			case 1: // ctrl-a
				setSelection(0, text.length());
				return;
			case 3: // ctrl-c
				GuiScreen.setClipboardString(getSelectedText());
				return;
			case 22: // ctrl-v
				insert(GuiScreen.getClipboardString());
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

	@Override
	public void render(int mouseX, int mouseY) {
		GlStateManager.disableTexture2D();
		RenderUtils.drawRectangle(xPos - 1, yPos - 1, 0, width + 2, height + 2, -6250336);
		RenderUtils.drawRectangle(xPos, yPos, 0, width, height, -16777216);
		GlStateManager.enableTexture2D();
	}

	@Override
	public void render2(int mouseX, int mouseY) {
		String text = this.text;

		if (text.isEmpty() && !focused) {
			drawPlaceholder();
			return;
		}

		FontRenderer fr = this.fr;
		int w = width;
		int rightBound = scrollChar;
		while (rightBound < text.length()) {
			int w1 = fr.getCharWidth(text.charAt(rightBound));
			if (w < w1) break;
			w -= w1;
			rightBound++;
		}
		String tmpText = text.substring(scrollChar, rightBound);

		float v = (float) (height - 2) / fr.FONT_HEIGHT;
		if (v > 1.25) {
			GlStateManager.pushMatrix();
			GlStateManager.translate(xPos + 2, yPos + 1 / v * (height - fr.FONT_HEIGHT) / 2f, 0f);
			GlStateManager.scale(1, v, 1);
			fr.drawStringWithShadow(tmpText, 0, 0, color);
			GlStateManager.popMatrix();
		} else {
			fr.drawStringWithShadow(tmpText, xPos + 2, yPos + (height - fr.FONT_HEIGHT) / 2f, color);
		}

		if (focused && selectionStart <= rightBound && selectionEnd >= scrollChar) {
			int end = selectionStart;

			int i = scrollChar;
			int x = 0;
			while (i < end) {
				x += fr.getCharWidth(text.charAt(i++));
			}

			w = 1;
			end = selectionEnd;
			while (i < end) {
				w += fr.getCharWidth(text.charAt(i++));
			}

			if (w == 1 && ((int) (System.currentTimeMillis() - cursorTicker) % BLING_TIME) > (BLING_TIME/2)) return;
			w = Math.min(w, width - x);

			GlStateManager.disableTexture2D();

			GlStateManager.enableColorLogic();
			GlStateManager.colorLogicOp(LogicOp.OR_REVERSE);
			RenderUtils.drawRectangle(xPos + 2 + x, yPos, 0, w, height, color ^ 0xFFFFFF);
			GlStateManager.disableColorLogic();

			GlStateManager.enableTexture2D();
		}
	}

	protected void drawPlaceholder() {
		if (placeholder != null) {
			fr.drawStringWithShadow(placeholder, xPos + 2, yPos + (height - fr.FONT_HEIGHT) / 2f, color);
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
		if (start < 0) {start = 0;} else if (start > text.length()) start = text.length();
		if (end < 0) {end = 0;} else if (end > text.length()) end = text.length();
		selectionStart = start;
		selectionEnd = end;

		FontRenderer fr = this.fr;
		if (fr != null) {
			if (start == prevSel) start = end;
			scrollChar = Math.min(text.length(), scrollChar);

			String text = this.text;
			int beforeW = 0;
			for (int i = scrollChar; i < start; i++) {
				beforeW += fr.getCharWidth(text.charAt(i));
			}

			int i = scrollChar;
			int w = this.width;
			// 触发距离
			if (w - beforeW < 12) {
				// 显示距离
				while (beforeW > 12 && i < text.length()) {
					beforeW -= fr.getCharWidth(text.charAt(i++));
				}
			} else if (beforeW < 12) {
				while (w - beforeW > 12 && i > 0) {
					beforeW += fr.getCharWidth(text.charAt(--i));
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

	public FontRenderer getFontRenderer() {
		return fr;
	}

	public void setFontRenderer(FontRenderer fr) {
		this.fr = fr;
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
