package roj.ui;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: TextAreaOutputStream.java
 */

import roj.io.DummyOutputStream;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.io.PrintStream;
import java.io.UTFDataFormatException;

public class TextAreaPrintStream extends PrintStream {
    private final JTextArea textArea;
    private final CharList sb = new CharList();
    private final int MAX;

    public TextAreaPrintStream(final JTextArea textArea, int max) {
        super(DummyOutputStream.INSTANCE);
        this.textArea = textArea;
        MAX = max;
    }

    public void write(int var1) {
        sb.append((char) var1);
        if(sb.length() > MAX) {
            sb.delete(0);
        }
    }

    public void write(@Nonnull byte[] arr, int off, int len) {
        if(sb.length() + len > MAX) {
            sb.delete(0, sb.length() + len - MAX);
        }

        try {
            ByteReader.decodeUTF(-1, sb, new ByteList(arr).subList(off, len));
        } catch (UTFDataFormatException e) {
            e.printStackTrace();
        }
    }

    private void write(char[] var1) {
        if(sb.length() + var1.length > MAX) {
            sb.delete(0, sb.length() + var1.length - MAX);
        }
        sb.append(var1);
    }

    private void write(String var1) {
        if(sb.length() + var1.length() > MAX) {
            sb.delete(0, sb.length() + var1.length() - MAX);
        }
        sb.append(var1);
    }

    private void newLine() {
        synchronized (sb) {
            sb.append('\n');

            final String string = sb.toString();
            sb.delete(0, sb.length());
            SwingUtilities.invokeLater(() -> {
                textArea.append(string);
                Document document = textArea.getDocument();
                if (document.getLength() > MAX) {
                    int diff = document.getLength() - MAX;
                    try {
                        document.remove(0, diff);
                    } catch (BadLocationException e) {
                        e.printStackTrace();
                    }
                }
                //textArea.updateUI();
                //textArea.validate();
            });
        }
    }

    public void flush() {
    }

    public void close() {
    }

    public boolean checkError() {
        return false;
    }

    public PrintStream append(CharSequence var1, int var2, int var3) {
        this.write(var1 == null ? "null" : var1.subSequence(var2, var3).toString());
        return this;
    }

    public void print(boolean var1) {
        this.write(var1 ? "true" : "false");
    }

    public void print(char var1) {
        this.write(String.valueOf(var1));
    }

    public void print(int var1) {
        this.write(String.valueOf(var1));
    }

    public void print(long var1) {
        this.write(String.valueOf(var1));
    }

    public void print(float var1) {
        this.write(String.valueOf(var1));
    }

    public void print(double var1) {
        this.write(String.valueOf(var1));
    }

    public void print(@Nonnull char[] var1) {
        this.write(var1);
    }

    public void print(String var1) {
        if (var1 == null) {
            var1 = "null";
        }

        this.write(var1);
    }

    public void print(Object var1) {
        this.write(String.valueOf(var1));
    }

    public void println() {
        this.newLine();
    }

    public void println(boolean var1) {
        this.print(var1);
        this.newLine();
    }

    public void println(char var1) {
        this.print(var1);
        this.newLine();
    }

    public void println(int var1) {
        this.print(var1);
        this.newLine();
    }

    public void println(long var1) {
        this.print(var1);
        this.newLine();
    }

    public void println(float var1) {
        this.print(var1);
        this.newLine();
    }

    public void println(double var1) {
        this.print(var1);
        this.newLine();
    }

    public void println(@Nonnull char[] var1) {
        this.print(var1);
        this.newLine();
    }

    public void println(String var1) {
        this.print(var1);
        this.newLine();
    }

    public void println(Object var1) {
        String var2 = String.valueOf(var1);
        this.print(var2);
        this.newLine();
    }
}
