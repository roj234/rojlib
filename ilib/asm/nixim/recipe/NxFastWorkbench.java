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
package ilib.asm.nixim.recipe;

import ilib.misc.MCHooks;
import ilib.misc.MCHooks.RecipeCache;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.collect.SimpleList;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
//!!AT [["net.minecraft.inventory.InventoryCrafting", ["field_70466_a"]]]
@Nixim("net.minecraft.item.crafting.CraftingManager")
abstract class NxFastWorkbench extends CraftingManager {
    @Inject("func_82787_a")
    public static ItemStack findMatchingResult(InventoryCrafting inv, World world) {
        IRecipe recipe = ((RecipeCache) inv).getRecipe();

        if (recipe == null || !recipe.canFit(inv.getWidth(), inv.getHeight()) || !recipe.matches(inv, world)) {
            recipe = findRecipe(inv, world);
        }

        return recipe == null ? ItemStack.EMPTY : recipe.getCraftingResult(inv);
    }

    @Inject("func_192413_b")
    public static IRecipe findMatchingRecipe(InventoryCrafting inv, World world) {
        IRecipe recipe = ((RecipeCache) inv).getRecipe();

        if (recipe == null || !recipe.canFit(inv.getWidth(), inv.getHeight()) || !recipe.matches(inv, world)) {
            recipe = findRecipe(inv, world);
        }

        return recipe;
    }

    @Inject("func_180303_b")
    public static NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv, World world) {
        IRecipe recipe = ((RecipeCache) inv).getRecipe();

        if (recipe == null || !recipe.canFit(inv.getWidth(), inv.getHeight()) || !recipe.matches(inv, world)) {
            recipe = findRecipe(inv, world);
        }

        return recipe == null ? inv.stackList : recipe.getRemainingItems(inv);
    }

    @Copy
    private static IRecipe findRecipe(InventoryCrafting inv, World world) {
        if (MCHooks.mcRecipes == null) {
            for (IRecipe recipe : REGISTRY) {
                if (recipe.matches(inv, world)) return recipe;
            }
            return null;
        }

        NonNullList<ItemStack> items = inv.stackList;
        SimpleList<ItemStack> nonEmptyItems = new SimpleList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) nonEmptyItems.add(stack);
        }

        List<List<IRecipe>> recipess = MCHooks.mcRecipes.getMulti(nonEmptyItems, 999, new SimpleList<>());

        for (int i = 0; i < recipess.size(); i++) {
            List<IRecipe> recipes = recipess.get(i);
            for (int j = 0; j < recipes.size(); j++) {
                IRecipe recipe = recipes.get(j);
                if (recipe.canFit(inv.getWidth(), inv.getHeight()) && recipe.matches(inv, world)) {
                    ((RecipeCache) inv).setRecipe(recipe);
                    return recipe;
                }
            }
        }

        List<IRecipe> fallback = MCHooks.fallbackRecipes;
        for (int i = 0; i < fallback.size(); i++) {
            IRecipe recipe = fallback.get(i);
            if (recipe.canFit(inv.getWidth(), inv.getHeight()) && recipe.matches(inv, world)) {
                ((RecipeCache) inv).setRecipe(recipe);
                return recipe;
            }
        }
        return null;
    }
}
