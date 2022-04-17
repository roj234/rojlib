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
package ilib.asm.util;

import roj.collect.AbstractIterator;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import java.util.Iterator;

/**
 * @author Roj234
 * @since  2020/8/23 0:15
 */
public class MergedItr extends AbstractIterator<Chunk> {
    final ChunkProviderServer w;
    final Iterator<ChunkPos> pers;
    final Iterator<Chunk> chunkIterator;

    public MergedItr(WorldServer w, Iterator<ChunkPos> persistent, Iterator<Chunk> chunkIterator) {
        this.w = w.getChunkProvider();
        this.pers = persistent;
        this.chunkIterator = chunkIterator;
    }

    /**
     * @return true if currentObject is updated, false if not elements
     */
    @Override
    public boolean computeNext() {
        if (pers.hasNext()) {
            ChunkPos pos = pers.next();
            result = w.loadChunk(pos.x, pos.z);
            return true;
        }

        boolean flag = chunkIterator.hasNext();
        if (flag) {
            result = chunkIterator.next();
        }
        return flag;
    }
}
