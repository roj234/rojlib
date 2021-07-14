package lac.server.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/10 14:16
 */
public class ComboRandom extends Random {
    final long[] seeds;
    int i = 0;

    private static final long multiplier = 0x5DEECE66DL;
    private static final long addend = 0xBL;
    private static final long mask = (1L << 48) - 1;

    public ComboRandom(long[] randoms) {
        this.seeds = randoms;
    }

    @Override
    protected int next(int bits) {
        long seed = seeds[i == seeds.length ? i = 0 : i];
        seed = (seed * multiplier + addend) & mask;
        seeds[i++] = seed;
        return (int)(seed >>> (48 - bits));
    }

    public static ComboRandom from(String keys) {
        List<Long> randoms = new ArrayList<>();
        long tmp = 0;
        int i = 0;
        while (i < keys.length()) {
            char c = keys.charAt(i++);
            tmp = tmp * 31 + c;
            if((i & 31) == 0) {
                randoms.add(tmp);
                if((i & 63) == 0)
                    tmp <<= 1;
                else
                    tmp --;
            }
        }
        randoms.add(tmp);
        long[] seed = new long[randoms.size()];
        for (int j = 0; j < randoms.size(); j++) {
            seed[j] = randoms.get(j);
        }

        return new ComboRandom(seed);
    }
}
