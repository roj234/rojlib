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
import ilib.client.api.ClientChangeWorldEvent;
import ilib.collect.StackComparator;
import ilib.util.MCTexts;
import ilib.util.PlayerUtil;
import ilib.util.Reflection;
import ilib.util.Registries;
import roj.asm.tree.Clazz;
import roj.asm.tree.Field;
import roj.asm.tree.Method;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.*;
import roj.asm.type.Type;
import roj.asm.util.InsnList;
import roj.collect.*;
import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.reflect.DirectAccessor;
import roj.reflect.TraceUtil;
import roj.text.UTFCoder;
import roj.util.ByteList;
import roj.util.Helpers;

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
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.PrimitiveIterator.OfInt;

import static roj.asm.Opcodes.*;
import static roj.asm.util.AccessFlag.PUBLIC;

/**
 * @author Roj234
 * @since 2020/8/23 0:55
 */
public class MCHooks {
    // region 自定义TPS

    public static long MSpT = 50L;

    // endregion
    // region FastTileConst/FastEntityConst: 批量生成Entity/TileEntity

    public static Object batchGenerate(RandomAccessFile raf, boolean entity, ToIntMap<String> byId) throws
        IOException {
        if (raf.length() == 0) {
            raf.writeInt(0);
        } else {
            int len = raf.readInt();
            if (len == 0) return null;

            ByteList tmp = IOUtil.getSharedByteBuf();
            UTFCoder uc = IOUtil.SharedCoder.get();

            Clazz cz = new Clazz();
            DirectAccessor.makeHeader(entity ? "roj/asm/BatchEntityCreator" : "roj/asm/BatchTileCreator",
                                      entity ? "ilib/asm/util/MCHooks$ICreator" : "ilib/asm/util/TileEntityCreator", cz);
            cz.version = 50 << 16;
            DirectAccessor.addInit(cz);
            DirectAccessor.cloneable(cz);
            if (entity) cz.interfaces.add("java/util/function/Function");

            Field f0 = new Field(0, "id", Type.std(Type.INT));
            cz.fields.add(f0);

            Method m0 = new Method(PUBLIC, cz, "setId", "(I)V");
            cz.methods.add(m0);
            AttrCode c0 = m0.code = new AttrCode(m0);
            c0.stackSize = c0.localSize = 2;
            c0.instructions.add(NPInsnNode.of(ALOAD_0));
            c0.instructions.add(NPInsnNode.of(ILOAD_1));
            c0.instructions.add(new FieldInsnNode(PUTFIELD, cz.name, "id", Type.std(Type.INT)));
            c0.instructions.add(NPInsnNode.of(RETURN));

            m0 = new Method(PUBLIC, cz, entity ? "apply" : "get",
                            entity ? "(Ljava/lang/Object;)Ljava/lang/Object;" : "()Lnet/minecraft/tileentity/TileEntity;");
            cz.methods.add(m0);
            c0 = m0.code = new AttrCode(m0);
            c0.stackSize = (char) (entity ? 3 : 2);
            c0.localSize = (char) (entity ? 2 : 1);

            InsnList insn = c0.instructions;

            if (entity) {
                insn.add(NPInsnNode.of(ALOAD_1));
                insn.add(new ClassInsnNode(CHECKCAST, "net/minecraft/world/World"));
                insn.add(NPInsnNode.of(ASTORE_1));
            }

            insn.add(NPInsnNode.of(ALOAD_0));
            insn.add(new FieldInsnNode(GETFIELD, cz.name, "id", Type.std(Type.INT)));

            SwitchInsnNode _switch = new SwitchInsnNode(TABLESWITCH);
            InsnNode label = NPInsnNode.of(ACONST_NULL);
            _switch.def = label;

            insn.add(_switch);
            insn.add(label);
            insn.add(NPInsnNode.of(ARETURN));

            int i = 0;

            while (len-- > 0) {
                int len1 = raf.readChar();
                tmp.ensureCapacity(len1);
                raf.readFully(tmp.list, 0, len1);
                tmp.wIndex(len1);
                byId.putInt(uc.decode(), i);

                len1 = raf.readChar();
                tmp.ensureCapacity(len1);
                raf.readFully(tmp.list, 0, len1);
                tmp.wIndex(len1);

                String clz = uc.decode();

                label = new ClassInsnNode(NEW, clz);
                _switch.switcher.add(new SwitchEntry(i++, label));
                insn.add(label);
                insn.add(NPInsnNode.of(DUP));
                if (entity) {
                    insn.add(NPInsnNode.of(ALOAD_1));
                    insn.add(new InvokeInsnNode(INVOKESPECIAL, clz, "<init>", "(Lnet/minecraft/world/World;)V"));
                } else {
                    insn.add(new InvokeInsnNode(INVOKESPECIAL, clz, "<init>", "()V"));
                }
                insn.add(NPInsnNode.of(ARETURN));
            }

            return DirectAccessor.i_build(cz);
        }
        return null;
    }

    public static void batchAdd(RandomAccessFile raf, String id, Class<?> clazz) throws IOException {
        if (raf != null) {
            long pos = raf.getFilePointer();

            raf.seek(0);
            int count = raf.readInt();

            raf.seek(0);
            raf.writeInt(count + 1);

            raf.seek(pos);

            UTFCoder uc = IOUtil.SharedCoder.get();
            uc.keep = false;

            ByteList data = uc.encodeR(id);
            raf.writeShort(data.wIndex());
            raf.write(data.list, 0, data.wIndex());

            data = uc.encodeR(clazz.getName().replace('.', '/'));
            raf.writeShort(data.wIndex());
            raf.write(data.list, 0, data.wIndex());
        }
    }

    public interface ICreator {
        default void setId(int id) {
            throw new UnsupportedOperationException();
        }
        default Object clone() {
            throw new UnsupportedOperationException();
        }
    }

    // endregion
    // region FastRecipe: 高效的合成map

    public interface RecipeCache {
        IRecipe getRecipe();
        void setRecipe(IRecipe recipe);
    }

    public static final UnsortedMultiKeyMap<ItemStack, String, List<IRecipe>> mcRecipes =
        UnsortedMultiKeyMap.create(StackComparator.COMPARE_ITEM, 9);
    public static final List<IRecipe> fallbackRecipes = new ArrayList<>();

    public static void initRecipes() {
        mcRecipes.clear();
        fallbackRecipes.clear();
        for (IRecipe r : Registries.recipe()) {
            if (r instanceof ShapedRecipes || r instanceof ShapelessRecipes ||
                r instanceof ShapedOreRecipe || r instanceof ShapelessOreRecipe) {
                List<Object> v = computeIngr(r, r.getIngredients());
                if(v != null) {
                    mcRecipes.ciaIntl(Helpers.cast(v), Helpers.fnArrayList()).add(r);
                    continue;
                }
            }
            fallbackRecipes.add(r);
        }
        System.out.println("统计: 优化/未优化的合成表 " + mcRecipes.size() + "/" + fallbackRecipes.size());
    }

    private static List<Object> computeIngr(IRecipe id, List<Ingredient> ings) {
        List<Object> stacks = new SimpleList<>(ings.size());
        IntSet commonOreDict = new IntSet(), tmp = new IntSet();
        for (int i = 0; i < ings.size(); i++) {
            Ingredient ing = ings.get(i);
            if(!ing.isSimple()) return null;
            if(ing instanceof IOreIngredient) {
                stacks.add(((IOreIngredient) ing).getOredict());
            } else {
                ItemStack[] matches = ing.getMatchingStacks();
                if (matches.length < 2) {
                    if (matches.length >= 1) {
                        ItemStack stack = matches[0];
                        ResourceLocation key = stack.getItem().getRegistryName();
                        if(stack.getItemDamage() == 32767) {
                            // noinspection all
                            stacks.add(key.toString());
                        } else if(!stack.isEmpty()) {
                            // noinspection all
                            stacks.add(key.toString() + ':' + stack.getItemDamage());
                        }
                    }
                } else {
                    for (ItemStack stack : matches) {
                        if(commonOreDict.isEmpty()) {
                            commonOreDict.addAll(OreDictionary.getOreIDs(stack));
                        } else {
                            tmp.clear();
                            tmp.addAll(OreDictionary.getOreIDs(stack));
                            commonOreDict.intersection(tmp);
                        }
                        if(commonOreDict.isEmpty()) {
                            //System.out.println("[FW]无法优化: Ingredient没有共同的矿物词典(" + Arrays.toString(matches) + ") : " + id.getRegistryName());
                            return null;
                        }
                    }
                    if(commonOreDict.size() > 1) {
                        String[] data = new String[commonOreDict.size()];
                        int j = 0;
                        for (OfInt itr = commonOreDict.iterator(); itr.hasNext(); ) {
                            data[j++] = OreDictionary.getOreName(itr.nextInt());
                        }
                        System.out.println("[FW]无法优化: Ingredient有超过一个共同的矿物词典(" + Arrays.toString(data) + ") : " + id.getRegistryName());
                        return null;
                    } else {
                        stacks.add(OreDictionary.getOreName(commonOreDict.iterator().nextInt()));
                    }
                }
                commonOreDict.clear();
            }
        }

        return stacks;
    }

    // endregion
    // region 修复一个渲染卡顿

    public static boolean debugRenderAllSide;
    public static final BlockRenderLayer[] BlockRenderLayerValues = BlockRenderLayer.values();

    // endregion
    // region NoSoManyBlockPos

    public static EnumFacing[] REDSTONE_UPDATE_ORDER = new EnumFacing[] {
        EnumFacing.WEST, EnumFacing.EAST, EnumFacing.DOWN,
        EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH
    };

    public static EnumFacing[] identityFacings() {
        return EnumFacing.VALUES;
    }

    // 临时禁止方块更新
    public static boolean blockUpdate;

    // endregion

    protected static List<String> aList;
    public static List<String> doYouReallyNeedSoMuchList() {
        if (aList == null)
            aList = new ArrayList<>(4);
        aList.clear();
        return aList;
    }

    public static int getStackDepth() {
        int i = TraceUtil.INSTANCE.classDepth("java.lang.Thread");
        if (i > 0) {
            return i;
        }
        return TraceUtil.stackDepth(new Throwable());
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
                    list.add(MCTexts.format("item.color", String.format("#%06X", tag1.getInteger("color"))));
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
                list.add(MCTexts.format("item.unbreakable"));
            }

            if (tag.hasKey("CanDestroy", 9)) {
                tagList = tag.getTagList("CanDestroy", 8);
                if (!tagList.isEmpty()) {
                    list.add(MCTexts.format("item.canBreak"));

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
                    list.add(MCTexts.format("item.canPlace"));

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
            list.add(MCTexts.format("item.durability", stack.getMaxDamage() - stack.getItemDamage(), stack.getMaxDamage()));
        }

        list.add(String.valueOf(Item.REGISTRY.getNameForObject(stack.getItem())));

        return list;
    }

    @Nonnull
    public static List<String> getItemInformation(ItemStack stack) {
        List<String> list = makeBetterInformation(stack);
        for (int i = list.size() - 1; i >= 0; i--) {
            String s = list.get(i);
            if (s == null) list.set(i, s = "null");
            else s = s.trim();

            if (s.isEmpty()) list.remove(i);
            else list.set(i, (Config.enablePinyinSearch ? MCTexts.pinyin().toPinyin(s) : s).toLowerCase());
        }
        return list;
    }

    // region NoEnchantTax/NoAnvilTax: 禁用经验等级

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

    // 0-30级所需经验
    public static final int ench30s = 37 + (27 - 15) * 5 + 37 + (28 - 15) * 5 + 37 + (29 - 15) * 5;

    public static int xpCap(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        } else {
            return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
        }
    }

    // endregion
    // region Fast World Join

    public static void redirectGC() {
        if (Config.changeWorldSpeed < 3) System.gc();
        MinecraftForge.EVENT_BUS.post(new ClientChangeWorldEvent());
    }

    // endregion
    // region WeakHashSet
    public static Set<Entity> createEntityList() {
        return new WeakHashSet<Entity>() {
            long time;

            @Override
            public boolean remove(Object key) {
                if (System.currentTimeMillis() - time > 600000) {
                    if (gc_ed > 0) {
                        PlayerUtil.sendTo(null, "[ImpLib] 在过去十分钟内, WHS 清除了 " + gc_ed + " 个对象");
                        gc_ed = 0;
                    }
                    time = System.currentTimeMillis();
                }
                return super.remove(key);
            }
        };
    }
    // endregion
}
