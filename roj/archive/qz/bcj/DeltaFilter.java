package roj.archive.qz.bcj;

import roj.util.ArrayCache;

/**
 * @since 2023/5/28 11:15
 */
public class DeltaFilter extends Filter {
    static final int DISTANCE_MIN = 1;
    static final int DISTANCE_MAX = 256;
    static final int DISTANCE_MASK = DISTANCE_MAX - 1;

    final int distance;
    final byte[] history = ArrayCache.getDefaultCache().getByteArray(DISTANCE_MAX, true);

    public DeltaFilter(boolean encoder, int distance) {
        super(encoder);

        if (distance < DISTANCE_MIN || distance > DISTANCE_MAX)
            throw new IllegalArgumentException();
        this.distance = distance;
    }

    @Override
    protected int filter(byte[] b, int off, int len) {
        int end = off + len;
        if (encode) {
            while (off < end) {
                byte tmp = history[(distance + pos) & DISTANCE_MASK];
                history[pos-- & DISTANCE_MASK] = b[off];
                b[off] -= tmp;

                off++;
            }
        } else {
            while (off < end) {
                b[off] += history[(distance + pos) & DISTANCE_MASK];
                history[pos-- & DISTANCE_MASK] = b[off];

                off++;
            }
        }
        return len;
    }
}
