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

import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import roj.io.DummyOutputStream;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteList.Slice;

import javax.annotation.Nonnull;
import java.io.PrintStream;
import java.io.UTFDataFormatException;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public abstract class DelegatedPrintStream extends PrintStream {
    protected final CharList sb = new CharList();
    protected final int MAX;

    public DelegatedPrintStream(int max) {
        super(DummyOutputStream.INSTANCE);
        MAX = max;
    }

    public final void write(int var1) {
        sb.append((char) var1);
        if(sb.length() > MAX) {
            sb.delete(0);
        }
    }

    public final void write(@Nonnull byte[] arr, int off, int len) {
        if(sb.length() + len > MAX) {
            sb.delete(0, sb.length() + len - MAX);
        }

        try {
            ByteList.decodeUTF(-1, sb, new Slice(arr, off, len));
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

    @OverrideOnly
    protected void newLine() {
        sb.clear();
    }

    public final void flush() {
    }

    public final void close() {
    }

    public final boolean checkError() {
        return false;
    }

    public final PrintStream append(CharSequence var1, int var2, int var3) {
        this.write(var1 == null ? "null" : var1.subSequence(var2, var3).toString());
        return this;
    }

    public final void print(boolean var1) {
        this.write(var1 ? "true" : "false");
    }

    public final void print(char var1) {
        this.write(String.valueOf(var1));
    }

    public final void print(int var1) {
        this.write(String.valueOf(var1));
    }

    public final void print(long var1) {
        this.write(String.valueOf(var1));
    }

    public final void print(float var1) {
        this.write(String.valueOf(var1));
    }

    public final void print(double var1) {
        this.write(String.valueOf(var1));
    }

    public final void print(@Nonnull char[] var1) {
        this.write(var1);
    }

    public final void print(String var1) {
        if (var1 == null) {
            var1 = "null";
        }

        this.write(var1);
    }

    public final void print(Object var1) {
        this.write(String.valueOf(var1));
    }

    public final void println() {
        this.newLine();
    }

    public final void println(boolean var1) {
        this.print(var1);
        this.newLine();
    }

    public final void println(char var1) {
        this.print(var1);
        this.newLine();
    }

    public final void println(int var1) {
        this.print(var1);
        this.newLine();
    }

    public final void println(long var1) {
        this.print(var1);
        this.newLine();
    }

    public final void println(float var1) {
        this.print(var1);
        this.newLine();
    }

    public final void println(double var1) {
        this.print(var1);
        this.newLine();
    }

    public final void println(@Nonnull char[] var1) {
        this.print(var1);
        this.newLine();
    }

    public final void println(String var1) {
        this.print(var1);
        this.newLine();
    }

    public final void println(Object var1) {
        String var2 = String.valueOf(var1);
        this.print(var2);
        this.newLine();
    }

    public CharList getChars() {
        return sb;
    }

    public int getMax() {
        return MAX;
    }
}
