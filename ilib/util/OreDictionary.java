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

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class OreDictionary {
    private static Map<String, ItemStack> oreList = new HashMap<>();

    private static final ItemInfo itemAIR = new ItemInfo(Items.AIR, 0);

    /**
     * 给物品注册矿物词典
     *
     * @param oreName 以空格分隔多个
     */
    public static void set(Item item, int meta, String oreName) {
        ItemStack is = new ItemStack(item, 1, meta);
        if (oreName == null) {
            throw new IllegalArgumentException("Couldn't register item " + is + " to oredict NULL");
        }
        if (oreName.equals("cancel")) return;
        if (itemIsAir(is)) {
            throw new IllegalArgumentException("Couldn't register air item to oredict " + oreName);
        }
        final String[] names = oreName.split(",");
        for (final String name : names)
            oreList.put(name, is);
    }

    /**
     * 给物品注册矿物词典
     *
     * @param oreName 以空格分隔多个
     */
    public static void set(Item item, String oreName) {
        set(item, 0, oreName);
    }

    /**
     * 给方块注册矿物词典
     *
     * @param oreName 以空格分隔多个
     */
    public static void set(Block block, String oreName) {
        set(block, 0, oreName);
    }

    /**
     * 给方块注册矿物词典
     *
     * @param oreName 以空格分隔多个
     */
    public static void set(Block block, int meta, String oreName) {
        ItemStack is = new ItemStack(block, 1, meta);
        if (oreName == null) {
            throw new IllegalArgumentException("Couldn't register block " + is + " to oredict NULL");
        }
        if (oreName.equals("cancel")) return;
        if (blockIsAir(is)) {
            throw new IllegalArgumentException("Couldn't register air block to oredict " + oreName);
        }
        String[] names = oreName.split(" ");
        for (String name : names)
            oreList.put(name, is);
    }

    private static boolean itemIsAir(ItemStack is) {
        return ItemInfo.get(is).equals(itemAIR);
    }

    private static boolean blockIsAir(ItemStack is) {
        return BlockInfo.get(is).equals(BlockInfo.AIR);
    }

    public static void init() {
        for (Map.Entry<String, ItemStack> entry : oreList.entrySet()) {
            net.minecraftforge.oredict.OreDictionary.registerOre(entry.getKey(), entry.getValue());
        }
        oreList.clear();
        oreList = null;
    }
}
