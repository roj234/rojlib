package roj.asm.mapper.obf.policy;

import java.util.Random;

/**
 * Confusing chars
 *
 * @author Roj233
 * @since 2021/7/18 19:29
 */
public final class CharMixture extends SimpleNamer {
    public int min, max, nonFirstOffset;
    public char[] chars;

    public CharMixture() {}

    public CharMixture(String seq, int min, int max) {
        chars = seq.toCharArray();
        this.min = min;
        this.max = max;
    }

    public static CharMixture newIII(int min, int max) {
        CharMixture mix = new CharMixture();
        mix.min = min;
        mix.max = max;
        mix.chars = new char[] {
                '1', 'I', 'i', 'l'
        };
        mix.nonFirstOffset = 1;
        return mix;
    }

    public static CharMixture newABC(int min, int max) {
        CharMixture mix = new CharMixture();
        mix.min = min;
        mix.max = max;
        mix.chars = new char[] {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
        };
        mix.nonFirstOffset = 10;
        return mix;
    }

    @Override
    public String obfName0(Random rand) {
        int length = rand.nextInt(max - min + 1) + min;
        buf.append(chars[nonFirstOffset + rand.nextInt(chars.length - nonFirstOffset)]);

        while (--length > 0) {
            buf.append(chars[rand.nextInt(chars.length)]);
        }

        String s = buf.toString();
        buf.clear();
        return s;
    }
}
