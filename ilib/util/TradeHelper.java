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

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipe;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class TradeHelper {
    public static EntityVillager.ITradeList withChance(EntityVillager.ITradeList list, double chance) {
        return (merchant, recipeList, random) -> {
            boolean i = random.nextDouble() < chance;
            if (i) {
                list.addMerchantRecipe(merchant, recipeList, random);
            }
        };
    }

    public static EntityVillager.ITradeList toEmerald(ItemStack item, int min, int max) {
        return toEmerald(item, new EntityVillager.PriceInfo(min, max));
    }

    public static EntityVillager.ITradeList toEmerald(ItemStack item, EntityVillager.PriceInfo buy) {
        return (merchant, recipeList, random) -> {
            int i = buy.getPrice(random);
            recipeList.add(new MerchantRecipe(getStack(item, (i < 0) ? 1 : i), getStack(Items.EMERALD, (i < 0) ? -i : 1)));
        };
    }

    public static EntityVillager.ITradeList toEmerald(ItemStack item, ItemStack item2, int min, int max) {
        return toEmerald(item, item2, new EntityVillager.PriceInfo(min, max));
    }

    public static EntityVillager.ITradeList toEmerald(ItemStack item, ItemStack item2, EntityVillager.PriceInfo buy) {
        return (merchant, recipeList, random) -> {
            int i = buy.getPrice(random);
            int i2 = buy.getPrice(random);
            recipeList.add(new MerchantRecipe(getStack(item, (i < 0) ? 1 : i), getStack(item, (i2 < 0) ? 1 : i2), getStack(Items.EMERALD, (i < 0) ? -i : 1)));
        };
    }

    public static EntityVillager.ITradeList fromEmerald(ItemStack item, int min, int max) {
        return fromEmerald(item, new EntityVillager.PriceInfo(min, max));
    }

    public static EntityVillager.ITradeList fromEmerald(ItemStack item, EntityVillager.PriceInfo sell) {
        return (merchant, recipeList, random) -> {
            int i = sell.getPrice(random);
            recipeList.add(new MerchantRecipe(getStack(Items.EMERALD, (i < 0) ? 1 : i), getStack(item, (i < 0) ? -i : 1)));
        };
    }

    public static EntityVillager.ITradeList std(ItemStack in1, ItemStack in2, ItemStack out) {
        return (merchant, recipeList, random) -> {
            recipeList.add(new MerchantRecipe(in1, in2, out));
        };
    }

    public static EntityVillager.ITradeList enchanted(ItemStack item, int min, int max) {
        return enchanted(item, new EntityVillager.PriceInfo(min, max));
    }

    public static EntityVillager.ITradeList enchanted(ItemStack item, EntityVillager.PriceInfo buy) {
        return (merchant, recipeList, random) -> {
            int i = buy.getPrice(random);

            ItemStack result = EnchantmentHelper.addRandomEnchantment(random, getStack(item, 1), 5 + random.nextInt(15), false);
            recipeList.add(new MerchantRecipe(getStack(Items.EMERALD, (i < 0) ? 1 : i), result));
        };
    }

    public static ItemStack getStack(ItemStack item, int count) {
        item = item.copy();
        item.setCount(count);
        return item;
    }

    public static ItemStack getStack(Item item, int count) {
        return new ItemStack(item, count);
    }
}