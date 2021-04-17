package roj.net.tcp.util;

import roj.config.ParseException;
import roj.config.word.AbstLexer;
import roj.config.word.Word;
import roj.math.MathUtils;
import roj.net.tcp.serv.util.Notify;
import roj.text.CharList;
import roj.util.ByteWriter;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/2/4 16:56
 */
public final class HTTPHeaderLexer extends AbstLexer {
    public HTTPHeaderLexer init(CharSequence s) {
        super.init(s);
        return this;
    }

    /**
     * 读词
     */
    public String readHttpWord() {
        last = last == null ? snapshot() : snapshot(last);

        CharList temp = this.found;
        temp.clear();

        while (hasNext()) {
            int c = next();
            switch (c) {
                case '\r':
                    if (remain() > 0 && offset(0) == '\n') {
                        next();
                        if (remain() > 2 && offset(0) == '\r' && offset(1) == '\n') {
                            next();
                            next();
                            return SharedConfig._SHOULD_EOF;
                        }
                    } else {
                        return SharedConfig._ERROR;
                    }
                    break;
                case ':':
                    if (next() != ' ') {
                        return SharedConfig._ERROR;
                    }

                    while ((c = next()) != '\r' || offset(0) != '\n') {
                        temp.append((char) c);
                    }
                    retract();

                    if (temp.length() == 0) {
                        return "";
                    }

                    return temp.toString();

                default: {
                    if (!WHITESPACE.contains(c)) {
                        retract();

                        while (hasNext()) {
                            c = next();

                            if (!WHITESPACE.contains(c) && c != ':') {
                                temp.append((char) c);
                            } else {
                                retract();
                                break;
                            }
                        }
                        if (temp.length() == 0) {
                            return null;
                        }

                        return temp.toString();
                    }
                }
            }
        }
        return SharedConfig._SHOULD_EOF;
    }

    /**
     * 读词
     */
    @Override
    public Word readWord() {
        return null;
    }

    /**
     * @return 其他字符
     */
    @Override
    protected Word readSpecial() {
        return null;
    }

    @Override
    protected Word formNumberClip(byte flag, CharList temp) {
        return null;
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

    public String readLine() {
        final CharList temp = this.found;
        temp.clear();

        char c = 0;
        while (hasNext() && (c = next()) != '\r' || offset(0) != '\n') {
            temp.append(c);
        }
        retract();

        if (temp.length() == 0) {
            return "";
        }

        return temp.toString();
    }
}
