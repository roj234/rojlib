package ilib.api.recipe.impl;

import ilib.api.recipe.IDisplayableRecipeList;
import ilib.api.recipe.IRecipe;
import ilib.api.recipe.IRecipeList;
import ilib.fluid.handler.IFluidProvider;

import net.minecraft.item.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class SingleRecipeList implements IRecipeList, IDisplayableRecipeList {
	private final List<IRecipe> recipes;
	public final IRecipe recipe;

	public SingleRecipeList(IRecipe recipe) {
		this.recipe = recipe;
		this.recipes = Collections.singletonList(recipe);
	}

	@Override
	public Collection<IRecipe> getDisplayableRecipes() {
		return this.recipes;
	}

	@Override
	public IRecipe contains(List<ItemStack> list, IFluidProvider machine) {
		return recipe.matches(machine, list) ? recipe : null;
	}
}