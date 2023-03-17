/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: RecipeUtils.java
 */
package ilib.util;

import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import ilib.ImpLib;
import ilib.misc.DummyRecipe;
import roj.collect.*;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryModifiable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * RecipeUtil supplies several methods for easing the process of removing existing recipes,
 * as well as a helper method for making a 9x9 grid of the same item (nuggets to ingots, etc)
 * <p>
 * Automatic generate JSON
 * Hot reload Recipes
 */
public class RecipeUtil {
	private static final char SPACE = ' ';
	private static CharMap<Ingredient> ingMap = new CharMap<>();
	private static Map<String, ResourceLocation> resourceCache = new MyHashMap<>();

	public static void afterInit() {
		ingMap = null;
		resourceCache = null;
	}

	public static IRecipe addShapedRecipe(Block result, Object... components) {
		return addShapedRecipe(new ItemStack(result), components);
	}

	public static IRecipe addShapedRecipe(ItemStack result, Object... components) {
		return addShapedRecipe(null, result, components);
	}

	public static IRecipe addShapedRecipe(@Nullable String key, ItemStack result, Object... components) {
		String $$MODID = ForgeUtil.getCurrentModId();

		List<String> pattern = new ArrayList<>();
		int i = 0;
		while (i < components.length && components[i] instanceof String) {
			pattern.add((String) components[i++]);
		}

		boolean od = false;

		ingMap.clear();

		Character c = null;
		for (; i < components.length; i++) {
			Object o = components[i];
			if (o instanceof Character) {
				if (c != null) throw new IllegalArgumentException("Duplicate key");
				c = (Character) o;
			} else {
				if (c == null) throw new IllegalArgumentException("Key missing");
				if (o instanceof String) od = true;
				ingMap.put(c, serializeItem(o));
				c = null;
			}
		}

		String _id = key != null ? key : (result.getItem().getRegistryName().getPath() + (result.getItem().getHasSubtypes() ? "_" + result.getItemDamage() : ""));
		String id = _id;
		ResourceLocation loc = new ResourceLocation($$MODID, id);

		int copyNum = 0;
		IForgeRegistry<IRecipe> registry = Registries.recipe();
		while (registry.containsKey(loc)) {
			id = _id + "_alt" + ++copyNum;
			loc = new ResourceLocation($$MODID, id);
		}

		IRecipe recipe = od ? shapedOreRecipeFactory(id, ingMap, pattern, result, true) : shapedRecipeFactory(id, ingMap, pattern, result);

		registry.register(recipe.setRegistryName(loc));
		return recipe;
	}

	public static IRecipe addShapelessRecipe(Block result, Object... components) {
		return addShapelessRecipe(null, new ItemStack(result), components);
	}

	public static IRecipe addShapelessRecipe(ItemStack result, Object... components) {
		return addShapelessRecipe(null, result, components);
	}

	public static IRecipe addShapelessRecipe(@Nullable String key, ItemStack result, Object... components) {
		String $$MODID = ForgeUtil.getCurrentModId();

		boolean od = false;
		List<Ingredient> ingredients = new ArrayList<>();
		for (Object o : components) {
			if (o instanceof String) od = true;
			ingredients.add(serializeItem(o));
		}

		String _id = key != null ? key : (result.getItem().getRegistryName().getPath() + (result.getItem().getHasSubtypes() ? "_" + result.getItemDamage() : ""));
		String id = _id;
		ResourceLocation loc = new ResourceLocation($$MODID, id);

		int copyNum = 0;
		IForgeRegistry<IRecipe> registry = Registries.recipe();
		while (registry.containsKey(loc)) {
			id = _id + "_alt" + ++copyNum;
			loc = new ResourceLocation($$MODID, id);
		}

		IRecipe recipe = od ? shapelessOreRecipeFactory(id, ingredients, result) : shapelessRecipeFactory(id, ingredients, result);

		registry.register(recipe.setRegistryName(loc));
		return recipe;
	}


	public static IRecipe shapedOreRecipeFactory(@Nullable String group, CharMap<Ingredient> keys, List<String> pattern, ItemStack result, boolean mirrored) {
		if (keys.containsKey(SPACE)) {
			throw new JsonSyntaxException("' ' is an reserved name.");
		}

		keys.put(' ', Ingredient.EMPTY);

		if (pattern.isEmpty()) {
			throw new JsonSyntaxException("Invalid pattern: empty pattern not allowed");
		} else {
			int w = pattern.get(0).length();
			for (int i = 0; i < pattern.size(); ++i) {
				if (i > 0 && w != pattern.get(i).length()) {
					throw new JsonSyntaxException("Invalid pattern: each row must be the same width");
				}
			}

			CraftingHelper.ShapedPrimer primer = new CraftingHelper.ShapedPrimer();
			primer.width = w;
			primer.height = pattern.size();
			primer.mirrored = mirrored;
			primer.input = NonNullList.withSize(primer.width * primer.height, Ingredient.EMPTY);

			IntSet remain = new IntSet(keys.size());
			for (CharMap.Entry<Ingredient> entry : keys.selfEntrySet()) {
				int i = entry.getChar();
				if (i != ' ') remain.add(i);
			}

			int x = 0;
			for (String line : pattern) {
				for (int i = 0; i < line.length(); i++) {
					char c = line.charAt(i);
					Ingredient ing = keys.get(c);
					if (ing == null) {
						System.out.println(keys);
						throw new JsonSyntaxException("Symbol '" + c + "' is not defined");
					}
					primer.input.set(x++, ing);
					remain.remove(c);
				}
			}

			if (!remain.isEmpty()) {
				throw new JsonSyntaxException("Symbols not used in pattern" + pattern + ": " + remain);
			} else {
				return new ShapedOreRecipe(resourceCache.computeIfAbsent(group, (r) -> r == null ? null : new ResourceLocation(r)), result, primer);
			}
		}
	}

	public static IRecipe shapedRecipeFactory(@Nullable String group, CharMap<Ingredient> keys, List<String> pattern, ItemStack result) {
		if (keys.containsKey(SPACE)) {
			throw new JsonSyntaxException("' ' is an reserved name.");
		}

		keys.put(' ', Ingredient.EMPTY);

		if (pattern.isEmpty()) {
			throw new JsonSyntaxException("Invalid pattern: empty pattern not allowed");
		} else {
			final int width = pattern.get(0).length();
			final int height = pattern.size();

			for (int i = 0; i < height; ++i) {
				if (i > 0 && width != pattern.get(i).length()) {
					throw new JsonSyntaxException("Invalid pattern: each row must  be the same width");
				}
			}

			NonNullList<Ingredient> input = NonNullList.withSize(width * height, Ingredient.EMPTY);

			IntSet remain = new IntSet(keys.size());
			for (CharMap.Entry<Ingredient> entry : keys.selfEntrySet()) {
				int i = entry.getChar();
				if (i != ' ') remain.add(i);
			}

			int x = 0;
			for (String line : pattern) {
				for (int i = 0; i < line.length(); i++) {
					char c = line.charAt(i);
					Ingredient ing = keys.get(c);
					if (ing == null) {
						System.out.println(keys);
						throw new JsonSyntaxException("Symbol '" + c + "' is not defined");
					}
					input.set(x++, ing);
					remain.remove(c);
				}
			}

			if (!remain.isEmpty()) {
				throw new JsonSyntaxException("Symbols not used in pattern" + pattern + ": " + remain);
			} else {
				return new ShapedRecipes(group == null ? "" : group, width, height, input, result);
			}
		}
	}

	public static IRecipe shapelessOreRecipeFactory(@Nullable String group, List<Ingredient> ings, ItemStack result) {
		NonNullList<Ingredient> list = NonNullList.withSize(ings.size(), Ingredient.EMPTY);
		int i = 0;
		for (Ingredient ing : ings) {
			list.set(i++, ing);
		}
		if (list.isEmpty()) {
			throw new JsonParseException("No ingredients for shapeless recipe");
		} else if (list.size() > 9) {
			throw new JsonParseException("Too many ingredients for shapeless recipe");
		}
		return new ShapelessOreRecipe(resourceCache.computeIfAbsent(group, (r) -> r == null ? null : new ResourceLocation(r)), list, result);
	}

	public static IRecipe shapelessRecipeFactory(@Nullable String group, List<Ingredient> ings, ItemStack result) {
		NonNullList<Ingredient> list = NonNullList.withSize(ings.size(), Ingredient.EMPTY);
		int i = 0;
		for (Ingredient ing : ings) {
			list.set(i++, ing);
		}
		if (list.isEmpty()) {
			throw new JsonParseException("No ingredients for shapeless recipe");
		} else if (list.size() > 9) {
			throw new JsonParseException("Too many ingredients for shapeless recipe");
		}
		return new ShapelessRecipes(group, result, list);
	}

	private static Ingredient serializeItem(Object thing) {
		if (thing instanceof Item) {
			return serializeItem(new ItemStack((Item) thing));
		} else if (thing instanceof Block) {
			return serializeItem(new ItemStack((Block) thing));
		} else if (thing instanceof ItemStack) {
			ItemStack stack = ((ItemStack) thing).copy();
			stack.setCount(1);
			return Ingredient.fromStacks(stack);
		}
		if (thing instanceof String) {
			List<ItemStack> list = OreDictionary.getOres((String) thing, false);
			if (list.isEmpty()) {
				ImpLib.logger().error("od name " + thing + " does not have any item!");
				return serializeItem(new ItemStack(Blocks.BEDROCK));
			}
			ItemStack[] stacks = list.toArray(new ItemStack[list.size()]);
			for (int i = 0; i < stacks.length; i++) {
				if (stacks[i].getCount() != 1) {
					stacks[i] = stacks[i].copy();
					stacks[i].setCount(1);
				}
			}
			return Ingredient.fromStacks(stacks);
		}

		throw new IllegalArgumentException(thing + " is not a block, item, stack, or od name!");
	}

	public static void removeRecipe(IRecipe recipe) {
		removeRecipe((IForgeRegistryModifiable<IRecipe>) Registries.recipe(), recipe);
	}

	public static void removeRecipeByModId(String modid) {
		IForgeRegistryModifiable<IRecipe> registry = (IForgeRegistryModifiable<IRecipe>) Registries.recipe();
		for (IRecipe recipe : registry) {
			if (recipe.getRegistryName().getNamespace().equals(modid)) {
				removeRecipe(registry, recipe);
			}
		}
	}

	public static void removeRecipe(ResourceLocation path) {
		IForgeRegistryModifiable<IRecipe> registry = (IForgeRegistryModifiable<IRecipe>) Registries.recipe();
		IRecipe recipe = registry.getValue(path);
		if (recipe == null) {
			ImpLib.logger().warn("Removed Recipe: NOT exist: " + path);
			return;
		}
		removeRecipe(registry, recipe);
	}

	public static void removeRecipe(IForgeRegistryModifiable<IRecipe> registry, IRecipe recipe) {
		registry.remove(recipe.getRegistryName());
		registry.register(DummyRecipe.from(recipe));


		ImpLib.logger().info("Removed Recipe: " + recipe.getRegistryName());
	}

	public static void removeSmelting(ItemStack stack) {
		ItemStack result;
		Map<ItemStack, ItemStack> recipes = FurnaceRecipes.instance().getSmeltingList();
		for (Iterator<Map.Entry<ItemStack, ItemStack>> itr = recipes.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<ItemStack, ItemStack> entry = itr.next();
			result = entry.getValue();
			if (InventoryUtil.areItemStacksEqual(stack, result)) {
				ImpLib.logger().debug("Removed Smelting: " + entry.getKey() + " -> " + result);
				itr.remove();
			}
		}
	}

	@Nullable
	public static IRecipe getFirstRecipeWithOutput(ItemStack output) {
		for (IRecipe recipe : Registries.recipe()) {
			if (InventoryUtil.areItemStacksEqual(output, recipe.getRecipeOutput())) {
				return recipe;
			}
		}
		return null;
	}

	/**
	 * Storage recipes: 2x2/3x3同一物品合成一个物品
	 */
	public static List<IRecipe> getStorageRecipes() {
		List<IRecipe> storages = new ArrayList<>();

		cyl:
		for (IRecipe recipe : Registries.recipe()) {
			if (recipe.canFit(1, 1)) continue;
			if (recipe instanceof IShapedRecipe) {
				IShapedRecipe shp = (IShapedRecipe) recipe;
				if (shp.getRecipeWidth() != shp.getRecipeHeight()) continue;
			}
			Ingredient one = null;
			int ing = 0;
			for (Ingredient s : recipe.getIngredients()) {
				if (s != Ingredient.EMPTY) {
					if (one == null) {one = s;} else if (one != s) {
						ItemStack[] s1 = one.getMatchingStacks();
						ItemStack[] s2 = s.getMatchingStacks();
						if (s1.length != s2.length) {
							continue cyl;
						}
						for (int i = 0; i < s1.length; i++) {
							if (!InventoryUtil.areItemStacksEqual(s1[i], s2[i])) continue cyl;
						}
					}
					ing++;
				}
			}
			if (ing == 4 || ing == 9) storages.add(recipe);
		}
		return storages;
	}

	public static Flippable<IRecipe, IRecipe> getCirculatedStorageRecipes() {
		Flippable<IRecipe, IRecipe> map = new HashBiMap<>();

		cyl:
		for (IRecipe recipe : Registries.recipe()) {
			ItemStack result = recipe.getRecipeOutput();
			if (result.getCount() != 4 && result.getCount() != 9) continue;
			if (!recipe.canFit(1, 1)) continue;
			if (recipe instanceof IShapedRecipe) {
				IShapedRecipe shp = (IShapedRecipe) recipe;
				if (shp.getRecipeWidth() != shp.getRecipeHeight()) continue;
			}
			Ingredient one = null;
			// only one non-empty ingredient
			for (Ingredient s : recipe.getIngredients()) {
				if (s != Ingredient.EMPTY) {
					if (one == null) {one = s;} else continue cyl;
				}
			}

			if (one != null) {
				IRecipe crafter = getFirstRecipeWithOutput(one.getMatchingStacks()[0]);
				if (crafter != null) {
					map.put(crafter, recipe);
				}
			}
		}

		return map;
	}

	/**
	 * Attempts to locate a similar recipe using a different material.<br>
	 * Largely speaking this will only ever match tools and armor (picks, swords, armor, etc.)
	 *
	 * @param tpl - an existing recipe to match against
	 * @param original - the original material
	 * @param variant - the variant to search for
	 */
	@Nullable
	public static IRecipe getRecipeVariant(IShapedRecipe tpl, ItemStack original, ItemStack variant) {
		MyBitSet matches = new MyBitSet(9);
		NonNullList<Ingredient> ingredients = tpl.getIngredients();
		for (int i = 0; i < ingredients.size(); i++) {
			Ingredient ing = ingredients.get(i);
			if (ing.test(original)) matches.add(i);
		}

		int tw = tpl.getRecipeWidth(), th = tpl.getRecipeHeight();
		cyl:
		for (IRecipe recipe : Registries.recipe()) {
			if (recipe == tpl) continue;
			NonNullList<Ingredient> ti = tpl.getIngredients();
			NonNullList<Ingredient> ri = recipe.getIngredients();

			if (tw == getRecipeWidth(recipe) && th == getRecipeHeight(recipe)) {
				for (int x = 0; x < tw; x++) {
					for (int y = 0; y < th; ++y) {
						int i = x + y * tw;
						Ingredient tIng = ti.get(i);
						Ingredient rIng = ri.get(i);

						if (matches.contains(i) != rIng.test(variant) || isOreDict(tIng) != isOreDict(rIng)) {
							continue cyl;
						}
					}
				}
				return recipe;
			}
		}
		return null;
	}

	private static boolean isOreDict(Ingredient ingred) {
		for (ItemStack stack : ingred.getMatchingStacks()) {
			int[] ids = OreDictionary.getOreIDs(stack);
			for (int id : ids) {
				String name = OreDictionary.getOreName(id);
				if (name.contains("ingot")) {
					return true;
				}
				if (name.contains("plank")) {
					return true;
				}
				if (name.contains("leather")) {
					return true;
				}
				if (name.contains("gem")) {
					return true;
				}
				if (name.contains("stone")) {
					return true;
				}
			}
		}
		return false;
	}

	private static int getRecipeWidth(IRecipe r) {
		if (r instanceof IShapedRecipe) {
			return ((IShapedRecipe) r).getRecipeWidth();
		}
		return 0;
	}

	private static int getRecipeHeight(IRecipe r) {
		if (r instanceof IShapedRecipe) {
			return ((IShapedRecipe) r).getRecipeHeight();
		}
		return 0;
	}
}
