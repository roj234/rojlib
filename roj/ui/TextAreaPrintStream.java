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

	public TextAreaPrintStream(final JTextComponent textArea, int max) {
		super(max);
		this.textArea = textArea;
	}

	@Override
	protected void newLine() {
		String value;
		synchronized (sb) {
			value = sb.append('\n').toString();
			sb.clear();
		}

		SwingUtilities.invokeLater(() -> {
			Document doc = GUIUtil.insert(textArea, value);

			if (doc.getLength() > MAX) {
				int diff = doc.getLength() - MAX;
				try {
					doc.remove(0, diff);
				} catch (BadLocationException ignored) {}
			}
		});
	}
}
