package roj.config.serial;

import roj.config.ParseException;
import roj.config.word.AbstLexer;
import roj.config.word.Word;
import roj.config.word.WordPresets;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/1/29 8:44
 */
public abstract class StreamDeserializer {
    protected static final int END = 1, NEXT = 2, MAP = 4, LIST = 8;

    // same as JSONParser
    public static final int TRUE = 10, FALSE = 11, NULL = 12;

    protected AbstLexer wr;
    protected Word w;
    protected boolean peek;
    protected Word poll() throws ParseException {
        if (peek) {
            peek = false;
            return w;
        }

        w = wr.readWord();
        wr.recycle(w);
        return w;
    }
    protected Word peek() throws ParseException {
        if (!peek) {
            w = wr.readWord();
            wr.recycle(w);
            peek = true;
        }
        return w;
    }

    public StreamDeserializer(AbstLexer wr) {
        this.wr = wr;
    }

    public StreamDeserializer setText(CharSequence text) {
        wr.init(text);
        return this;
    }

    public final ParseException unexcept(String type) {
        return wr.err("期待 " + type + " 得到 " + w.val() + " (id: " + w.type() + ")");
    }

    protected int flag;

    private int[] levels = new int[16];
    protected int level;

    public final int forInt() throws ParseException {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        if (poll().type() == WordPresets.INTEGER) {
            flag = f >= LIST ? f | NEXT : f | END;
            return w.number().asInt();
        } else {
            throw unexcept("int");
        }
    }

    public final String forString() throws ParseException {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        if (checkString()) {
            flag = f >= LIST ? f | NEXT : f | END;
            return w.val();
        } else {
            throw unexcept("string");
        }
    }

    public final long forLong() throws ParseException {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        switch (poll().type()) {
            case WordPresets.LONG:
            case WordPresets.INTEGER:
                flag = f >= LIST ? f | NEXT : f | END;
                return w.number().asLong();
            default:
                throw unexcept("long");
        }
    }

    public final double forDouble() throws ParseException {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        switch (poll().type()) {
            case WordPresets.LONG:
            case WordPresets.INTEGER:
            case WordPresets.DECIMAL_D:
            case WordPresets.DECIMAL_F:
                flag = f >= LIST ? f | NEXT : f | END;
                return w.number().asDouble();
            default:
                throw unexcept("double");
        }
    }

    public final boolean forBoolean() throws ParseException {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        switch (poll().type()) {
            case TRUE:
            case FALSE:
                return w.type() == TRUE;
            default:
                throw unexcept("boolean");
        }
    }

    public final void forNull() throws ParseException {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        if (poll().type() == NULL) {
            flag = f >= LIST ? f | NEXT : f | END;
            return;
        }
        throw unexcept("boolean");
    }

    protected final void push(int data) throws ParseException {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        f = f >= LIST ? f | NEXT : f | END;

        // 4bits
        int i = level / 8;
        int j = (level++ & 7) << 2;

        int[] LV = this.levels;
        if (LV.length < i) {
            this.levels = LV = Arrays.copyOf(LV, i);
        }
        LV[i] = (LV[i] & ~(15 << j)) | (f << j);

        flag = data;
    }
    public final void pop() throws ParseException {
        if (level == 0) throw new IllegalStateException("level = 0");
        int i = --level / 8;
        int j = (level & 7) << 2;

        endLevel();
        flag = (levels[i] >>> j) & 15;
    }

    protected abstract boolean checkString() throws ParseException;
    protected abstract void listNext() throws ParseException;
    protected abstract void endLevel() throws ParseException;

    public abstract void forMap() throws ParseException;
    public abstract void forList() throws ParseException;

    public abstract String key() throws ParseException;
}
