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

import ilib.autoreg.TileRegister;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import roj.collect.Int2IntMap;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@TileRegister("ilib:storage")
public class TileStorage extends TileBase {
    private final Int2IntMap data = new Int2IntMap();
    private String type = DEFAULT_TYPE;
    public static final String DEFAULT_TYPE = "[UNKNOWN]";

    public TileStorage() {
        super();
    }

    public TileStorage(String type) {
        super();
        this.type = type;
    }

    public TileStorage(int meta) {
        super();
        data.put(0, meta);
    }

    public int getOr(int a, int b) {
        return data.getOrDefault(a, b);
    }

    public Integer get(int id) {
        return data.get(id);
    }

    public void set(int id, int value) {
        data.put(id, value);
        if (!getWorld().isRemote)
            markForDataUpdate();
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound tag) {
        super.writeToNBT(tag);

        NBTTagList list = new NBTTagList();
        for (Int2IntMap.Entry entry : data.entrySet()) {
            NBTTagCompound tag2 = new NBTTagCompound();
            tag2.setInteger("k", entry.getKey());
            tag2.setInteger("v", entry.getValue());
            list.appendTag(tag2);
        }
        tag.setTag("data", list);
        tag.setString("type", type);

        return tag;
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound tag) {
        super.readFromNBT(tag);

        NBTTagList list = tag.getTagList("data", 10);
        data.clear();
        if (!list.isEmpty()) {
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag3 = list.getCompoundTagAt(i);
                data.put(tag3.getInteger("k"), tag3.getInteger("v"));
            }
        }
        type = tag.hasKey("type") ? tag.getString("type") : DEFAULT_TYPE;
    }

}