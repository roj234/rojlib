/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
