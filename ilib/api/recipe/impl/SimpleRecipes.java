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
import ilib.api.recipe.IRecipeList;
import ilib.fluid.handler.IFluidProvider;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

@Deprecated/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/2 23:59
 */
public class SimpleRecipes implements IRecipeList, IDisplayableRecipeList, IModifiableRecipeList {
    public final List<IRecipe> recipes = new ArrayList<>();

    public SimpleRecipes() {
    }

    public void addRecipe(IRecipe recipe) {
        this.recipes.add(recipe);
    }

    public Collection<IRecipe> getDisplayableRecipes() {
        return this.recipes;
    }

    public boolean remove(String name) {
        boolean modified = false;
        Iterator<IRecipe> itr = recipes.iterator();
        while (itr.hasNext()) {
            if (itr.next().getName().equals(name)) {
                itr.remove();
                modified = true;
            }
        }
        return modified;
    }

    public IRecipe contains(List<ItemStack> list, IFluidProvider machine, @Nullable EntityPlayer player) {
        for (IRecipe recipe : this.recipes) {
            if (recipe.matches(machine, list)) {
                return recipe;
            }
        }
        return null;
    }
}