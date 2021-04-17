/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.asm;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.asm.tree.Clazz;
import roj.asm.tree.ConstantData;
import roj.util.ByteList;
import roj.util.ByteReader;

/**
 * @author Roj234
 * @version 1.0
 * @since  2020/10/28 23:14
 */
public final class SharedBuf {
    private static final ThreadLocal<Level> BUFFERS = ThreadLocal.withInitial(Level::new);

    @Internal
    public static ByteList i_get() {
        return BUFFERS.get().pool;
    }

    public static Level alloc() {
        return BUFFERS.get();
    }

    static ByteList store(ConstantData data) {
        Level arr = BUFFERS.get();
        return data.getBytes(arr.pool, arr.current());
    }

    static ByteList store(Clazz clazz) {
        Level arr = BUFFERS.get();
        return clazz.getBytes(arr.pool, arr.current());
    }

    static ByteReader reader(ByteList data) {
        ByteReader r = BUFFERS.get().sharedReader;
        r.refresh(data);
        return r;
    }

    public static final class Level {
        static final int LEVEL_MAX = 6;

        ByteList   pool = new ByteList();
        ByteReader sharedReader;
        ByteList[] buffers = new ByteList[LEVEL_MAX];
        int level;

        Level() {
            for (int i = 0; i < LEVEL_MAX; i++) {
                buffers[i] = new ByteList();
            }
            level = 0;
            sharedReader = new ByteReader();
        }

        ByteList current() {
            if (level == LEVEL_MAX) return new ByteList(4096);
            return buffers[level];
        }

        public boolean setLevel(boolean add) {
            if (add) {
                if (level >= LEVEL_MAX) return false;
                level++;
            } else {
                if (level == 0) return false;
                level--;
            }
            return true;
        }
    }
}
