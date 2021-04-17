package roj.math;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/19 1:08
 */
public class MutableBoolean {
    public boolean value;

    public MutableBoolean() {
    }

    public MutableBoolean(boolean b) {
        this.value = b;
    }

    public void set(boolean b) {
        this.value = b;
    }

    public boolean get() {
        return this.value;
    }

    public boolean getSet(boolean b) {
        boolean v = value;
        value = b;
        return v;
    }
}
