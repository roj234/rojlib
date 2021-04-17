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
package ilib.api.recipe.impl;

import ilib.api.recipe.BaseRecipe;
import ilib.api.recipe.IRecipe;
import ilib.fluid.handler.IFluidProvider;
import ilib.util.InventoryUtil;
import roj.collect.IBitSet;
import roj.util.Helpers;
import roj.util.Idx;

import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public abstract class AbstractItemRecipe extends BaseRecipe implements IRecipe {
    public final List<ItemStack> output;
    protected IBitSet keepInputIds;

    public AbstractItemRecipe(String name, int mePerTick, int tickCost, boolean shaped) {
        super(name, mePerTick, tickCost, shaped);
        this.output = new ArrayList<>();
    }

    public AbstractItemRecipe(String name, int me, int tick, boolean shaped, List<ItemStack> output) {
        super(name, me, tick, shaped);
        this.output = output;
    }

    @Override
    public boolean isStandard() {
        return true;
    }

    @Nonnull
    @Override
    public List<ItemStack> operateInput(@Nonnull IFluidProvider fluidProvider, @Nonnull List<ItemStack> input) {
        return isShaped() ? operateItemsShaped(this, input) : operateItemsShapeless(this, input);
    }

    /**
     * 有序合成物品处理函数
     *
     * @param recipe 合成
     * @param input  输入
     * @return 合成后的物品列表
     */
    public static List<ItemStack> operateItemsShaped(IRecipe recipe, List<ItemStack> input) {
        if (recipe.getInput().size() != input.size()) {
            throw new IllegalArgumentException("[MI有序-" + recipe.getName() + "]物品列表在合成结束前已经改变");
        }
        int i = 0;
        for (ItemStack is : recipe.getInput()) {
            if (is == null) {
                i++;
                continue;
            }
            ItemStack stack = input.get(i);
            if (!IRecipe.stackEquals(stack, is) || stack.getCount() < is.getCount()) {
                throw new IllegalStateException("[MI有序-" + recipe.getName() + "]物品在合成结束前已经改变");
            }
            if (recipe.willConsume(i))
                IRecipe.decrStackSize(input, i, is.getCount());
            i++;
        }
        return input;
    }

    /**
     * 无序合成物品处理函数
     *
     * @param recipe 合成
     * @param input  输入
     * @return 合成后的物品列表
     */
    public static List<ItemStack> operateItemsShapeless(IRecipe recipe, List<ItemStack> input) {
        Idx idx = new Idx(input.size());
        outer:
        for (ItemStack is : recipe.getInput()) {
            PrimitiveIterator.OfInt itr = idx.remains();
            while (itr.hasNext()) {
                int i = itr.nextInt();
                ItemStack stack = input.get(i);
                if (IRecipe.stackEquals(stack, is) && stack.getCount() >= is.getCount()) {
                    idx.add(i);
                    if (recipe.willConsume(i))
                        IRecipe.decrStackSize(input, i, is.getCount());
                    continue outer;
                }
            }
            throw new IllegalStateException("[MI无序-" + recipe.getName() + "]物品在合成结束前已经改变");
        }
        if (!idx.isFull() && recipe.getInput().size() > 0) {
            throw new IllegalStateException("[MI无序-" + recipe.getName() + "]没有找到所有的输入");
        }
        return input;
    }

    public <T extends AbstractItemRecipe> T addOutput(ItemStack is) {
        if (is == null || is.isEmpty())
            throw new IllegalArgumentException("Output must not be empty or null;");
        this.output.add(is);
        return Helpers.cast(this);
    }

    @Override
    public boolean willConsume(int index) {
        return keepInputIds == null || !keepInputIds.contains(index);
    }

    // 包括位置
    @Override
    public boolean matches(@Nonnull IFluidProvider fluidProvider, @Nonnull List<ItemStack> list) {
        return isShaped() ? matchesShaped(this, list) : matchesShapeless(this, list);
    }

    /**
     * 有序合成匹配
     *
     * @param recipe 合成
     * @param list   输入
     * @return 是否匹配
     */
    public static boolean matchesShaped(IRecipe recipe, List<ItemStack> list) {
        List<ItemStack> input0 = recipe.getInput();
        if (list.size() != input0.size()) {
            throw new IllegalArgumentException("[MI有序-" + recipe.getName() + "]物品列表大小不对! 需求: " + list.size() + ", 实际: " + input0.size());
        }

        for (int i = 0, j = input0.size(); i < j; i++) {
            ItemStack craft = input0.get(i);
            ItemStack stack = list.get(i);
            if (craft == null) {
                if (!stack.isEmpty()) {
                    return false;
                }
            } else if (!InventoryUtil.areItemStacksEqual(stack, craft) || stack.getCount() < craft.getCount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 无序合成匹配
     *
     * @param recipe 合成
     * @param list   输入
     * @return 是否匹配
     */
    public static boolean matchesShapeless(IRecipe recipe, List<ItemStack> list) {
        List<ItemStack> input0 = recipe.getInput();
        int inputLen = input0.size();

        if (list.size() < inputLen) return false;

        Idx recipeSlot = new Idx(inputLen);

        outer:
        for (ItemStack stack : list) {
            if (stack.isEmpty()) {
                continue;
            }

            // 不止一种输入的合成
            if (recipeSlot.isFull()) {
                return inputLen == 1;
            }

            PrimitiveIterator.OfInt itr = recipeSlot.remains();
            while (itr.hasNext()) {
                int j = itr.nextInt();
                ItemStack craft = input0.get(j);
                if (InventoryUtil.areItemStacksEqual(stack, craft) && stack.getCount() >= craft.getCount()) {
                    recipeSlot.add(j);
                    continue outer;
                }
            }
            return false;
        }
        return recipeSlot.isFull();
    }

    @Override
    public final List<ItemStack> getOutput() {
        return this.output;
    }
}
