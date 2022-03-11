package roj.text;

import java.io.Writer;

/**
 * @author Roj233
 * @since 2022/3/16 20:48
 */
public class StringWriter extends Writer {
    private final StringBuilder buf;

    public StringWriter() {
        buf = new StringBuilder();
        lock = buf;
    }

    public StringWriter(int cap) {
        buf = new StringBuilder(cap);
        lock = buf;
    }

    @Override
    public void write(int c) {
        buf.append((char) c);
    }

    @Override
    public void write(char[] buf, int off, int len) {
        this.buf.append(buf, off, len);
    }

    @Override
    public void write(String str) {
        buf.append(str);
    }

    @Override
    public void write(String str, int off, int len)  {
        buf.append(str, off, off + len);
    }

    @Override
    public StringWriter append(CharSequence csq) {
        buf.append(csq);
        return this;
    }

    @Override
    public StringWriter append(CharSequence csq, int start, int end) {
        buf.append(csq, start, end);
        return this;
    }

    @Override
    public StringWriter append(char c) {
        write(c);
        return this;
    }

    @Override
    public String toString() {
        return buf.toString();
    }

    public StringBuilder getBuffer() {
        return buf;
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
}
