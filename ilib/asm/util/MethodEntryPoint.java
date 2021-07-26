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
import ilib.util.PinyinUtil;
import ilib.util.TextHelperM;
import net.minecraft.block.Block;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import roj.collect.*;
import roj.concurrent.OperationDone;
import roj.reflect.DirectFieldAccess;
import roj.reflect.DirectFieldAccessor;
import roj.reflect.DirectMethodAccess;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/23 0:55
 */
public class MethodEntryPoint {

    public static IBitSet COLOR_CODE;
    public static IBitSet HEX_CHAR;
    private static char[] AWFUL_ASCII;
    public static Int2IntMap AWFUL_ASCII_ID;

    private static IntMap<IntList> widthTable;

    @SideOnly(Side.CLIENT)
    public static IntList getOrCreateWidthTable(FontRenderer fr, int w) {
        if(widthTable == null) {
            widthTable = new IntMap<>(4);
            for (char c : AWFUL_ASCII) {
                int width = fr.getCharWidth(c);
                IntList list = widthTable.get(width);
                if(list == null)
                    widthTable.put(width, list = new IntList());
                list.add(width);
            }
        }
        return widthTable.get(w);
    }

    public static void clientInit() {
        AWFUL_ASCII_ID = filled(AWFUL_ASCII = new char[] {'À','Á','Â','È','Ê','Ë','Í','Ó','Ô','Õ','Ú','ß','ã','õ','ğ','İ','ı',
                'Œ','œ','Ş','ş','Ŵ','ŵ','ž','ȇ','�','�','�','�','�','�','�','�','�','�','�','�','�','�',' ','!','"',
                '#','$','%','&','\'','(',')','*','+',',','-','.','/','0','1','2','3','4','5','6','7','8','9',':',';','<','=',
                '>','?','@','A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X',
                'Y','Z','[','\\',']','^','_','`','a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s',
                't','u','v','w','x','y','z','{','|','}','~','�','�','Ç','ü','é','â','ä','à','å','ç','ê','ë','è','ï','î','ì',
                'Ä','Å','É','æ','Æ','ô','ö','ò','û','ù','ÿ','Ö','Ü','ø','£','Ø','×','ƒ','á','í','ó','ú','ñ','Ñ','ª','º','¿',
                '®','¬','½','¼','¡','«','»','░','▒','▓','│','┤','╡','╢','╖','╕','╣','║','╗','╝','╜','╛','┐','└','┴','┬','├',
                '─','┼','╞','╟','╚','╔','╩','╦','╠','═','╬','╧','╨','╤','╥','╙','╘','╒','╓','╫','╪','┘','┌','█','▄','▌','▐',
                '▀','α','β','Γ','π','Σ','σ','μ','τ','Φ','Θ','Ω','δ','∞','∅','∈','∩','≡','±','≥','≤','⌠','⌡','÷','≈',
                '°','∙','·','√','ⁿ','²','■','�','�'}); // ? why it not crash...
        COLOR_CODE = LongBitSet.from('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'k', 'l', 'm', 'n', 'o', 'r');
        HEX_CHAR = LongBitSet.from('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f');
    }

    private static Int2IntMap filled(char... chars) {
        Int2IntMap map = new Int2IntMap(chars.length);
        for (int i = 0, l = chars.length; i < l; i++) {
            map.put(i, chars[i]);
        }
        return map;
    }

    protected static List<String> aList;
    public static List<String> doYouReallyNeedSoMuchList() {
        if (aList == null)
            aList = new ArrayList<>(4);
        aList.clear();
        return aList;
    }

    public static int getStackDepth() {
        depthAccessor.setInstance(new Throwable());
        return depthAccessor.getInt();
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

    private static DirectFieldAccessor cachedRecipeAcc;

    static final DirectFieldAccessor depthAccessor = DirectMethodAccess.get(DirectFieldAccessor.class, "getInt", Throwable.class, "getStackTraceDepth");

    static final DirectFieldAccessor addAllAccessor = DirectMethodAccess.get(DirectFieldAccessor.class, "invoke", EnumSet.class, "addAll");

    private static final EnumSet<EnumFacing> facings = EnumSet.allOf(EnumFacing.class);

    public static <T> FilterList<T> getThrowExceptionFilter() {
        return Helpers.cast(throwAny);
    }

    public static FilterList<Entity> getEntityAliveFilter(Entity entity) {
        list.found = entity;
        return list;
    }

    public static EnumSet<EnumFacing> getAllFaceSet() {
        addAllAccessor.setInstance(facings);
        addAllAccessor.invoke();
        //addAllAccessor.clearInstance();
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
                NBTTagCompound nbttagcompound = tagList.getCompoundTagAt(i);
                int k = nbttagcompound.getShort("id");
                int l = nbttagcompound.getShort("lvl");
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

    public static DirectFieldAccessor getCachedRecipeAcc() {
        if (cachedRecipeAcc == null)
            cachedRecipeAcc = DirectFieldAccess.get(InventoryCrafting.class, "currentRecipe");
        return cachedRecipeAcc;
    }
}
