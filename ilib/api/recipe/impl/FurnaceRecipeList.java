package ilib.api.recipe.impl;

import ilib.api.recipe.IRecipe;
import ilib.api.recipe.IRecipeList;
import ilib.fluid.handler.IFluidProvider;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class FurnaceRecipeList implements IRecipeList {
	private static final Map<ItemStack, IRecipe> cached = new HashMap<>();

	public FurnaceRecipeList() {
	}

	public static ItemStack getSmeltingResultForItem(ItemStack stack) {
		return FurnaceRecipes.instance().getSmeltingResult(stack);
	}

	public IRecipe contains(List<ItemStack> list, IFluidProvider machine) {
		ItemStack inStack = list.get(0);
		ItemStack result = getSmeltingResultForItem(inStack);
		inStack = inStack.copy();
		inStack.setCount(1);
		if (result.isEmpty()) return null;
		IRecipe recipe = cached.get(inStack);
		if (recipe == null) {
			recipe = new FurnaceRecipe(inStack, result);
			cached.put(inStack, recipe);
		}
		return recipe;
	}
}