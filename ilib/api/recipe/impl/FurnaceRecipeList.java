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
import ilib.api.recipe.IRecipeList;
import ilib.fluid.handler.IFluidProvider;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class FurnaceRecipeList implements IRecipeList {
    private static final Map<ItemStack, IRecipe> cached = new HashMap<>();

    public FurnaceRecipeList() {
    }

    public static ItemStack getSmeltingResultForItem(ItemStack stack) {
        return FurnaceRecipes.instance().getSmeltingResult(stack);
    }

    public IRecipe contains(List<ItemStack> list, IFluidProvider machine, @Nullable EntityPlayer player) {
        ItemStack inStack = list.get(0);
        ItemStack result = getSmeltingResultForItem(inStack);
        inStack = inStack.copy();
        inStack.setCount(1);
        if (result.isEmpty())
            return null;
        IRecipe recipe = cached.get(inStack);
        if (recipe == null) {
            recipe = new FurnaceRecipe(inStack, result);
            cached.put(inStack, recipe);
        }
        return recipe;
    }
}