package ilib.api.recipe.impl;

import ilib.api.recipe.IDisplayableRecipeList;
import ilib.api.recipe.IModifiableRecipeList;
import ilib.api.recipe.IRecipe;
import ilib.api.recipe.MultiInputRecipe;
import ilib.collect.StackComparator;
import ilib.fluid.handler.IFluidProvider;
import ilib.misc.MCHooks;
import roj.collect.IntSet;
import roj.collect.SimpleList;
import roj.collect.UnsortedMultiKeyMap;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/23 18:0
 */
public class UMKRecipeList implements IDisplayableRecipeList, IModifiableRecipeList {
	public final UnsortedMultiKeyMap<ItemStack, String, IRecipe> recipes;
	private final List<IRecipe> fallback = new ArrayList<>();
	private final List<IRecipe> display = new ArrayList<>();

	public UMKRecipeList(int machineInventorySize) {
		this.recipes = UnsortedMultiKeyMap.create(StackComparator.DAMAGE_ONLY, machineInventorySize);
	}

	public void addRecipe(IRecipe recipe) {
		display.add(recipe);
		if (recipe.isStandard()) {
			if (recipe instanceof MultiInputRecipe) {
				List<ItemStack[]> arr = ((MultiInputRecipe) recipe).getMultiInputs();

				List<Object> stacks = new SimpleList<>(arr.size());
				IntSet tmp1 = new IntSet(), tmp = new IntSet();

				for (int i = 0; i < arr.size(); i++) {
					MCHooks.tryFindOD(stacks, tmp1, tmp, arr.get(i));
				}

				recipes.computeIfAbsentMulti(String.class, stacks, list -> recipe);
			} else {
				recipes.put(recipe.getInput(), recipe);
			}
		} else {
			fallback.add(recipe);
		}
	}


	public Collection<IRecipe> getDisplayableRecipes() {
		return display;
	}

	@Override
	public boolean removeByInput(List<ItemStack[]> inputs) {
		// todo Handle
		//return recipes.remove(inputs) != null;
		return false;
	}

	public IRecipe contains(List<ItemStack> list, IFluidProvider machine) {
		List<IRecipe> recipes = this.recipes.getMulti(list, 10);
		for (int i = 0; i < recipes.size(); i++) {
			IRecipe recipe = recipes.get(i);
			if (recipe.matches(machine, list)) return recipe;
		}

		recipes = this.fallback;
		for (int i = 0; i < recipes.size(); i++) {
			IRecipe recipe = recipes.get(i);
			if (recipe.matches(machine, list)) return recipe;
		}
		return null;
	}
}