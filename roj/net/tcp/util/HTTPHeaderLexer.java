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
package roj.net.tcp.util;

import roj.config.ParseException;
import roj.math.MathUtils;
import roj.text.CharList;
import roj.util.ByteWriter;

import static roj.config.word.AbstLexer.WHITESPACE;

/**
 * @author Roj234
 * @version 1.0
 * @since  2021/2/4 16:56
 */
public final class HTTPHeaderLexer {
    public HTTPHeaderLexer init(CharSequence s) {
        this.input = s;
        this.index = 0;
        return this;
    }

    CharSequence input;
    public int index;
    CharList found = new CharList();

    /**
     * 读词
     */
    public String readHttpWord() {
        CharSequence in = this.input;
        int i = this.index;

        CharList temp = this.found;
        temp.clear();

        int remain;
        while ((remain = in.length() - i) > 0) {
            int c = in.charAt(i++);
            switch (c) {
                case '\r':
                    if (remain > 1 && in.charAt(i) == '\n') {
                        i++;
                        if (remain > 3 && in.charAt(i) == '\r' && in.charAt(i + 1) == '\n') {
                            this.index = i + 2;
                            return Shared._SHOULD_EOF;
                        }
                    } else {
                        this.index = i;
                        return Shared._ERROR;
                    }
                    break;
                case ':':
                    if (in.charAt(i++) != ' ') {
                        this.index = i;
                        return Shared._ERROR;
                    }

                    while ((c = in.charAt(i++)) != '\r' || in.charAt(i) != '\n') {
                        temp.append((char) c);
                    }
                    this.index = i - 1;

                    if (temp.length() == 0) {
                        return "";
                    }

                    return temp.toString();

                default: {
                    if (!WHITESPACE.contains(c)) {
                        i--;

                        while (i < in.length()) {
                            c = in.charAt(i++);

                            if (!WHITESPACE.contains(c) && c != ':') {
                                temp.append((char) c);
                            } else {
                                i--;
                                break;
                            }
                        }

                        this.index = i;

                        if (temp.length() == 0) {
                            return null;
                        }

                        return temp.toString();
                    }
                }
            }
        }
        this.index = i;
        return Shared._SHOULD_EOF;
    }

    public String content(String length, int max) throws ParseException {
        int index = this.index;
        final CharSequence input = this.input;

        final CharList temp = this.found;
        temp.clear();

        int len;
        if (length != null) {
            try {
                len = MathUtils.parseInt(length);
                if (index + len > input.length()) {
                    throw err("Invalid clen " + len + " of fact " + input.length());
                }
            } catch (NumberFormatException e) {
                throw err("Excepting NUMBER, got " + length);
            }
            if (len > max)
                throw new Notify(-127);
        } else {
            try { // wait for connection close
                int i = index;
                while (input.length() == Integer.MAX_VALUE) {
                    input.charAt(i++);
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {}

            len = input.length() - index;
        }

        temp.ensureCapacity(temp.length() + len);

        char[] list = temp.list;
        int j = temp.length();

        try {
            int byteLen = 0;
            while (byteLen < len) {
                final char c = input.charAt(index++);
                list[j++] = c;
                byteLen += ByteWriter.byteCountUTF8(c);
            }

        } catch (ArrayIndexOutOfBoundsException ignored) {
            System.err.println("Connection closed too early");
            j--;
        }
        temp.setIndex(j);

        this.index = index;
        return temp.toString();
    }

    public ParseException err(String reason) {
        ParseException pe = new ParseException(input, reason, index, null);
        try {
            pe.__lineParser();
        } catch (Throwable ignored) {
            pe.noDetail();
        }
        return pe;
    }

    public ParseException err(String reason, Throwable reason2) {
        ParseException pe = new ParseException(input, reason, index, reason2);
        try {
            pe.__lineParser();
        } catch (Throwable ignored) {
            pe.noDetail();
        }
        return pe;
    }

    public String readLine() {
        int index = this.index;
        final CharSequence input = this.input;

        final CharList temp = this.found;
        temp.clear();

        char c;
        while ((c = input.charAt(index++)) != '\r' || input.charAt(index) != '\n') {
            temp.append(c);
        }
        this.index = index + 1;

        if (temp.length() == 0) {
            return "";
        }

        return temp.toString();
    }
}
