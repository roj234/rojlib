package roj.util;

import java.util.Random;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: MyRandom.java.java
 */
public class MyRandom extends Random {
    static final long serialVersionUID = 0L;

    public MyRandom() {
        super(0);
        setSeed(System.identityHashCode(Runtime.getRuntime()) ^ System.identityHashCode(this) ^ System.currentTimeMillis());
    }

    public MyRandom(long seed) {
        super(seed);
    }

    @Override
    public int nextInt(int i) {
        if (i == 1) {
            return 0;
        }
        return super.nextInt(i);
    }
}
