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

import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class EnchantmentHelper extends net.minecraft.enchantment.EnchantmentHelper {
    public static boolean hasEnchantment(Enchantment ench, ItemStack is) {
        NBTTagList list = is.getItem() == Items.ENCHANTED_BOOK ? ItemEnchantedBook.getEnchantments(is) : is.getEnchantmentTagList();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            Enchantment found = Enchantment.getEnchantmentByID(tag.getShort("id"));
            if (found == ench)
                return true;
        }
        return false;
    }

    public static boolean canStackEnchant(ItemStack stack, Enchantment ench) {
        if (Items.ENCHANTED_BOOK == stack.getItem())
            return true;
        if (!ench.canApply(stack))
            return false;
        for (Enchantment enchCompare : net.minecraft.enchantment.EnchantmentHelper.getEnchantments(stack).keySet()) {
            if (enchCompare != null && enchCompare != ench && !enchCompare.isCompatibleWith(ench))
                return false;
        }
        return true;
    }

    public static int getApplyCost(Enchantment enchantment, int lvl) {
        int rarityFactor = 10;
        Enchantment.Rarity rarity = enchantment.getRarity();
        switch (rarity) {
            case COMMON:
                rarityFactor = 1;
                break;
            case UNCOMMON:
                rarityFactor = 2;
                break;
            case RARE:
                rarityFactor = 4;
                break;
            case VERY_RARE:
                rarityFactor = 8;
                break;
        }
        return rarityFactor * lvl;
    }
}