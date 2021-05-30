package roj.text;

import roj.io.IOUtil;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: SimpleLineReader.java
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
        this(IOUtil.readAsUTF(stream), true);
        this.stream = stream;
    }

    public SimpleLineReader(InputStream stream, boolean cleanEmptyLines) throws IOException {
        this(IOUtil.readAsUTF(stream), cleanEmptyLines);
        this.stream = stream;
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
}
