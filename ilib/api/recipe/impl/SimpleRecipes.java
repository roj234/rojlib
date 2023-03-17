package ilib.api.recipe.impl;

import ilib.api.recipe.IDisplayableRecipeList;
import ilib.api.recipe.IModifiableRecipeList;
import ilib.api.recipe.IRecipe;
import ilib.api.recipe.IRecipeList;
import ilib.fluid.handler.IFluidProvider;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

@Deprecated
/**
 * @author Roj234
 * @since 2021/6/2 23:59
 */ public class SimpleRecipes implements IRecipeList, IDisplayableRecipeList, IModifiableRecipeList {
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

	public IRecipe contains(List<ItemStack> list, IFluidProvider machine) {
		for (IRecipe recipe : this.recipes) {
			if (recipe.matches(machine, list)) {
				return recipe;
			}
		}
		return null;
	}
}