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
package ilib.asm.util;

import ilib.ClientProxy;
import ilib.Config;
import ilib.util.CraftingMap.StackComparator;
import ilib.util.PinyinUtil;
import ilib.util.Reflection;
import ilib.util.Registries;
import ilib.util.TextHelperM;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.OreIngredient;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import roj.collect.FilterList;
import roj.collect.IntSet;
import roj.collect.MyHashMap;
import roj.collect.UnsortedMultiKeyMap;
import roj.concurrent.OperationDone;
import roj.math.MathUtils;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.PrimitiveIterator.OfInt;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/23 0:55
 */
public class MCHooks {
    public interface RecipeCache {
        IRecipe getRecipe();
        void setRecipe(IRecipe recipe);
    }

    public static final class ItemStackMap<V> extends MyHashMap<ItemStack, V> {
        @Override
        protected int indexFor(ItemStack id) {
            int v;
            return id == null ? 0 : ((v = (Item.getIdFromItem(id.getItem()) * 31 + id.getItemDamage())) ^ (v >>> 16)) & (length - 1);
        }

        @Override
        public Entry<ItemStack, V> getEntry(ItemStack id) {
            Entry<ItemStack, V> entry = getEntryFirst(id, false);
            int dmg = id.getItemDamage();
            while (entry != null) {
                ItemStack s = entry.k;
                if (id.getItem() == s.getItem() && id.getItemDamage() == s.getItemDamage()) {
                    return entry;
                }
                entry = entry.next;
            }
            if(dmg != 32767) {
                id.setItemDamage(32767);
                entry = getEntry(id);
                id.setItemDamage(dmg);
            }
            return entry;
        }
    }

    protected static List<String> aList;
    public static List<String> doYouReallyNeedSoMuchList() {
        if (aList == null)
            aList = new ArrayList<>(4);
        aList.clear();
        return aList;
    }

    public static int getStackDepth() {
        return Reflection.HELPER.getStackTraceDepth(new Throwable());
    }

    private static final FilterList<Entity> list = new FilterList<>(((old, latest) -> {
        if (latest != null) {
            if (!latest.isDead && latest.preventEntitySpawning && (old == null || !latest.isRidingSameEntity(old))) {
                throw OperationDone.INSTANCE;
            }
        }
        return false;
    }));

    private static final FilterList<Object> throwAny = new FilterList<>((old, latest) -> {
        throw OperationDone.INSTANCE;
    });

    private static final EnumSet<EnumFacing> facings = EnumSet.allOf(EnumFacing.class);

    public static final UnsortedMultiKeyMap<ItemStack, String, List<IRecipe>> mcRecipes;
    public static final List<IRecipe> fallbackRecipes = new ArrayList<>();

    static {
        mcRecipes = UnsortedMultiKeyMap.create(StackComparator.COMPARE_ITEM, 9);
    }

    @SuppressWarnings("unchecked")
    public static void initRecipes() {
        mcRecipes.clear();
        fallbackRecipes.clear();
        for (IRecipe r : Registries.recipe()) {
            if (r instanceof ShapedRecipes || r instanceof ShapelessRecipes ||
                    r instanceof ShapedOreRecipe || r instanceof ShapelessOreRecipe) {
                List<Object> v = computeIngr(r, r.getIngredients());
                if(v != null) {
                    if(v.get(0) instanceof String[]) {
                        // 多个OD我也不知道啊...
                        MathUtils.dikaerProduct((List<String[]>) Helpers.cast(v), (ls) -> {
                            mcRecipes.ciaIntl(ls, Helpers.fnArrayList()).add(r);
                        });
                    } else {
                        mcRecipes.ciaIntl(Helpers.cast(v), Helpers.fnArrayList()).add(r);
                    }
                    continue;
                }
            }
            fallbackRecipes.add(r);
        }
        System.out.println("统计: 优化的合成表/未优化 " + mcRecipes.size() + "/" + fallbackRecipes.size());
    }

    private static List<Object> computeIngr(IRecipe id, List<Ingredient> ings) {
        List<Object> stacks = new ArrayList<>(ings.size());
        boolean shouldBeStringArray = false;
        IntSet commonOreDict = new IntSet(), tmp = new IntSet();
        for (int i = 0; i < ings.size(); i++) {
            Ingredient ing = ings.get(i);
            if(!ing.isSimple()) return null;
            if(ing instanceof OreIngredient) {
                for (ItemStack stack : ing.getMatchingStacks()) {
                    if(commonOreDict.isEmpty()) {
                        commonOreDict.addAll(OreDictionary.getOreIDs(stack));
                    } else {
                        tmp.clear();
                        tmp.addAll(OreDictionary.getOreIDs(stack));
                        commonOreDict.intersection(tmp);
                    }
                    if(commonOreDict.isEmpty())
                        throw new IllegalStateException("[FastWorkbench] No common ore dictionary in OreDict type " + Arrays.toString(ing.getMatchingStacks()) + "!");
                }
                if(commonOreDict.size() > 1) {
                    shouldBeStringArray = true;
                    String[] data = new String[commonOreDict.size()];
                    int j = 0;
                    for (OfInt itr = commonOreDict.iterator(); itr.hasNext(); ) {
                        data[j++] = OreDictionary.getOreName(itr.nextInt());
                    }
                    stacks.add(data);
                } else {
                    stacks.add(OreDictionary.getOreName(commonOreDict.iterator().nextInt()));
                }
                commonOreDict.clear();
            } else {
                for(ItemStack stack : ing.getMatchingStacks()) {
                    if(stack.getItemDamage() == 32767) {
                        stacks.add(stack.getItem().getRegistryName().toString());
                    } else if(!stack.isEmpty()) {
                        stacks.add(stack.getItem().getRegistryName().toString() + stack.getItemDamage());
                    } else {
                        stacks.add(null);
                    }
                }
            }
        }
        if(shouldBeStringArray) {
            System.err.println("[FastWorkbench] More than one common ore dictionaries found in recipe #" + id.getRegistryName() + " ( A " + id.getClass().getName() + " )");
            for (int i = 0; i < stacks.size(); i++) {
                Object o = stacks.get(i);
                if(!(o instanceof String[])) {
                    stacks.set(i, new String[] { (String) o });
                }
            }
        }
        return stacks;
    }

    public static <T> FilterList<T> getThrowExceptionFilter() {
        return Helpers.cast(throwAny);
    }

    public static FilterList<Entity> getEntityAliveFilter(Entity entity) {
        list.found = entity;
        return list;
    }

    public static EnumSet<EnumFacing> getAllFaceSet() {
        Reflection.HELPER.addAll(facings);
        return facings;
    }

    public static List<String> makeBetterInformation(ItemStack stack) {
        List<String> list = doYouReallyNeedSoMuchList();

        list.add(stack.getDisplayName());

        stack.getItem().addInformation(stack, ClientProxy.mc.world, list, ITooltipFlag.TooltipFlags.NORMAL);
        for (int i = 0; i < list.size(); i++) {
            list.set(i, TextFormatting.getTextWithoutFormattingCodes(list.get(i)));
        }

        int i;
        if (stack.hasTagCompound()) {

            final NBTTagCompound tag = stack.getTagCompound();

            NBTTagList tagList = stack.getEnchantmentTagList();
            for (i = 0; i < tagList.tagCount(); ++i) {
                NBTTagCompound tag1 = tagList.getCompoundTagAt(i);
                int k = tag1.getShort("id");
                int l = tag1.getShort("lvl");
                Enchantment enchantment = Enchantment.getEnchantmentByID(k);
                if (enchantment != null) {
                    list.add(TextFormatting.getTextWithoutFormattingCodes(enchantment.getTranslatedName(l)));
                }
            }

            if (tag.hasKey("display", 10)) {
                NBTTagCompound tag1 = tag.getCompoundTag("display");
                if (tag1.hasKey("color", 3)) {
                    list.add(TextHelperM.translate("item.color", String.format("#%06X", tag1.getInteger("color"))));
                }

                if (tag1.hasKey("Lore", 9)) {
                    tagList = tag1.getTagList("Lore", 8);
                    if (!tagList.isEmpty()) {
                        for (i = 0; i < tagList.tagCount(); ++i) {
                            list.add(TextFormatting.getTextWithoutFormattingCodes(tagList.getStringTagAt(i)));
                        }
                    }
                }
            }

            if (tag.getBoolean("Unbreakable")) {
                list.add(TextHelperM.translate("item.unbreakable"));
            }

            if (tag.hasKey("CanDestroy", 9)) {
                tagList = tag.getTagList("CanDestroy", 8);
                if (!tagList.isEmpty()) {
                    list.add(TextHelperM.translate("item.canBreak"));

                    for (i = 0; i < tagList.tagCount(); ++i) {
                        Block block = Block.getBlockFromName(tagList.getStringTagAt(i));
                        if (block != null) {
                            list.add(TextFormatting.getTextWithoutFormattingCodes(block.getLocalizedName()));
                        } else {
                            list.add("missingno");
                        }
                    }
                }
            }

            if (tag.hasKey("CanPlaceOn", 9)) {
                tagList = tag.getTagList("CanPlaceOn", 8);
                if (!tagList.isEmpty()) {
                    list.add(TextHelperM.translate("item.canPlace"));

                    for (i = 0; i < tagList.tagCount(); ++i) {
                        Block block = Block.getBlockFromName(tagList.getStringTagAt(i));
                        if (block != null) {
                            list.add(TextFormatting.getTextWithoutFormattingCodes(block.getLocalizedName()));
                        } else {
                            list.add("missingno");
                        }
                    }
                }
            }
        }

        if (stack.isItemDamaged()) {
            list.add(TextHelperM.translate("item.durability", stack.getMaxDamage() - stack.getItemDamage(), stack.getMaxDamage()));
        }

        list.add(String.valueOf(Item.REGISTRY.getNameForObject(stack.getItem())));

        return list;
    }

    @Nonnull
    public static List<String> getItemInformation(ItemStack stack) {
        List<String> list = makeBetterInformation(stack);
        for (ListIterator<String> itr = list.listIterator(); itr.hasNext(); ) {
            String s = itr.next().trim();
            if (s.isEmpty()) {
                itr.remove();
            } else {
                itr.set((Config.enablePinyinSearch ? PinyinUtil.pinyin().toPinyin(s) : s).toLowerCase());
            }
        }
        return list;
    }

    public static void writeAABB(Entity entity, NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        AxisAlignedBB aabb = entity.getEntityBoundingBox();
        list.appendTag(new NBTTagDouble(aabb.minX/* - entity.posX*/));
        list.appendTag(new NBTTagDouble(aabb.minY /* - entity.posY*/));
        list.appendTag(new NBTTagDouble(aabb.minZ/* - entity.posZ*/));
        list.appendTag(new NBTTagDouble(aabb.maxX/* - entity.posX*/));
        list.appendTag(new NBTTagDouble(aabb.maxY/* - entity.posY*/));
        list.appendTag(new NBTTagDouble(aabb.maxZ/* - entity.posZ*/));
        compound.setTag("AABB", list);
    }

    public static void readAABB(Entity entity, NBTTagCompound compound) {
        if (!compound.hasKey("AABB"))
            return;
        NBTTagList aabb = compound.getTagList("AABB", 6);
        entity.setEntityBoundingBox(new AxisAlignedBB(/*entity.posX + */aabb.getDoubleAt(0),
                /*entity.posY + */aabb.getDoubleAt(1),
                /*entity.posZ + */aabb.getDoubleAt(2),
                /*entity.posX + */aabb.getDoubleAt(3),
                /*entity.posY + */aabb.getDoubleAt(4),
                /*entity.posZ + */aabb.getDoubleAt(5)));
    }

    public static void playerAnvilClick(EntityPlayer player, int level) {
        player.addExperience(-get02LScores(level));
    }

    public static int get02LScores(int cost) {
        int i = 0;
        for (int j = 0; j < cost; j++) {
            i += xpCap(j);
        }
        return i;
    }
    public static final int ench30s = 37 + (27 - 15) * 5 + 37 + (28 - 15) * 5 + 37 + (29 - 15) * 5;

    public static int xpCap(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        } else {
            return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
        }
    }
}
