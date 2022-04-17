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
package roj.text;

import roj.io.IOUtil;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * @author Roj234
 * @since 2021/5/27 0:12
 */
public class SimpleLineReader implements Iterable<String>, AutoCloseable, Iterator<String> {
    private final CharSequence keys;
    private final boolean cleanEmpty;
    private int index, size = -1, lineNumber;
    private String[] lines;

    public SimpleLineReader(InputStream stream) throws IOException {
        this(IOUtil.readUTF(stream), true);
    }

    public SimpleLineReader(InputStream stream, boolean cleanEmpty) throws IOException {
        this(IOUtil.readUTF(stream), cleanEmpty);
    }

    public SimpleLineReader(CharSequence string) {
        this(string, true);
    }

    public SimpleLineReader(CharSequence string, boolean cleanEmpty) {
        this.keys = string;
        this.cleanEmpty = cleanEmpty;
    }

    @SuppressWarnings("fallthrough")
    public static List<String> slrParserV2(CharSequence keys, boolean clean) {
        List<String> list = new ArrayList<>();

        int r = 0, i = 0, prev = 0;
        while (i < keys.length()) {
            switch (keys.charAt(i)) {
                case '\r':
                    if (i + 1 >= keys.length() || keys.charAt(i + 1) != '\n') {
                        break;
                    } else {
                        r = 1;
                        i++;
                    }
                case '\n':
                    if (prev < i || !clean) {
                        list.add(prev == i ? "" : keys.subSequence(prev, i - r).toString());
                    }
                    prev = i + 1;
                    r = 0;
                    break;
            }
            i++;
        }

        if (prev < i || !clean) {
            list.add(prev == i ? "" : keys.subSequence(prev, i).toString());
        }

        return list;
    }

    @SuppressWarnings("fallthrough")
    public static String readSingleLine(CharSequence keys, int line) {
        int r = 0, i = 0, prev = 0;
        while (i < keys.length()) {
            switch (keys.charAt(i)) {
                case '\r':
                    if (i + 1 >= keys.length() || keys.charAt(i + 1) != '\n') {
                        break;
                    } else {
                        r = 1;
                        i++;
                    }
                case '\n':
                    if(--line == 0) {
                        return prev == i ? "" : keys.subSequence(prev, i - r).toString();
                    }
                    prev = i + 1;
                    r = 0;
                    break;
            }
            i++;
        }

        return --line == 0 ? prev == i ? "" : keys.subSequence(prev, i).toString() : null;
    }

    public int index() {
        return this.index;
    }

    @SuppressWarnings("fallthrough")
    public int size() {
        if (size == -1) {
            int r = 0, size = 0, i = 0, prev = 0;
            CharSequence keys = this.keys;
            while (i < keys.length()) {
                switch (keys.charAt(i)) {
                    case '\r':
                        if (i + 1 >= keys.length() || keys.charAt(i + 1) != '\n') {
                            break;
                        } else {
                            r = 1;
                            i++;
                        }
                    case '\n':
                        if (prev+r < i || !cleanEmpty) {
                            size++;
                        }
                        prev = i + 1;
                        r = 0;
                        break;
                }
                i++;
            }

            this.size = prev < i || !cleanEmpty ? size + 1 : size;
        }
        return size;
    }

    public String[] toArray() {
        if (lines != null) return lines;
        List<String> lines = slrParserV2(keys, cleanEmpty);
        return this.lines = lines.toArray(new String[lines.size()]);
    }

    @Nonnull
    @Override
    public Iterator<String> iterator() {
        this.index = 0;
        this.lineNumber = 0;
        this.cur = null;
        return this;
    }

    @Override
    @Deprecated
    public void close() {}

    static final String EOF = new String();
    String cur;

    @Override
    @SuppressWarnings("fallthrough")
    public boolean hasNext() {
        if (cur != null) {
            return cur != EOF;
        } else if (index > keys.length()) {
            if (!cleanEmpty) lineNumber++;
            size = lineNumber;
            cur = EOF;
            return false;
        }

        int r = 0, i = index;
        CharSequence keys = this.keys;
        while (i < keys.length()) {
            switch (keys.charAt(i)) {
                case '\r':
                    if (i >= keys.length() || keys.charAt(i + 1) != '\n') {
                        break;
                    } else {
                        r = 1;
                        i++;
                    }
                case '\n':
                    if (i > index+r || !cleanEmpty) {
                        CharSequence seq = index == i ? "" : keys.subSequence(index, i - r);
                        index = i+1;
                        lineNumber++;
                        cur = seq.toString();
                        return true;
                    }
                    index = i+1;
                    r = 0;
                    break;
            }
            i++;
        }

        if (i > index || !cleanEmpty) {
            CharSequence seq = index == i ? "" : keys.subSequence(index, i);
            index = i+1;
            cur = seq.toString();
            return true;
        } else {
            index = i;
            cur = EOF;
            return false;
        }
    }

    @Override
    public String next() {
        hasNext();
        if (cur == EOF) {
            throw new NoSuchElementException();
        } else {
            String c = cur;
            cur = null;
            return c;
        }
    }

    @SuppressWarnings("fallthrough")
    public int skipLines(int oLines) {
        int lines = oLines;
        int r = 0, prev = index, i = index;
        CharSequence keys = this.keys;
        while (i < keys.length()) {
            switch (keys.charAt(i)) {
                case '\r':
                    if (i >= keys.length() || keys.charAt(i + 1) != '\n') {
                        break;
                    } else {
                        r = 1;
                        i++;
                    }
                case '\n':
                    if (i > prev+r || !cleanEmpty) {
                        lineNumber++;
                        if (lines-- <= 0) {
                            CharSequence seq = prev == i ? "" : keys.subSequence(prev, i - r);
                            index = i+1;
                            cur = seq.toString();
                            return oLines;
                        }
                    }
                    prev = i+1;
                    break;
            }
            i++;
        }

        if (i > prev || !cleanEmpty) {
            lineNumber++;
            if (lines-- <= 0) {
                CharSequence seq = prev == i ? "" : keys.subSequence(prev, i);
                index = i+1;
                cur = seq.toString();
                return oLines;
            }
        }
        cur = EOF;
        return oLines - lines;
    }

    public int lineNumber() {
        return lineNumber;
    }
}
