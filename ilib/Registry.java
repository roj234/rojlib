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
package ilib;

import com.google.common.collect.BiMap;
import ilib.api.PreInitCompleteEvent;
import ilib.client.register.BlockModelInfo;
import ilib.client.register.ItemModelInfo;
import ilib.client.register.ModelInfo;
import ilib.item.fake.FakeItemBlock;
import ilib.item.fake.ItemBlockMissing;
import ilib.util.ForgeUtil;
import ilib.util.Registries;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.VillagerRegistry;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class Registry {
    public static String NS = null;

    private static ArrayList<Block> BLOCKS = new ArrayList<>();
    private static ArrayList<Item> ITEMS = new ArrayList<>();

    private static ArrayList<ModelInfo> MODELS = new ArrayList<>();

    static {
        MinecraftForge.EVENT_BUS.register(Registry.class);
    }

    public static void fake(String modid, String id, Block block) {
        BLOCKS.add(block.setRegistryName(modid, id).setTranslationKey("fake." + modid + '.' + id));

        fake(modid, id, new FakeItemBlock(block));
    }

    public static void fake(String modid, String id, Item item) {
        ITEMS.add(item.setRegistryName(modid, id).setTranslationKey("fake." + modid + "." + id).setMaxStackSize(64));
    }

    /**
     * 不注册物品
     */
    public static void block(String id, Block block) {
        block(id, block, null, null, 0, false);
    }

    public static void block(String id, Block block, boolean mmodel) {
        block(id, block);
        model(new BlockModelInfo(block, true));
    }

    public static void block(String id, Block block, Item item, CreativeTabs tab, int stack, boolean model) {
        BLOCKS.add(block.setRegistryName(NS, id).setTranslationKey(NS + '.' + id));
        if (tab != null)
            block.setCreativeTab(tab);

        if (model)
            model(new BlockModelInfo(block, false));

        if (item != null) {
            item(id, item, tab, stack, false);
            if (model && !(item instanceof ItemBlock))
                model(new ItemModelInfo(item, 0, false, true));
        }
    }

    public static void item(String id, Item item, CreativeTabs tab, int stack, boolean model) {
        ITEMS.add(item.setRegistryName(NS, id).setTranslationKey(NS + '.' + id).setCreativeTab(tab));
        if (model)
            model(new ItemModelInfo(item, false));

        if (stack > 0) item.setMaxStackSize(stack);
    }

    public static void egg(String entityName, int color1, int color2) {
        ResourceLocation loc = new ResourceLocation(entityName);
        EntityEntry entry = Registries.entity().getValue(loc);
        if (entry != null) {
            EntityList.EntityEggInfo egg = new EntityList.EntityEggInfo(loc, color1, color2);
            entry.setEgg(egg);
        }
    }

    public static SoundEvent sound(String id) {
        SoundEvent sound = new SoundEvent(new ResourceLocation(NS, id)).setRegistryName(NS, id);
        Registries.soundEvent().register(sound);
        return sound;
    }

    public static void biome(ResourceLocation loc, Biome recipe) {
        Registries.biome().register(recipe.setRegistryName(loc));
    }

    public static void biome(String loc, Biome biome) {
        biome(new ResourceLocation(NS, loc), biome);
    }

    public static void recipe(ResourceLocation loc, IRecipe recipe) {
        Registries.recipe().register(recipe.setRegistryName(loc));
    }

    public static void recipe(String id, IRecipe recipe) {
        recipe(new ResourceLocation(NS, id), recipe);
    }

    public static void enchant(String id, Enchantment enchant) {
        Registries.enchantment().register(enchant.setName(NS + "." + id).setRegistryName(new ResourceLocation(NS, id)));
    }

    public static void potion(String id, Potion potion) {
        String k = NS + '.' + id;
        ResourceLocation loc = new ResourceLocation(NS, id);
        Registries.potion().register(potion.setPotionName("effect." + k).setRegistryName(loc));
        Registries.potionType().register(new PotionType(k, new PotionEffect(potion)).setRegistryName(loc));
    }

    public static VillagerRegistry.VillagerProfession prof(String name, String texName) {
        final VillagerRegistry.VillagerProfession profession = new VillagerRegistry.VillagerProfession(NS + ':' + name, NS + ":textures/entity/villager/" + texName + ".png", NS + ":textures/entity/villager/" + texName + "_zombie.png");
        Registries.villagerProfession().register(profession);
        return profession;
    }

    public static VillagerRegistry.VillagerProfession prof(String name) {
        return prof(name, name);
    }

    private static List<Item> generateMissingItems() {
        IForgeRegistry<Block> registryBlock = Registries.block();
        IForgeRegistry<Item> registryItems = Registries.item();
        BiMap<Block, Item> blockItemBiMap = Registries.b2i();

        List<Item> items = new ArrayList<>();
        for (Block block : registryBlock.getValuesCollection()) {
            if (blockItemBiMap.get(block) == null && registryItems.getValue(block.getRegistryName()) == null) {
                Item item = new ItemBlockMissing(block).setRegistryName(block.getRegistryName());
                items.add(item);
            }
        }
        return items;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void postRegItem(RegistryEvent.Register<Item> event) {
        MinecraftForge.EVENT_BUS.post(new PreInitCompleteEvent(false));

        if (Config.enableMissingItemCreation) {
            for (Item item : generateMissingItems())
                event.getRegistry().register(item);
        }
    }

    public static void registerFluidBlock(String fluid, BlockFluidBase block) {
        String modid = ForgeUtil.getCurrentModId();

        BLOCKS.add(block.setRegistryName(modid, fluid).setTranslationKey(modid + '.' + fluid));

        ImpLib.proxy.registerFluidModel(fluid, block);
    }


    public static void model(ModelInfo modelInfo) {
        MODELS.add(modelInfo);
    }

    public static void mergedModel(Block block) {
        model(new BlockModelInfo(block, true));
    }

    public static void mergedModel(Item item) {
        model(new ItemModelInfo(item, true));
    }

    /////////////////////////////////// Call by events

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        for (Block block : BLOCKS) {
            try {
                event.getRegistry().register(block);
            } catch (ArrayIndexOutOfBoundsException ex) {
                try {
                    throw new MoreIdMissingException(block);
                } catch (Error e) {
                    throw new RuntimeException("MoreId mod is missing due to meta limit overhead of block " + block);
                }
            }
        }
        BLOCKS.clear();
        BLOCKS = null;
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        for (Item item : ITEMS)
            event.getRegistry().register(item);
        ITEMS.clear();
        ITEMS = null;
    }

    public static List<ModelInfo> getModels() {
        if (MODELS != null) {
            List<ModelInfo> models = new ArrayList<>(MODELS);
            MODELS.clear();
            MODELS = null;
            return models;
        }
        return null;
    }

    public static ArrayList<Block> blocks() {
        return BLOCKS;
    }

    public static ArrayList<Item> items() {
        return ITEMS;
    }

    public static void namespace(String namespace) {
        NS = namespace;
    }

}
