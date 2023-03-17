package roj.ui;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public final class TextAreaPrintStream extends DelegatedPrintStream {
	private final JTextArea textArea;

	public TextAreaPrintStream(final JTextArea textArea, int max) {
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
			textArea.append(value);
			Document doc = textArea.getDocument();
			if (doc.getLength() > MAX) {
				int diff = doc.getLength() - MAX;
				try {
					doc.remove(0, diff);
				} catch (BadLocationException ignored) {}
			}
		});
	}
}
