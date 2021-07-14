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

package ilib.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ItemInfo {
    private static final ThreadLocal<ItemInfo> CACHE = ThreadLocal.withInitial(ItemInfo::new);

    public Item item;
    public int meta;

    private ItemInfo() {
    }

    public ItemInfo(ItemStack stack) {
        set(stack);
    }

    public ItemInfo(Item i, int j) {
        item = i;
        meta = j;
    }

    public static ItemInfo get(ItemStack stack) {
        return CACHE.get().set(stack);
    }

    private ItemInfo set(ItemStack stack) {
        item = stack.getItem();
        meta = stack.getItemDamage();
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        return obj instanceof ItemInfo &&
                ((ItemInfo) obj).item == item &&
                ((ItemInfo) obj).meta == meta;
    }

    public int getMeta() {
        return this.meta;
    }

    public Item getItem() {
        return this.item;
    }

    @Override
    public int hashCode() {
        int code = 1;
        code = 31 * code + System.identityHashCode(item);
        code = 7 * code + meta;
        return code;
    }

}