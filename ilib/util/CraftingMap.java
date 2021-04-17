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
package ilib.util;

import roj.collect.UnsortedMultiKeyMap;

import net.minecraft.item.ItemStack;

import net.minecraftforge.oredict.OreDictionary;

import java.util.List;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/21 18:48
 */
public class CraftingMap {
    public static class StackComparator implements UnsortedMultiKeyMap.Keys<ItemStack, String> {
        public static final StackComparator COMPARE_ITEM = new StackComparator();
        public static final StackComparator INSTANCE = new StackComparator();

        @Override
        public List<String> getKeysFor(ItemStack key, List<String> holder) {
            int[] oreIds = net.minecraftforge.oredict.OreDictionary.getOreIDs(key);
            if (oreIds.length > 0) {
                for (int i : oreIds) {
                    holder.add(net.minecraftforge.oredict.OreDictionary.getOreName(i));
                }
            }
            String s = key.getItem().getRegistryName().toString();
            holder.add(s + key.getItemDamage());
            if(this == COMPARE_ITEM) {
                holder.add(s);
            }
            return holder;
        }

        @Override
        public String getMostFamous(ItemStack key) {
            int[] oreIds = net.minecraftforge.oredict.OreDictionary.getOreIDs(key);
            if (oreIds.length > 0) {
                return OreDictionary.getOreName(oreIds[0]);
            }
            return key.getItem().getRegistryName().toString() + key.getItemDamage();
        }
    }
}
