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
package ilib.asm.nixim.client;

import ilib.asm.nixim.FastSearchTree;
import ilib.asm.util.MCHooks;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.FilterList;
import roj.collect.MyHashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.recipebook.RecipeList;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.util.RecipeBookClient;
import net.minecraft.client.util.SearchTree;
import net.minecraft.client.util.SearchTreeManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/1 12:16
 */
@Nixim("net.minecraft.client.Minecraft")
public class NiximPinyinSearch extends Minecraft {
    public NiximPinyinSearch(GameConfiguration gameConfig) {
        super(gameConfig);
    }

    @Shadow("field_193995_ae")
    SearchTreeManager searchTreeManager;

    @Override
    @Inject("func_193986_ar")
    public void populateSearchTreeManager() {
        SearchTree<ItemStack> stackTree = new FastSearchTree<>(MCHooks::getItemInformation, (stack) -> Collections.singleton(Item.REGISTRY.getNameForObject(stack.getItem())));

        NonNullList<ItemStack> items = new NonNullList<>(new FilterList<>((old, latest) -> {
            stackTree.add(latest);
            return false;
        }), ItemStack.EMPTY);

        for (Item item : Item.REGISTRY) {
            item.getSubItems(CreativeTabs.SEARCH, items);
        }

        SearchTree<RecipeList> recipeTree = new FastSearchTree<>((recipes) -> {
            final List<IRecipe> list = recipes.getRecipes();
            List<String> list1 = new ArrayList<>(list.size() * 5);
            for (IRecipe recipe : list) {
                list1.addAll(MCHooks.getItemInformation(recipe.getRecipeOutput()));
            }
            return list1;
        }, (recipes) -> {
            Set<ResourceLocation> list = new MyHashSet<>(recipes.getRecipes().size());
            for (IRecipe recipe : recipes.getRecipes()) {
                list.add(Item.REGISTRY.getNameForObject(recipe.getRecipeOutput().getItem()));
            }
            return list;
        });

        for (RecipeList recipes : RecipeBookClient.ALL_RECIPES) {
            recipeTree.add(recipes);
        }

        this.searchTreeManager.register(SearchTreeManager.ITEMS, stackTree);
        this.searchTreeManager.register(SearchTreeManager.RECIPES, recipeTree);
    }
}
