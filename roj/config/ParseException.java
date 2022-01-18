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
package roj.config;

import roj.math.MathUtils;
import roj.text.CharList;

/**
 * Signals that an error has been reached unexpectedly
 * while parsing.
 *
 * @see Exception
 */
public final class ParseException extends Exception {
    private static final long serialVersionUID = 3703218443322787635L;
    private static final boolean DEBUG = true;

    /**
     * The zero-based character offset into the string being parsed at which
     * the error was found during parsing.
     */
    private final int index;

    private int line = -2, linePos;
    private CharSequence lineContent;
    private CharList path;

    /**
     * Constructs a ParseException with the specified detail message and
     * offset.
     * A detail message is a String that describes this particular exception.
     *
     * @param reason       the detail message
     * @param index the position where the error is found while parsing.
     */
    public ParseException(CharSequence all, String reason, int index, Throwable cause) {
        super(reason, cause, true, DEBUG);
        this.index = index;
        this.lineContent = all;
    }

    public int getIndex() {
        return index;
    }

    public int getLine() {
        return line;
    }

    public int getLineOffset() {
        return linePos;
    }

    public CharSequence getPath() {
        return path;
    }

    public ParseException addPath(CharSequence pathSeq) {
        if (path == null) path = new CharList();
        path.insert(0, pathSeq);
        return this;
    }

    @Override
    public String getMessage() {
        return !(getCause() instanceof ParseException) ? super.getMessage() : getCause().getMessage();
    }

    public String getLineContent() {
        return lineContent.toString();
    }

    @SuppressWarnings("fallthrough")
    public void __lineParser() {
        if(this.line != -2) return;

        CharList chars = new CharList(20);

        CharSequence keys = this.lineContent;

        int target = index;
        if(target > keys.length() || target < 0) {
            noDetail();
            return;
        }

        int line = 1, linePos = 0;
        int i = 0;

        for (; i < target; i++) {
            char c1 = keys.charAt(i);
            switch (c1) {
                case '\r':
                    if(i + 1 < keys.length() && keys.charAt(i + 1) == '\n') // \r\n
                        i++;
                case '\n':
                    linePos = 0;
                    line++;
                    chars.clear();
                    break;
                default:
                    linePos++;
                    chars.append(c1);
            }
        }

        o:
        for (; i < keys.length(); i++) {
            char c1 = keys.charAt(i);
            switch (c1) {
                case '\r':
                case '\n':
                    break o; // till this line end
                default:
                    chars.append(c1);
            }
        }

        this.line = line;
        this.linePos = linePos - 1;
        this.lineContent = chars.toString();
    }

    public void noDetail() {
        this.line = 0;
        this.linePos = 0;
        this.lineContent = "<无数据>";
    }

    @Override
    public String toString() {
        String msg = getMessage() == null ? (getCause() == null ? "<未提供>" : getCause().toString()) : getMessage();

        __lineParser();

        String line = getLineContent();

        CharList k = new CharList().append("解析错误:\r\n  Line ").append(this.line).append(": ");

        if (line.length() > 512) {
            k.append("当前行偏移量 ").append(this.linePos);
        } else {
            k.append(line).append("\r\n");
            int off = this.linePos + 10 + MathUtils.digitCount(this.line);
            for (int i = 0; i < off; i++) {
                k.append('-');
            }

            for (int i = linePos; i >= 0; i--) {
                char c = line.charAt(i);
                if (c > 255) // 双字节我直接看做中文了, 再说吧
                    k.append('-');
                else if(c == 9) // 制表符, cmd中显示5个
                    k.append("----");
            }
            k.append('^');
        }

        k.append("\r\n总偏移量: ").append(this.index);
        if (path != null) {
            k.append("\r\n对象位置: ").append(path);
        }
        return k.append("\r\n原因: ").append(msg).append("\r\n").toString();
    }
}
