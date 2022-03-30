/*
 * This file is a part of MoreItems
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
package ilib.util.freeze;

import ilib.util.MCTexts;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 冻结的物品
 *
 * @author Roj233
 * @since 2021/8/26 19:36
 */
public final class FreezedItem extends Item {
    public FreezedItem() {
        setHasSubtypes(true);
        setNoRepair();
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return "item.ilib.freezed";
    }

    @Override
    public String getTranslationKey() {
        return "item.ilib.freezed";
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return MCTexts.format(this.getTranslationKey(stack)).trim();
    }

    @Override
    public int hashCode() {
        return getRegistryName() == null ? 0 : getRegistryName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FreezedItem && ((FreezedItem) obj).getRegistryName().equals(getRegistryName());
    }
}
