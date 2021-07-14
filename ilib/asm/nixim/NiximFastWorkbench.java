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

import ilib.asm.util.MethodEntryPoint;
import ilib.util.PlayerUtil;
import ilib.util.Registries;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.RemapTo;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.item.crafting.CraftingManager")
public abstract class NiximFastWorkbench extends CraftingManager {
    @RemapTo("func_82787_a")
    public static ItemStack findMatchingResult(InventoryCrafting inv, World world) {
        MethodEntryPoint.getCachedRecipeAcc().setInstance(inv);
        IRecipe recipe = (IRecipe) MethodEntryPoint.getCachedRecipeAcc().getObject();

        if (recipe == null || !recipe.canFit(inv.getWidth(), inv.getHeight()) || !recipe.matches(inv, world)) {
            recipe = null;
            for (IRecipe recipe1 : Registries.recipe()) {
                if (recipe1.canFit(inv.getWidth(), inv.getHeight()) && recipe1.matches(inv, world)) {
                    MethodEntryPoint.getCachedRecipeAcc().setObject(recipe = recipe1);
                    break;
                }
            }
        } else {
            PlayerUtil.broadcastAll("Cache hit a!");
        }

        MethodEntryPoint.getCachedRecipeAcc().clearInstance();

        return recipe == null ? null : recipe.getCraftingResult(inv);
    }

    @RemapTo("func_192413_b")
    public static IRecipe findMatchingRecipe(InventoryCrafting inv, World world) {
        MethodEntryPoint.getCachedRecipeAcc().setInstance(inv);
        IRecipe recipe = (IRecipe) MethodEntryPoint.getCachedRecipeAcc().getObject();

        if (recipe == null || !recipe.canFit(inv.getWidth(), inv.getHeight()) || !recipe.matches(inv, world)) {
            recipe = null;
            for (IRecipe recipe1 : Registries.recipe()) {
                if (recipe1.canFit(inv.getWidth(), inv.getHeight()) && recipe1.matches(inv, world)) {
                    MethodEntryPoint.getCachedRecipeAcc().setObject(recipe = recipe1);
                    break;
                }
            }
        } else {
            PlayerUtil.broadcastAll("Cache hit b!");
        }

        MethodEntryPoint.getCachedRecipeAcc().clearInstance();

        return recipe;
    }

    @RemapTo("func_180303_b")
    public static NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv, World world) {
        MethodEntryPoint.getCachedRecipeAcc().setInstance(inv);
        IRecipe recipe = (IRecipe) MethodEntryPoint.getCachedRecipeAcc().getObject();

        if (recipe == null || !recipe.canFit(inv.getWidth(), inv.getHeight()) || !recipe.matches(inv, world)) {
            for (IRecipe recipe1 : Registries.recipe()) {
                if (recipe1.matches(inv, world)) {
                    MethodEntryPoint.getCachedRecipeAcc().setObject(recipe = recipe1);
                    MethodEntryPoint.getCachedRecipeAcc().clearInstance();
                    return recipe.getRemainingItems(inv);
                }
            }
        } else {
            PlayerUtil.broadcastAll("Cache hit c!");
        }

        MethodEntryPoint.getCachedRecipeAcc().clearInstance();

        return inv.stackList;
    }
}
