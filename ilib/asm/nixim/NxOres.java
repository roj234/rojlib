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
package ilib.asm.nixim;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.oredict.OreDictionary;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.IntList;
import roj.collect.IntMap;
import roj.collect.IntSet;
import roj.collect.ToIntMap;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author Roj234
 * @since  2020/11/14 15:09
 */
@Nixim("net.minecraftforge.oredict.OreDictionary")
class NxOres extends OreDictionary {
    @Shadow("idToName")
    private static List<String> idToName;
    @Copy
    private static ToIntMap<String> nameToId_1;
    @Shadow("idToStack")
    private static List<NonNullList<ItemStack>> idToStack;
    @Shadow("idToStackUn")
    private static List<NonNullList<ItemStack>> idToStackUn;
    @Copy
    private static IntMap<IntList> stackToId_1;
    @Shadow("EMPTY_LIST")
    public static NonNullList<ItemStack> EMPTY_LIST;

    @Inject("registerOreImpl")
    private static void registerOreImpl(String name, @Nonnull ItemStack ore) {
        if (!"Unknown".equals(name)) {
            if (ore.isEmpty()) {
                FMLLog.bigWarning("????????????????????????????????????: {}", name);
            } else {
                ResourceLocation key = ore.getItem().delegate.name();

                int hash;
                if (key == null) {
                    ModContainer modContainer = Loader.instance().activeModContainer();
                    String modContainerName = modContainer == null ? null : modContainer.getName();
                    FMLLog.bigWarning("???????????? {} ????????? ({}) ???????????????\n??????????????? " + modContainerName + " ???bug!", name, ore.getItem().getClass());
                    hash = -1;
                } else {
                    hash = Item.REGISTRY.getIDForObject(ore.getItem().delegate.get());
                }

                int oreID = getOreID(name);
                if (ore.getItemDamage() != 32767) {
                    hash |= ore.getItemDamage() + 1 << 16;
                }

                if (stackToId_1 == null) stackToId_1 = new IntMap<>();

                IntList ids = stackToId_1.get(hash);
                if (ids == null) {
                    stackToId_1.put(hash, ids = new IntList());
                }
                if (!ids.contains(oreID)) {
                    ids.add(oreID);
                    ore = ore.copy();
                    idToStack.get(oreID).add(ore);
                    MinecraftForge.EVENT_BUS.post(new OreDictionary.OreRegisterEvent(name, ore));
                }
            }
        }
    }

    @Inject("rebakeMap")
    public static void rebakeMap() {
        stackToId_1.clear();

        for (int id = 0; id < idToStack.size(); ++id) {
            NonNullList<ItemStack> ores = idToStack.get(id);
            if (ores != null) {
                for (int i = 0; i < ores.size(); i++) {
                    ItemStack ore = ores.get(i);

                    ResourceLocation name = ore.getItem().delegate.name();
                    int hash;
                    if (name == null) {
                        FMLLog.log.debug("Defaulting unregistered ore dictionary entry for ore dictionary {}: type {} to -1", getOreName(id), ore.getItem().getClass());
                        hash = -1;
                    } else {
                        hash = Item.REGISTRY.getIDForObject(ore.getItem().delegate.get());
                    }

                    if (ore.getItemDamage() != 32767) {
                        hash |= ore.getItemDamage() + 1 << 16;
                    }

                    IntList ids = stackToId_1.computeIfAbsentSp(hash, IntList::new);
                    ids.add(id);
                }
            }
        }
    }

    @Shadow("getOres")
    private static NonNullList<ItemStack> getOres(int id) {
        return EMPTY_LIST;
    }

    @Inject("getOres")
    public static NonNullList<ItemStack> getOres(String name, boolean alwaysCreateEntry) {
        return alwaysCreateEntry || nameToId_1.containsKey(name) ? getOres(getOreID(name)) : EMPTY_LIST;
    }

    @Inject("getOreID")
    public static int getOreID(String name) {
        if (nameToId_1 == null) nameToId_1 = new ToIntMap<>();
        int val = nameToId_1.getOrDefault(name, -1);
        if (val == -1) {
            idToName.add(name);
            nameToId_1.putInt(name, val = idToName.size() - 1);

            NonNullList<ItemStack> back = NonNullList.create();
            idToStack.add(back);
            idToStackUn.add(back);
        }

        return val;
    }

    @Inject("getOreIDs")
    public static int[] getOreIDs(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Stack can not be invalid!");
        } else {
            IntSet set = new IntSet();

            ResourceLocation registryName = stack.getItem().delegate.name();
            if (registryName == null) {
                FMLLog.log.warn("?????? {} ????????????.", stack);
                return new int[0];
            } else {
                int id = Item.REGISTRY.getIDForObject(stack.getItem().delegate.get());

                IntList ids = stackToId_1.get(id);
                if (ids != null) {
                    for (int i = 0; i < ids.size(); i++) {
                        set.add(ids.get(i));
                    }
                }

                ids = stackToId_1.get(id | (stack.getItemDamage() + 1 << 16));
                if (ids != null) {
                    for (int i = 0; i < ids.size(); i++) {
                        set.add(ids.get(i));
                    }
                }

                return set.toArray();
            }
        }
    }
}
