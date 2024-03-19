package roj.ui;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public final class TextAreaPrintStream extends DelegatedPrintStream {
	private final JTextComponent textArea;
	private final int MAX;

	public TextAreaPrintStream(final JTextComponent textArea, int max) {
		this.textArea = textArea;
		this.MAX = max;
	}

	@Override
	protected void newLine() {
		String value;
		synchronized (sb) {
			value = sb.append('\n').toString();
			sb.clear();
		}

		SwingUtilities.invokeLater(() -> {
			Document doc = GuiUtil.insert(textArea, value);

			if (doc.getLength() > MAX) {
				int diff = doc.getLength() - MAX;
				try {
					doc.remove(0, diff);
				} catch (BadLocationException ignored) {}
			}
		});
	}
}