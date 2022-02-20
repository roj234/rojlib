package roj.config.serial;

import roj.config.word.AbstLexer;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/1/29 8:44
 */
public abstract class StreamSerializer {
    protected static final int END = 1, NEXT = 2, MAP = 4, LIST = 8;

    protected StringBuilder sb = new StringBuilder();
    protected int flag;

    public void setSb(StringBuilder sb) {
        this.sb = sb;
    }

    private int[] levels = new int[16];
    protected int level;

    public final void value(int l) {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        sb.append(l);
        flag = f >= LIST ? f | NEXT : f | END;
    }

    public final void value(String l) {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        AbstLexer.addSlashes(l, sb.append('"')).append('"');
        flag = f >= LIST ? f | NEXT : f | END;
    }

    public final void value(long l) {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        sb.append(l);
        flag = f >= LIST ? f | NEXT : f | END;
    }

    public final void value(double l) {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        sb.append(l);
        flag = f >= LIST ? f | NEXT : f | END;
    }

    public final void value(boolean l) {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        sb.append(l);
        flag = f >= LIST ? f | NEXT : f | END;
    }

    public final void valueNull() {
        int f = this.flag;
        if ((f & END) != 0) throw new IllegalStateException("EOF");
        if (f == (NEXT|LIST)) listNext();
        sb.append("null");
        flag = f >= LIST ? f | NEXT : f | END;
    }

    protected final void push(int data) {
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
    public final void pop() {
        if (level == 0) throw new IllegalStateException("level = 0");
        int i = --level / 8;
        int j = (level & 7) << 2;

        endLevel();
        flag = (levels[i] >>> j) & 15;
    }

    protected abstract void listNext();
    protected abstract void endLevel();

    public abstract void valueMap();
    public abstract void valueList();

    public abstract void key(String key);

    public final StringBuilder getValue() {
        while (level > 0) pop();
        return sb;
    }

    public void reset() {
        level = 0;
        flag = 0;
        sb.setLength(0);
    }
}
