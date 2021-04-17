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

import ilib.api.recipe.IDisplayableRecipeList;
import ilib.api.recipe.IModifiableRecipeList;
import ilib.api.recipe.IRecipe;
import ilib.api.recipe.MultiInputRecipe;
import ilib.fluid.handler.IFluidProvider;
import ilib.util.CraftingMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import roj.collect.UnsortedMultiKeyMap;
import roj.math.MathUtils;
import roj.math.MutableBoolean;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/23 18:0
 */
public class AdvancedRecipeList implements IDisplayableRecipeList, IModifiableRecipeList {
    public final UnsortedMultiKeyMap<ItemStack, String, IRecipe> recipes;
    private final List<IRecipe> fallback = new ArrayList<>();
    private final List<IRecipe> display = new ArrayList<>();

    public AdvancedRecipeList(int machineInventorySize) {
        this.recipes = UnsortedMultiKeyMap.create(CraftingMap.StackComparator.INSTANCE, machineInventorySize);
    }

    public void addRecipe(IRecipe recipe) {
        display.add(recipe);
        if(recipe.isStandard() && !(recipe instanceof MultiInputRecipe)) {
            recipes.put(recipe.getInput(), recipe);
        } else {
            fallback.add(recipe);
        }
    }

    public Collection<IRecipe> getDisplayableRecipes() {
        return display;
    }

    @Override
    public boolean removeByInput(List<ItemStack[]> inputs) {
        MutableBoolean one = new MutableBoolean();
        MathUtils.dikaerProduct(inputs, (k) -> {
            if(recipes.remove(k, k.size()) != null)
                one.set(true);
        });
        return one.get();
    }

    public IRecipe contains(List<ItemStack> list, IFluidProvider machine, @Nullable EntityPlayer player) {
        for (IRecipe recipe : this.recipes.getMulti(list, 10)) {
            if (recipe.matches(machine, list)) {
                return recipe;
            }
        }
        for (IRecipe recipe : this.fallback) {
            if (recipe.matches(machine, list)) {
                return recipe;
            }
        }
        return null;
    }
}