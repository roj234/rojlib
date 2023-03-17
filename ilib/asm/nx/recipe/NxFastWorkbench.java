package ilib.asm.nx.recipe;

import ilib.asm.Loader;
import ilib.asm.util.IOreIngredient;
import ilib.collect.StackComparator;
import ilib.misc.MCHooks;
import ilib.misc.MCHooks.RecipeCache;
import ilib.util.Registries;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.collect.IntSet;
import roj.collect.SimpleList;
import roj.collect.UnsortedMultiKeyMap;
import roj.util.Helpers;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
//!!AT [["net.minecraft.inventory.InventoryCrafting", ["field_70466_a"]]]
@Nixim("/")
abstract class NxFastWorkbench extends CraftingManager {
	@Copy(staticInitializer = "cacheRecipes", unique = true)
	public static UnsortedMultiKeyMap<ItemStack, String, List<IRecipe>> recipeSimple;
	@Copy(unique = true)
	public static List<IRecipe> recipeComplex;

	public static void cacheRecipes() {
		if (recipeSimple == null) recipeSimple = UnsortedMultiKeyMap.create(StackComparator.ALL, 9);
		else recipeSimple.clear();

		if (recipeComplex == null) recipeComplex = new SimpleList<>();
		else recipeComplex.clear();

		for (IRecipe r : Registries.recipe()) {
			if (r instanceof ShapedRecipes || r instanceof ShapelessRecipes ||
				r instanceof ShapedOreRecipe || r instanceof ShapelessOreRecipe) {
				List<Object> v = computeIngr(r, r.getIngredients());
				if (v != null) {
					recipeSimple.computeIfAbsent1(Helpers.cast(v), Helpers.fnArrayList()).add(r);
					continue;
				}
			}
			recipeComplex.add(r);
		}
		Loader.logger.info("统计: 优化/未优化的合成表 " + recipeSimple.size() + "/" + recipeComplex.size());
	}

	@Copy(unique = true)
	private static List<Object> computeIngr(IRecipe id, List<Ingredient> ings) {
		List<Object> stacks = new SimpleList<>(ings.size());
		IntSet commonOreDict = new IntSet(), tmp = new IntSet();

		for (int i = 0; i < ings.size(); i++) {
			Ingredient ing = ings.get(i);
			if (!ing.isSimple()) return null;
			if (ing instanceof IOreIngredient) {
				stacks.add(((IOreIngredient) ing).getOredict());
			} else {
				boolean b = MCHooks.tryFindOD(stacks, commonOreDict, tmp, ing.getMatchingStacks());
				if (b) {
					System.out.println("[GOC_Multi is being used] " + id.getRegistryName());
					return null;
				}
			}
		}

		return stacks;
	}

	@Inject
	public static ItemStack findMatchingResult(InventoryCrafting inv, World world) {
		IRecipe recipe = ((RecipeCache) inv).getRecipe();

		if (checkMatches(inv, world, recipe)) {
			recipe = findRecipe(inv, world);
		}

		return recipe == null ? ItemStack.EMPTY : recipe.getCraftingResult(inv);
	}

	@Copy(unique = true)
	private static boolean checkMatches(InventoryCrafting inv, World world, IRecipe recipe) {
		try {
			return recipe == null || !recipe.canFit(inv.getWidth(), inv.getHeight()) || !recipe.matches(inv, world);
		} catch (Throwable e) {
			return false;
		}
	}

	@Inject
	public static IRecipe findMatchingRecipe(InventoryCrafting inv, World world) {
		IRecipe recipe = ((RecipeCache) inv).getRecipe();

		if (checkMatches(inv, world, recipe)) {
			recipe = findRecipe(inv, world);
		}

		return recipe;
	}

	@Inject()
	public static NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv, World world) {
		IRecipe recipe = ((RecipeCache) inv).getRecipe();

		if (checkMatches(inv, world, recipe)) {
			recipe = findRecipe(inv, world);
		}

		return recipe == null ? inv.stackList : recipe.getRemainingItems(inv);
	}

	@Copy(unique = true)
	private static IRecipe findRecipe(InventoryCrafting inv, World world) {
		if (recipeSimple == null) {
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

		List<List<IRecipe>> staticRecipes = recipeSimple.getMulti(nonEmptyItems, 999, new SimpleList<>());
		for (int i = 0; i < staticRecipes.size(); i++) {
			List<IRecipe> recipes = staticRecipes.get(i);
			for (int j = 0; j < recipes.size(); j++) {
				IRecipe recipe = recipes.get(j);
				if (recipe.canFit(inv.getWidth(), inv.getHeight()) && recipe.matches(inv, world)) {
					((RecipeCache) inv).setRecipe(recipe);
					return recipe;
				}
			}
		}

		List<IRecipe> fallback = recipeComplex;
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
