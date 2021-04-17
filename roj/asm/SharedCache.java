package roj.asm;

import roj.util.ByteList;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/28 23:14
 */
public final class SharedCache {
    private static final ThreadLocal<ByteList[]> BUFFERS = new ThreadLocal<ByteList[]>() {
        @Override
        protected ByteList[] initialValue() {
            return new ByteList[]{
                    new ByteList(4096), // MAIN
                    new ByteList(4096), // POOL
                    new ByteList() // READ
            };
        }
    };

    public static ByteList bufCstPool() {
        return BUFFERS.get()[1];
    }

    public static ByteList bufGlobal() {
        return BUFFERS.get()[0];
    }

    public static ByteList bufRead() {
        return BUFFERS.get()[2];
    }
}
