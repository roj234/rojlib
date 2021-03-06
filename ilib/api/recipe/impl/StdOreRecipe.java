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

import ilib.api.recipe.IRecipe;
import ilib.api.recipe.MultiInputRecipe;
import ilib.fluid.handler.IFluidProvider;
import ilib.util.InventoryUtil;
import roj.collect.MyBitSet;
import roj.util.Helpers;
import roj.util.Idx;

import net.minecraft.item.ItemStack;

import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class StdOreRecipe extends AbstractItemRecipe implements MultiInputRecipe {
    final List<ItemStack[]> input;

    public StdOreRecipe(String name, int me, int tick) {
        this(name, me, tick, false);
    }

    public StdOreRecipe(String name, int me, int tick, boolean shaped) {
        super(name, me, tick, shaped);
        this.input = new ArrayList<>();
    }

    public StdOreRecipe(String name, int me, int tick, boolean shaped, List<ItemStack[]> input, List<ItemStack> output) {
        super(name, me, tick, shaped, output);
        this.input = input;
    }

    /**
     * Notice: Different count is supported
     */
    public StdOreRecipe addMultiInput(ItemStack[] stacks) {
        this.input.add(stacks);
        return this;
    }

    public StdOreRecipe addOreInput(String ore, int count) {
        if (count < 0) throw new UnsupportedOperationException("Ore non-consume was not supported.");
        List<ItemStack> ores = OreDictionary.getOres(ore, false);
        if (ores.isEmpty()) {
            throw new IllegalArgumentException("The ore specified (" + ore + ") doesn't has item.");
        }

        ItemStack[] targetStacks = new ItemStack[ores.size()];
        int i = 0;
        for (ItemStack stack : ores) {
            if (stack.getCount() != count) {
                stack = stack.copy();
                stack.setCount(count);
            }
            targetStacks[i++] = stack;
        }
        this.input.add(targetStacks);
        return this;
    }

    public <T extends StdOreRecipe> T addInput(ItemStack stack) {
        if (stack.isEmpty()) {
            stack = stack.copy();
            stack.setCount(1);
            if (stack.isEmpty())
                throw new NullPointerException("Non-consume stack is really empty");
            if (keepInputIds == null) {
                keepInputIds = new MyBitSet(this.input.size() + 1);
            }
            keepInputIds.add(this.input.size());
        }
        this.input.add(new ItemStack[]{stack});

        return Helpers.cast(this);
    }

    public boolean matches(@Nonnull IFluidProvider fp, @Nonnull List<ItemStack> list) {
        return isShaped() ? matchesOreShaped(this, list) : matchesOreShapeless(this, list);
    }

    /**
     * ????????????????????????????????????
     *
     * @param recipe ??????
     * @param list   ??????
     * @return ??????????????????
     */
    public static boolean matchesOreShapeless(@Nonnull MultiInputRecipe recipe, @Nonnull List<ItemStack> list) {
        List<ItemStack[]> input0 = recipe.getMultiInputs();
        int inputLen = input0.size();
        if (list.size() < inputLen) return false;

        Idx recipeSlot = new Idx(inputLen);

        outer:
        for (ItemStack stack : list) {
            if (stack.isEmpty()) {
                continue;
            }

            if (recipeSlot.isFull()) { // ???????????????????????????????????????????????????
                return inputLen == 1;
            }

            PrimitiveIterator.OfInt itr = recipeSlot.remains();
            while (itr.hasNext()) {
                int j = itr.nextInt();
                ItemStack[] crafts = input0.get(j);
                for (ItemStack crafting : crafts) {
                    if (InventoryUtil.areItemStacksEqual(crafting, stack) &&
                            stack.getCount() >= crafting.getCount()) {
                        //PlayerUtil.broadcastAll("Succeed at " + list.indexOf(stack) + ", " + j);
                        recipeSlot.add(j);
                        continue outer;
                    }
                }
            }
//            PlayerUtil.broadcastAll(recipe.getValue() + " Failed at " + list.indexOf(stack) + " cause not ore matches: " + stack);
//            itr = recipeSlot.remains();
//            while (itr.hasNext()) {
//                int j = itr.nextInt();
//                ItemStack[] crafts = input0.get(j);
//                PlayerUtil.broadcastAll(j + ":" + Arrays.toString(crafts));
//            }
            return false;
        }
        return recipeSlot.isFull();
    }

    /**
     * ????????????????????????????????????
     *
     * @param recipe ??????
     * @param list   ??????
     * @return ??????????????????
     */
    public static boolean matchesOreShaped(@Nonnull MultiInputRecipe recipe, @Nonnull List<ItemStack> list) {
        List<ItemStack[]> input0 = recipe.getMultiInputs();
        int inputLen = input0.size();
        if (list.size() != input0.size()) {
            throw new IllegalArgumentException("[MI????????????-" + recipe.getName() + "]????????????????????????! ??????: " + list.size() + ", ??????: " + input0.size());
        }

        outer:
        for (int i = 0, k = list.size(); i < k; i++) {
            ItemStack stack = list.get(i);
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack[] crafts = input0.get(i);
            for (ItemStack crafting : crafts) {
                if (InventoryUtil.areItemStacksEqual(crafting, stack) &&
                        stack.getCount() >= crafting.getCount()) {
                    continue outer;
                }
            }
            return false;
        }
        return true;
    }

    @Nonnull
    @Override
    public List<ItemStack> operateInput(@Nonnull IFluidProvider fp, @Nonnull List<ItemStack> input) {
        return isShaped() ? operateMultiShaped(this, input) : operateMultiShapeless(this, input);
    }

    /**
     * ????????????????????????????????????
     *
     * @param recipe ??????
     * @param input  ??????
     * @return ????????????????????????
     */
    public static List<ItemStack> operateMultiShaped(MultiInputRecipe recipe, List<ItemStack> input) {
        int inputLen = input.size();
        List<ItemStack[]> list = recipe.getMultiInputs();
        if (inputLen != list.size()) throw new IllegalStateException("NFSIZEError");

        outer:
        for (int i = 0; i < inputLen; i++) {
            ItemStack selfStack = input.get(i);
            if (selfStack.isEmpty()) {
                continue;
            }
            ItemStack[] stacks = list.get(i);
            for (ItemStack recipeStack : stacks) {
                if (IRecipe.stackEquals(selfStack, recipeStack)) {
                    if (recipe.willConsume(i))
                        IRecipe.decrStackSize(input, i, recipeStack.getCount());
                    continue outer;
                }
            }
            throw new IllegalStateException("[MI??????-" + recipe.getName() + "] slotId " + i + ":  ??????????????????");
        }
        return input;
    }

    /**
     * ????????????????????????????????????
     *
     * @param recipe ??????
     * @param input  ??????
     * @return ????????????????????????
     */
    public static List<ItemStack> operateMultiShapeless(@Nonnull MultiInputRecipe recipe, @Nonnull List<ItemStack> input) {
        List<ItemStack[]> list = recipe.getMultiInputs();
        Idx idx = new Idx(list.size());

        int inputLen = input.size();
        outer:
        for (int i = 0; i < inputLen; i++) {
            ItemStack selfStack = input.get(i);
            if (selfStack.isEmpty()) {
                continue;
            }

            PrimitiveIterator.OfInt itr = idx.remains();
            while (itr.hasNext()) {
                int j = itr.nextInt();
                ItemStack[] stacks = list.get(j);
                for (ItemStack recipeStack : stacks) {
                    if (IRecipe.stackEquals(selfStack, recipeStack)) {
                        if (recipe.willConsume(j))
                            IRecipe.decrStackSize(input, i, recipeStack.getCount());
                        idx.add(j);
                        continue outer;
                    }
                }
            }
            throw new IllegalStateException("[MI??????-" + recipe.getName() + "] slotId " + i + ":  ??????????????????");
        }
        if (!idx.isFull()) {
            throw new IllegalStateException("[MI??????-" + recipe.getName() + "] ??????`??????`????????????");
        }
        return input;
    }

    public final List<ItemStack[]> getMultiInputs() {
        return this.input;
    }
}