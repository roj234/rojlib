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
package ilib.asm.nixim;

import ilib.asm.util.MCHooks;
import ilib.asm.util.MCHooks.RecipeCache;
import ilib.util.PlayerUtil;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.item.crafting.CraftingManager")
public abstract class NiximFastWorkbench extends CraftingManager {
    @Inject("func_82787_a")
    public static ItemStack findMatchingResult(InventoryCrafting inv, World world) {
        IRecipe recipe = ((RecipeCache) inv).getRecipe();

        if (recipe == null || !recipe.canFit(inv.getWidth(), inv.getHeight()) || !recipe.matches(inv, world)) {
            recipe = findRecipe(inv, world);
        } else {
            PlayerUtil.broadcastAll("[FW]Match1: " + recipe);
        }

        return recipe == null ? null : recipe.getCraftingResult(inv);
    }

    @Inject("func_192413_b")
    public static IRecipe findMatchingRecipe(InventoryCrafting inv, World world) {
        IRecipe recipe = ((RecipeCache) inv).getRecipe();

        if (recipe == null || !recipe.canFit(inv.getWidth(), inv.getHeight()) || !recipe.matches(inv, world)) {
            recipe = findRecipe(inv, world);
        } else {
            PlayerUtil.broadcastAll("[FW]Match2: " + recipe);
        }

        return recipe;
    }

    @Inject("func_180303_b")
    public static NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv, World world) {
        IRecipe recipe = ((RecipeCache) inv).getRecipe();

        if (recipe == null || !recipe.canFit(inv.getWidth(), inv.getHeight()) || !recipe.matches(inv, world)) {
            recipe = findRecipe(inv, world);
        } else {
            PlayerUtil.broadcastAll("[FW]Match3: " + recipe);
        }

        return recipe == null ? inv.stackList : recipe.getRemainingItems(inv);
    }

    @Copy
    private static IRecipe findRecipe(InventoryCrafting inv, World world) {
        List<List<IRecipe>> multi = MCHooks.mcRecipes.getMulti(inv.stackList, 999);
        PlayerUtil.broadcastAll("[FW]StdTotal: " + multi.size());
        for (int i = 0; i < multi.size(); i++) {
            List<IRecipe> recipe1 = multi.get(i);
            for (int j = 0; j < recipe1.size(); j++) {
                IRecipe recipe2 = recipe1.get(j);
                if (recipe2.canFit(inv.getWidth(), inv.getHeight()) && recipe2.matches(inv, world)) {
                    ((RecipeCache) inv).setRecipe(recipe2);
                    PlayerUtil.broadcastAll("[FW]Found: " + recipe2);
                    return recipe2;
                }
            }
        }
        for (IRecipe recipe2 : MCHooks.fallbackRecipes) {
            if (recipe2.canFit(inv.getWidth(), inv.getHeight()) && recipe2.matches(inv, world)) {
                ((RecipeCache) inv).setRecipe(recipe2);
                PlayerUtil.broadcastAll("[FW]Found: " + recipe2);
                return recipe2;
            }
        }
        PlayerUtil.broadcastAll("[FW]FoundNone");
        return null;
    }
}
