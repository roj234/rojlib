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

// Thank to mekanism
package ilib.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import net.minecraftforge.common.util.Constants.NBT;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class ItemNBT {
    public static final String DATA_ID = "IL";

    public static NBTTagCompound getDataMap(ItemStack stack) {
        init(stack);

        return stack.getTagCompound().getCompoundTag(DATA_ID);
    }

    public static boolean hasData(ItemStack stack, String key) {
        if (getRootTagNullable(stack) == null)
            return false;
        init(stack);

        return getDataMap(stack).hasKey(key);
    }

    public static boolean hasData(ItemStack stack, String key, int type) {
        if (getRootTagNullable(stack) == null)
            return false;
        init(stack);

        return getDataMap(stack).hasKey(key, type);
    }

    public static void removeData(ItemStack stack, String key) {
        init(stack);

        getDataMap(stack).removeTag(key);
    }

    public static int getInt(ItemStack stack, String key) {
        init(stack);

        return getDataMap(stack).getInteger(key);
    }

    public static long getLong(ItemStack stack, String key) {
        init(stack);

        return getDataMap(stack).getLong(key);
    }

    public static boolean getBoolean(ItemStack stack, String key) {
        init(stack);

        return getDataMap(stack).getBoolean(key);
    }

    public static double getDouble(ItemStack stack, String key) {
        init(stack);

        return getDataMap(stack).getDouble(key);
    }

    public static float getFloat(ItemStack stack, String key) {
        init(stack);

        return getDataMap(stack).getFloat(key);
    }

    public static String getString(ItemStack stack, String key) {
        init(stack);

        return getDataMap(stack).getString(key);
    }

    public static NBTTagCompound getCompound(ItemStack stack, String key) {
        init(stack);

        return getDataMap(stack).getCompoundTag(key);
    }

    public static NBTTagList getList(ItemStack stack, String key) {
        init(stack);

        return getDataMap(stack).getTagList(key, NBT.TAG_COMPOUND);
    }

    public static void setInt(ItemStack stack, String key, int i) {
        init(stack);

        getDataMap(stack).setInteger(key, i);
    }

    public static void setLong(ItemStack stack, String key, long i) {
        init(stack);

        getDataMap(stack).setLong(key, i);
    }

    public static void setBoolean(ItemStack stack, String key, boolean b) {
        init(stack);

        getDataMap(stack).setBoolean(key, b);
    }

    public static void setDouble(ItemStack stack, String key, double d) {
        init(stack);

        getDataMap(stack).setDouble(key, d);
    }

    public static void setFloat(ItemStack stack, String key, float d) {
        init(stack);

        getDataMap(stack).setFloat(key, d);
    }

    public static void setString(ItemStack stack, String key, String s) {
        init(stack);

        getDataMap(stack).setString(key, s);
    }

    public static void setCompound(ItemStack stack, String key, NBTTagCompound tag) {
        init(stack);

        getDataMap(stack).setTag(key, tag);
    }

    public static void setList(ItemStack stack, String key, NBTTagList tag) {
        init(stack);

        getDataMap(stack).setTag(key, tag);
    }

    public static void setRootTag(ItemStack stack, NBTTagCompound tag) {
        init(stack);

        stack.getTagCompound().setTag(DATA_ID, tag);
    }

    public static NBTTagCompound getRootTag(ItemStack stack) {
        init(stack);

        return stack.getTagCompound().getCompoundTag(DATA_ID);
    }

    public static NBTTagCompound getRootTagNullable(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return null;
        if (!tag.hasKey(DATA_ID)) return null;
        return tag.getCompoundTag(DATA_ID);
    }

    private static void init(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            stack.setTagCompound(tag = new NBTTagCompound());
        }

        if (!tag.hasKey(DATA_ID)) {
            tag.setTag(DATA_ID, new NBTTagCompound());
        }
    }
}