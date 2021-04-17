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
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryModifiable;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * RecipeUtil supplies several methods for easing the process of removing existing recipes,
 * as well as a helper method for making a 9x9 grid of the same item (nuggets to ingots, etc)
 * <p>
 * Automatic generate JSON
 * Hot reload Recipes
 */
public class RecipeUtil {
    public static final Character SPACE = ' ';
    private static Map<Character, Ingredient> ingMap = new MyHashMap<>();
    private static Map<String, ResourceLocation> RES_PATH_CACHE = new MyHashMap<>();

    public static void afterInit() {
        ingMap = null;
        RES_PATH_CACHE = null;
    }

    public static IRecipe addShapedRecipe(@Nonnull Block result, @Nonnull Object... components) {
        return addShapedRecipe(new ItemStack(result), components);
    }

    public static IRecipe addShapedRecipe(@Nonnull ItemStack result, @Nonnull Object... components) {
        return addShapedRecipe(null, result, components);
    }

    public static IRecipe addShapedRecipe(@Nullable String name, @Nonnull ItemStack result, @Nonnull Object... components) {
        String $$MODID = ForgeUtil.getCurrentModId();

        List<String> pattern = new ArrayList<>();
        int i = 0;
        while (i < components.length && components[i] instanceof String) {
            pattern.add((String) components[i]);
            i++;
        }

        boolean isOreDict = false;

        ingMap.clear();

        Character curKey = null;

        for (; i < components.length; i++) {
            Object o = components[i];
            if (o instanceof Character) {
                if (curKey != null)
                    throw new IllegalArgumentException("Provided two char keys in a row");
                curKey = (Character) o;
            } else {
                if (curKey == null)
                    throw new IllegalArgumentException("Providing object without a char key");
                if (o instanceof String)
                    isOreDict = true;
                ingMap.put(curKey, serializeItem(o));
                curKey = null;
            }
        }

        final String _id = name != null ? name : (result.getItem().getRegistryName().getPath() + (result.getItem().getHasSubtypes() ? "_" + result.getItemDamage() : ""));
        String id = _id;
        ResourceLocation loc = new ResourceLocation($$MODID, id);

        int copyNum = 0;
        IForgeRegistry<IRecipe> recipeRegistry = Registries.recipe();
        while (recipeRegistry.containsKey(loc)) {
            copyNum++;
            id = _id + "_alt" + copyNum;
            loc = new ResourceLocation($$MODID, id);
        }

        IRecipe recipe = isOreDict ? shapedOreRecipeFactory(id, ingMap, pattern, result, true) : shapedRecipeFactory(id, ingMap, pattern, result);

        recipeRegistry.register(recipe.setRegistryName(loc));
        return recipe;
    }

    public static IRecipe addShapelessRecipe(@Nonnull Block result, @Nonnull Object... components) {
        return addShapelessRecipe(null, new ItemStack(result), components);
    }

    public static IRecipe addShapelessRecipe(@Nonnull ItemStack result, @Nonnull Object... components) {
        return addShapelessRecipe(null, result, components);
    }

    public static IRecipe addShapelessRecipe(@Nullable String key, @Nonnull ItemStack result, @Nonnull Object... components) {
        String $$MODID = ForgeUtil.getCurrentModId();

        Map<String, Object> json = new HashMap<>();

        boolean isOreDict = false;
        List<Ingredient> ingredients = new ArrayList<>();
        //try {
        for (Object o : components) {
            if (o instanceof String)
                isOreDict = true;
            ingredients.add(serializeItem(o));
        }
        /*} catch (IllegalArgumentException e) {
            ImpLib.logger().warn("Recipe would not be registered because its od doesn't exists");
            return null;
        }*/

        final String _id = key != null ? key : (result.getItem().getRegistryName().getPath() + (result.getItem().getHasSubtypes() ? "_" + result.getItemDamage() : ""));
        String id = _id;
        ResourceLocation loc = new ResourceLocation($$MODID, id);

        int copyNum = 0;
        IForgeRegistry<IRecipe> recipeRegistry = Registries.recipe();
        while (recipeRegistry.containsKey(loc)) {
            copyNum++;
            id = _id + "_alt" + copyNum;
            loc = new ResourceLocation($$MODID, id);
        }

        IRecipe recipe = isOreDict ? shapelessOreRecipeFactory(id, ingredients, result) : shapelessRecipeFactory(id, ingredients, result);

        recipeRegistry.register(recipe.setRegistryName(loc));
        return recipe;
    }


    public static IRecipe shapedOreRecipeFactory(@Nullable String group, @Nonnull Map<Character, Ingredient> ingMap, @Nonnull List<String> pattern, @Nonnull ItemStack result, boolean mirrored) {
        if (ingMap.containsKey(SPACE)) {
            throw new JsonSyntaxException("' ' is an reserved name.");
        }

        ingMap.put(' ', Ingredient.EMPTY);

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
            Set<Character> keys = new MyHashSet<>(ingMap.size());
            keys.addAll(ingMap.keySet());

            keys.remove(' ');

            int x = 0;

            for (String line : pattern) {
                char[] chars = line.toCharArray();

                for (char chr : chars) {
                    Ingredient ing = ingMap.get(chr);
                    if (ing == null) {
                        System.out.println(ingMap);
                        throw new JsonSyntaxException("Pattern references symbol '" + chr + "' but it's not defined in the key");
                    }

                    primer.input.set(x++, ing);
                    keys.remove(chr);
                }
            }

            if (!keys.isEmpty()) {
                throw new JsonSyntaxException("Key defines symbols that aren't used in pattern: " + keys);
            } else {
                return new ShapedOreRecipe(RES_PATH_CACHE.computeIfAbsent(group, (r) -> r == null ? null : new ResourceLocation(r)), result, primer);
            }
        }
    }

    public static IRecipe shapedRecipeFactory(@Nullable String group, @Nonnull Map<Character, Ingredient> ingMap, @Nonnull List<String> pattern, @Nonnull ItemStack result) {
        if (ingMap.containsKey(SPACE)) {
            throw new JsonSyntaxException("' ' is an reserved name.");
        }

        ingMap.put(' ', Ingredient.EMPTY);

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

            Set<Character> keys = new MyHashSet<>(ingMap.size());
            keys.addAll(ingMap.keySet());

            keys.remove(' ');

            int x = 0;

            for (String line : pattern) {
                char[] chars = line.toCharArray();

                for (char chr : chars) {
                    Ingredient ing = ingMap.get(chr);
                    if (ing == null) {
                        throw new JsonSyntaxException("Pattern references symbol '" + chr + "' but it's not defined in the key");
                    }

                    input.set(x++, ing);
                    keys.remove(chr);
                }
            }

            if (!keys.isEmpty()) {
                throw new JsonSyntaxException("Key defines symbols that aren't used in pattern: " + keys);
            } else {
                return new ShapedRecipes(group == null ? "" : group, width, height, input, result);
            }
        }
    }

    public static IRecipe shapelessOreRecipeFactory(@Nullable String group, @Nonnull List<Ingredient> ings, @Nonnull ItemStack result) {
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
        return new ShapelessOreRecipe(RES_PATH_CACHE.computeIfAbsent(group, (r) -> r == null ? null : new ResourceLocation(r)), list, result);
    }

    public static IRecipe shapelessRecipeFactory(@Nullable String group, @Nonnull List<Ingredient> ings, @Nonnull ItemStack result) {
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
                //throw new IllegalArgumentException("od name " + thing + " does not have any item!");
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

    /**
     * Remove a crafting recipe
     *
     * @param recipe - resource location of the recipe to remove
     */
    public static void removeRecipe(IRecipe recipe) {
        removeRecipe((IForgeRegistryModifiable<IRecipe>) Registries.recipe(), recipe);
    }

    /**
     * Remove all crafting recipe of a mod
     */
    public static void removeRecipe(String modid) {
        IForgeRegistryModifiable<IRecipe> registry = (IForgeRegistryModifiable<IRecipe>) Registries.recipe();
        for (IRecipe recipe : registry) {
            if (recipe.getRegistryName().getNamespace().equals(modid)) {
                removeRecipe(registry, recipe);
            }
        }
    }

    /**
     * Remove a crafting recipe
     *
     * @param path - resource location of the recipe to remove
     */
    public static void removeRecipe(ResourceLocation path) {
        IForgeRegistryModifiable<IRecipe> registry = (IForgeRegistryModifiable<IRecipe>) Registries.recipe();
        IRecipe recipe = registry.getValue(path);
        if (recipe == null) {
            ImpLib.logger().warn("Removed Recipe: NOT exist: " + path);
            return;
        }
        removeRecipe(registry, recipe);
    }

    /**
     * Remove a crafting recipe
     *
     * @param modRegistry - the recipe registry
     * @param recipe      - the recipe to remove
     */
    public static void removeRecipe(IForgeRegistryModifiable<IRecipe> modRegistry, IRecipe recipe) {
        modRegistry.remove(recipe.getRegistryName());
        modRegistry.register(DummyRecipe.from(recipe));


        ImpLib.logger().info("Removed Recipe: " + recipe.getRegistryName());
        //This was a nice try, but Advancements are loaded when the world loads.
    }

    /**
     * Remove a smelting recipe
     *
     * @param resultItem - the smelting output Item
     * @param stacksize  - the smelting output stack size
     * @param meta       - the smelting output metadata
     */
    public static void removeSmelting(Item resultItem, int stacksize, int meta) {
        ItemStack resultStack = new ItemStack(resultItem, stacksize, meta);
        removeSmelting(resultStack);
    }

    /**
     * Remove a smelting recipe
     *
     * @param resultStack - the output result stack, including metadata and size
     */
    public static void removeSmelting(ItemStack resultStack) {
        ItemStack recipeResult;
        Map<ItemStack, ItemStack> recipes = FurnaceRecipes.instance().getSmeltingList();
        Iterator<ItemStack> iterator = recipes.keySet().iterator();
        while (iterator.hasNext()) {
            ItemStack tmpRecipe = iterator.next();
            recipeResult = recipes.get(tmpRecipe);
            if (ItemStack.areItemStacksEqual(resultStack, recipeResult)) {
                ImpLib.logger().debug("Removed Smelting: " + tmpRecipe + " -> " + recipeResult);
                iterator.remove();
            }
        }
    }

    /**
     * Finds the first recipe in the recipe list that produces a given output.<br>
     * Generally speaking this should return the basic recipe, rather than a repair recipe.
     *
     * @param resultStack - the recipe output
     */
    @Nullable
    public static IRecipe getFirstRecipeWithOutput(ItemStack resultStack) {
        resultStack = resultStack.copy();
        ItemStack recipeResult;
        for (IRecipe tmpRecipe : Registries.recipe()) {
            recipeResult = tmpRecipe.getRecipeOutput();
            resultStack.setCount(Math.max(recipeResult.getCount(), 1));
            if (ItemStack.areItemStacksEqual(resultStack, recipeResult)) {
                return tmpRecipe;
            }
        }
        return null;
    }

    public static List<IRecipe> getAllStorageRecipes() {
        List<IRecipe> results = new ArrayList<>();
        ItemStack recipeResult = null;
        Iterator<IRecipe> iterator = Registries.recipe().iterator();
        outer:
        while (iterator.hasNext()) {
            IRecipe tmpRecipe = iterator.next();
            if (tmpRecipe.canFit(1, 1)) continue;
            if (tmpRecipe instanceof ShapedRecipes) {
                ShapedRecipes shp = (ShapedRecipes) tmpRecipe;
                if (shp.getWidth() != shp.getHeight()) continue;
            }
            Ingredient obj = null;
            int numIngreds = 0;
            for (Ingredient s : tmpRecipe.getIngredients()) {
                if (s != Ingredient.EMPTY) {
                    if (obj == null) obj = s;
                    else if (!obj.equals(s)) {
                        if (obj.getMatchingStacks().length == s.getMatchingStacks().length) {
                            ItemStack[] s1 = obj.getMatchingStacks();
                            ItemStack[] s2 = s.getMatchingStacks();
                            for (int i = 0; i < s1.length; i++) {
                                if (!ItemStack.areItemStacksEqual(s1[i], s2[i]))
                                    continue outer;
                            }
                        } else {
                            continue outer;
                        }
                    }
                    numIngreds++;
                }
            }
            if (numIngreds == 4 || numIngreds == 9)
                results.add(tmpRecipe);
        }
        return results;
    }

    public static List<IRecipe> getAllStorageRecipesChecked() {
        List<IRecipe> results = new ArrayList<>();
        ItemStack recipeResult;
        Iterator<IRecipe> iterator = Registries.recipe().iterator();
        outer:
        while (iterator.hasNext()) {
            IRecipe tmpRecipe = iterator.next();
            recipeResult = tmpRecipe.getRecipeOutput();

            if (recipeResult.getCount() == 4 || recipeResult.getCount() == 9) {
                if (tmpRecipe instanceof ShapedRecipes) {
                    ShapedRecipes shp = (ShapedRecipes) tmpRecipe;
                    if (!shp.canFit(1, 1)) continue;
                }
                Ingredient obj = null;
                for (Ingredient s : tmpRecipe.getIngredients()) {
                    if (s != Ingredient.EMPTY) {
                        if (obj == null) obj = s;
                        else if (!obj.equals(s)) continue outer;
                    }
                }
                if (obj != null) {
                    IRecipe craftRecip = getFirstRecipeWithOutput(obj.getMatchingStacks()[0]);
                    if (craftRecip != null) {
                        results.add(tmpRecipe);
                    }
                }
            }
        }
        return results;
    }

    /**
     * Attempts to locate a similar recipe using a different material.<br>
     * Largely speaking this will only ever match tools and armor (picks, swords, armor, etc.)
     *
     * @param template        - an existing recipe to match against
     * @param desiredMaterial - the variant to search for
     */
    @Nullable
    public static IRecipe getSimilarRecipeWithGivenInput(IRecipe template, ItemStack desiredMaterial) {
        if (template == null) return null;
        if (template.getRecipeOutput().isEmpty())
            return null;
        desiredMaterial.setCount(1);
        ItemStack recipeResult = null;
        for (Ingredient ingred : template.getIngredients()) {
            //if the thing we're trying to match accepts the material we want, its correct
            if (ingred.test(desiredMaterial)) {
                return template;
            }
        }
        ImpLib.logger().warn(template.getRecipeOutput().getDisplayName());
        for (IRecipe itrRecipe : Registries.recipe()) {
            //itrRecipe = RecipeUtil.getRecipeWithOutput(new ItemStack(Items.IRON_SHOVEL));
            if (itrRecipe == template) {
                //we already know the material doesn't match toMatch
                //not skipping would inadvertently return a bad recipe
                continue;
            }
            NonNullList<Ingredient> templateIngreds = template.getIngredients();
            NonNullList<Ingredient> itrRecipeIngreds = itrRecipe.getIngredients();
            boolean doesNotMatch = false;
            //ImpLib.logger().warn(templateIngreds.size() + " ?= " + itrRecipeIngreds.size());
            int twidth = getRecipeWidth(template);
            int theight = getRecipeHeight(template);
            int iwidth = getRecipeWidth(itrRecipe);
            int iheight = getRecipeHeight(itrRecipe);

            if (twidth == iwidth && theight == iheight) {
                ImpLib.logger().warn(itrRecipe.getRecipeOutput().getDisplayName());
                //ImpLib.logger().warn(twidth + "," + theight + ":" + templateIngreds.size() + "|" + itrRecipeIngreds.size());
                for (int x = 0; x < twidth && !doesNotMatch; x++) {
                    for (int y = 0; y < theight && !doesNotMatch; ++y) {
                        //ImpLib.logger().warn((x + y * twidth));
                        Ingredient templateIng = templateIngreds.get(x + y * twidth);
                        Ingredient iteratorIng = itrRecipeIngreds.get(x + y * twidth);

                        ImpLib.logger().warn(iteratorIng.test(desiredMaterial) + " || " + Compare(templateIng, iteratorIng));
                        if (!(iteratorIng.test(desiredMaterial) || Compare(templateIng, iteratorIng))) {
                            doesNotMatch = true;
                        } else {
                            ImpLib.logger().warn("ASDF");
                            if (isIngredientIngot(templateIng) != isIngredientIngot(iteratorIng)) {
                                doesNotMatch = true;
                            }
                        }
                    }
                }
				/*for(int i = 0; i < templateIngreds.size(); i++) {
					if(!(itrRecipeIngreds.get(i).test(desiredMaterial) || Compare(templateIngreds.get(i),itrRecipeIngreds.get(i)))) {
						doesNotMatch = true;
						break;
					}
				}*/
                if (doesNotMatch) {
                    continue;
                }
                return itrRecipe;
            }
        }
        return null;
    }

    private static boolean isIngredientIngot(Ingredient ingred) {
        for (ItemStack stack : ingred.getMatchingStacks()) {
            int[] ids = OreDictionary.getOreIDs(stack);
            for (int id : ids) {
                if (OreDictionary.getOreName(id).contains("ingot")) {
                    return true;
                }
                if (OreDictionary.getOreName(id).contains("plank")) {
                    return true;
                }
                if (OreDictionary.getOreName(id).contains("leather")) {
                    return true;
                }
                if (OreDictionary.getOreName(id).contains("gem")) {
                    return true;
                }
                if (OreDictionary.getOreName(id).contains("stone")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int getRecipeWidth(IRecipe template) {
        if (template instanceof ShapedRecipes) {
            return ((ShapedRecipes) template).getWidth();
        }
        if (template instanceof ShapedOreRecipe) {
            return ((ShapedOreRecipe) template).getWidth();
        }
        return 0;
    }

    private static int getRecipeHeight(IRecipe template) {
        if (template instanceof ShapedRecipes) {
            return ((ShapedRecipes) template).getHeight();
        }
        if (template instanceof ShapedOreRecipe) {
            return ((ShapedOreRecipe) template).getHeight();
        }
        return 0;
    }

    private static boolean Compare(Ingredient a, Ingredient b) {
        ItemStack[] ss1 = a.getMatchingStacks();
        ItemStack[] ss2 = b.getMatchingStacks();
        if (ss1.length == 0 && ss2.length == 0) return true;
        for (ItemStack s1 : ss1) {
            for (ItemStack s2 : ss2) {
                if (ItemStack.areItemStacksEqual(s1, s2)) return true;
            }
        }
        return false;
    }

    public static class DummyRecipe implements IRecipe {
        public static final ItemStack missingNo;

        static {
            NBTTagCompound tag = new NBTTagCompound();
            NBTTagCompound tagDisplay = new NBTTagCompound();
            tagDisplay.setString("Name", "Error! This recipe couldn't be found on client!");
            tag.setTag("display", tagDisplay);
            ItemStack stack = new ItemStack(Blocks.BEDROCK, 1);
            stack.setTagCompound(tag);
            missingNo = stack;
        }

        private final ItemStack output;
        private ResourceLocation name;

        public DummyRecipe() {
            this.output = missingNo;
        }

        public DummyRecipe(ItemStack output) {
            this.output = output;
        }

        public Class<IRecipe> getRegistryType() {
            return IRecipe.class;
        }

        public static IRecipe from(IRecipe other) {
            return new DummyRecipe(other.getRecipeOutput()).setRegistryName(other.getRegistryName());
        }

        public IRecipe setRegistryName(ResourceLocation name) {
            this.name = name;
            return this;
        }

        public ResourceLocation getRegistryName() {
            return this.name;
        }

        @Override
        public boolean matches(@Nonnull InventoryCrafting inv, @Nonnull World worldIn) {
            return false;
        }

        @Nonnull
        @Override
        public ItemStack getCraftingResult(@Nonnull InventoryCrafting inv) {
            return output;
        }

        @Override
        public boolean canFit(int width, int height) {
            return false;
        }

        public boolean isDynamic() {
            return true;
        }

        @Nonnull
        @Override
        public ItemStack getRecipeOutput() {
            return output;
        }
    }

/*
	private static void writeRecipeAdvancement(String result) {
		if(ADVANCE_DIR == null) {
			throw new RuntimeException("No advancements directory!");
		}
		Map<String, Object> json = new HashMap<>();
		json.put("parent", "minecraft:recipes/root");
		Map<String, Object> rewards = new HashMap<>();
		List<String> recipes = new ArrayList<String>();
		recipes.add(DOMAIN+":"+result);
		rewards.put("recipes",recipes);
		
		Map<String, Map<String, Object>> criteria = new HashMap<>();
		Map<String, Object> has_item = new HashMap<>();
		Map<String, Object> conditions = new HashMap<>();
		Map<String, Object> conditions2 = new HashMap<>();
		Map<String, Object> has_the_recipe = new HashMap<>();
		ArrayList<ArrayList<String>> requirements = new ArrayList<ArrayList<String>>();
		
		has_item.put("trigger", "minecraft:inventory_changed");
		ArrayList<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
		Map<String, Object> entry = new HashMap<>();
		entry.put("item", "");
		items.add(entry);
		conditions.put("items", items);
		conditions2.put("recipe", DOMAIN+":"+result);
		has_the_recipe.put("trigger", "minecraft:recipe_unlocked");
		has_the_recipe.put("conditions", conditions2);
		
		has_item.put("conditions", conditions);
		criteria.put("has_item", has_item);
		criteria.put("has_the_recipe", has_the_recipe);
		
		ArrayList<String> reqs = new ArrayList<String>();
		reqs.add("has_item");
		reqs.add("has_the_recipe");
		requirements.add(reqs);

		json.put("requirements", requirements);
		json.put("criteria", criteria);
		json.put("rewards", rewards);
		
		String suffix = "";
		File f = new File(ADVANCE_DIR, result + suffix + ".json");

		if(f.exists()) return;

		int copyNum = 0;
		while (f.exists()) {
			if(copyNum == 0) {
				suffix += "_alt";
				copyNum++;
			}
			else {
				copyNum++;
			}
			f = new File(ADVANCE_DIR, result + suffix + copyNum + ".json");
		}

		try (FileWriter w = new FileWriter(f)) {
			GSON.toJson(json, w);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	*/

}
