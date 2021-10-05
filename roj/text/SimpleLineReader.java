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
import roj.util.ByteList;
import roj.util.ByteReader;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;

/**
 * Simple Line Reader
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/27 0:12
 */
public class SimpleLineReader implements Iterable<String>, Closeable, Iterator<String> {
    private final List<String> lines;
    private int index;

    private InputStream stream;

    /**
     * 简单的LineReader
     *
     * @param stream InputStream
     */
    public SimpleLineReader(InputStream stream) throws IOException {
        this(readAsUTF(stream), true);
        this.stream = stream;
    }

    public SimpleLineReader(InputStream stream, boolean cleanEmptyLines) throws IOException {
        this(readAsUTF(stream), cleanEmptyLines);
        this.stream = stream;
    }

    private static CharSequence readAsUTF(InputStream stream) throws IOException {
        ByteList in = IOUtil.getSharedByteBuf().readStreamArrayFully(stream);
        CharList out = new CharList((in.pos() / 3) << 1);
        ByteReader.decodeUTF(-1, out, in);
        in.clear();
        return out;
    }

    /**
     * 简单的LineReader
     *
     * @param string String
     */
    public SimpleLineReader(CharSequence string) {
        this(string, true);
    }

    public SimpleLineReader(CharSequence string, boolean cleanEmptyLines) {
        this.lines = (string == null || string.length() == 0) ? Collections.emptyList() : slrParserV2(string, cleanEmptyLines);
    }

    @SuppressWarnings("fallthrough")
    public static List<String> slrParserV2(CharSequence keys, boolean clean) {
        CharList chars = new CharList(20);
        List<String> list = new ArrayList<>();

        for (int i = 0; i < keys.length(); i++) {
            char c1 = keys.charAt(i);
            switch (c1) {
                case '\r':
                    if (i + 1 < keys.length() && keys.charAt(i + 1) == '\n') // \r\n
                        i++;
                case '\n':
                    if (chars.length() > 0 || !clean) {
                        list.add(chars.toString());
                        chars.clear();
                    }
                    break;
                default:
                    chars.append(c1);
            }
        }

        if (chars.length() > 0) {
            list.add(chars.toString());
        }

        return list;
    }

    @SuppressWarnings("fallthrough")
    public static String readSingleLine(String keys, boolean clean, int line) {
        CharList chars = new CharList(20);

        for (int i = 0; i < keys.length(); i++) {
            char c1 = keys.charAt(i);
            switch (c1) {
                case '\r':
                    if (i + 1 < keys.length() && keys.charAt(i + 1) == '\n') // \r\n
                        i++;
                case '\n':
                    if (chars.length() > 0 || !clean) {
                        if(line-- == 0) {
                            return chars.toString();
                        }
                        chars.clear();
                    }
                    break;
                default:
                    chars.append(c1);
            }
        }

        return line == 0 ? chars.toString() : null;
    }

    public SimpleLineReader(List<String> list) {
        this.lines = list;
    }

    public int index() {
        return this.index;
    }

    public int size() {
        return this.lines.size();
    }

    public String get(int index) {
        return this.lines.get(index);
    }

    public String[] toArray() {
        return this.lines.toArray(new String[this.lines.size()]);
    }

    @Nonnull
    @Override
    public Iterator<String> iterator() {
        this.index = 0;
        return this;
    }

    @Override
    public void forEach(Consumer<? super String> consumer) {
        forEachRemaining(consumer);
    }

    @Override
    public Spliterator<String> spliterator() {
        return Spliterators.spliterator(lines, 0);
    }

    @Override
    public void close() throws IOException {
        if (this.stream != null)
            this.stream.close();
    }

    @Override
    public boolean hasNext() {
        return index < lines.size();
    }

    @Override
    public String next() {
        return lines.get(index++);
    }

    public String prev() {
        return lines.get(--index);
    }

    @Override
    public void forEachRemaining(Consumer<? super String> consumer) {
        while (index < lines.size()) {
            consumer.accept(lines.get(index++));
        }
    }

    public void index(int index) {
        this.index = index;
    }

    public List<String> getList() {
        return lines;
    }
}
