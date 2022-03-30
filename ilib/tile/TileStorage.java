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

package ilib.tile;

import ilib.api.TileRegister;
import roj.collect.Int2IntMap;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@TileRegister("ilib:storage")
public class TileStorage extends TileBase {
    private final Int2IntMap data = new Int2IntMap();

    public TileStorage() {
        super();
    }

    public int getOr(int a, int b) {
        return data.getOrDefault(a, b);
    }

    public Integer get(int id) {
        return data.get(id);
    }

    public void set(int id, int value) {
        data.put(id, value);
        if (!world.isRemote) sendDataUpdate();
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        int[] array = new int[data.size() << 1];
        int i = 0;
        for (Int2IntMap.Entry entry : data.entrySet()) {
            array[i++] = entry.getKey();
            array[i++] = entry.v;
        }

        NBTTagCompound tag = new NBTTagCompound();
        tag.setIntArray("data", array);
        return tag;
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound tag) {
        int[] array = new int[data.size() << 1];
        int i = 0;
        for (Int2IntMap.Entry entry : data.entrySet()) {
            array[i++] = entry.getKey();
            array[i++] = entry.v;
        }
        tag.setIntArray("data", array);

        return super.writeToNBT(tag);
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound tag) {
        super.readFromNBT(tag);

        int[] list = tag.getIntArray("data");
        data.clear();
        if (list.length > 0 && (list.length & 1) == 0) {
            for (int i = 0; i < list.length; ) {
                data.put(list[i++], list[i++]);
            }
        }
    }

}