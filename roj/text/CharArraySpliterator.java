package roj.text;

import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.IntConsumer;

/**
 * @author Roj233
 * @since 2022/3/13 19:39
 */
final class CharArraySpliterator implements Spliterator.OfInt {
    private final char[] array;
    private       int    index;  // current index, modified on advance/split
    private final int    fence;  // one past last index
    private final int    characteristics;

    public CharArraySpliterator(char[] array, int origin, int fence, int addChar) {
        this.array = array;
        this.index = origin;
        this.fence = fence;
        this.characteristics = addChar | Spliterator.SIZED | Spliterator.SUBSIZED;
    }

    @Override
    public OfInt trySplit() {
        int lo = index, mid = (lo + fence) >>> 1;
        return (lo >= mid) ? null : new CharArraySpliterator(array, lo, index = mid, characteristics);
    }

    @Override
    public void forEachRemaining(IntConsumer action) {
        if (action == null) throw new NullPointerException();
        char[] a;
        int i, hi; // hoist accesses and checks from loop
        if ((a = array).length >= (hi = fence) && (i = index) >= 0 && i < (index = hi)) {
            do {
                action.accept(a[i]);
            } while (++i < hi);
        }
    }

    @Override
    public boolean tryAdvance(IntConsumer action) {
        if (action == null) throw new NullPointerException();
        if (index >= 0 && index < fence) {
            action.accept(array[index++]);
            return true;
        }
        return false;
    }

    @Override
    public long estimateSize() {
        return fence - index;
    }

    @Override
    public int characteristics() {
        return characteristics;
    }

    @Override
    public Comparator<? super Integer> getComparator() {
        if (hasCharacteristics(Spliterator.SORTED)) return null;
        throw new IllegalStateException();
    }
}
