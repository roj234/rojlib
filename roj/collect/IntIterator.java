package roj.collect;

import javax.annotation.Nonnull;
import java.util.PrimitiveIterator;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: IntIterator.java.java
 */
public
interface IntIterator extends PrimitiveIterator.OfInt {
    @Nonnull
    default OfInt reset() {
        throw new UnsupportedOperationException();
    }
}
