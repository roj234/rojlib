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

package ilib.api.recipe;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;

import java.util.List;
import java.util.Map;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class AnvilRecipe {
    private final ItemStack input1;
    private final ItemStack input2;
    private final int expCost;
    private final ItemStack output;

    public static final Map<Item, List<AnvilRecipe>> REGISTRY = new MyHashMap<>();

    public AnvilRecipe(int exp, ItemStack input1, ItemStack input2, ItemStack output) {
        this.expCost = exp;
        this.input1 = input1;
        this.input2 = input2;
        this.output = output;
        REGISTRY.computeIfAbsent(input1.getItem(), (k) -> new SimpleList<>()).add(this);
    }

    public int getExpCost() {
        return expCost;
    }

    public ItemStack getInput1() {
        return this.input1;
    }

    public ItemStack getInput2() {
        return this.input2;
    }

    public ItemStack getOutput() {
        return this.output;
    }
}