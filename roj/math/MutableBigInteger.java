package roj.math;

/**
 * Mutable Big Integer , WIP
 *
 * @author Roj233
 * @since 2021/7/8 0:35
 */
public class MutableBigInteger {
    public int[] number;
    public int dotIndex;

    private MutableBigInteger() {

    }

    public MutableBigInteger(String number) {

    }

    public MutableBigInteger copy() {
        MutableBigInteger mbi = new MutableBigInteger();
        mbi.number = number.clone();
        mbi.dotIndex = dotIndex;
        return mbi;
    }
}
