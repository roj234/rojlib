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

package ilib.math;

import net.minecraft.util.math.BlockPos;
import roj.collect.LongMap;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class SelectionCache {
    private static final LongMap<Arena> map = new LongMap<>();

    public static Arena set(long id, int s, BlockPos pos) {
        Arena arena = map.get(id);
        if (arena == null) map.put(id, arena = new Arena());
        if (s == 1) arena.setPos1(pos);
        else arena.setPos2(pos);
        return arena;
    }

    public static boolean remove(long id) {
        return null != map.remove(id);
    }

    public static Arena get(long id) {
        return map.get(id);
    }

    public static boolean has(long id) {
        return map.containsKey(id);
    }
}