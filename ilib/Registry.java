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
import ilib.client.model.ILBlockModel;
import ilib.client.model.ILFluidModel;
import ilib.client.model.ILItemModel;
import ilib.client.model.ModelInfo;
import ilib.item.fake.FakeItemBlock;
import ilib.item.fake.ItemBlockMissing;
import ilib.util.ForgeUtil;
import ilib.util.Hook;
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
import roj.collect.SimpleList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class Registry {
    public static String NS = null;

    private static SimpleList<ModelInfo> MODELS = new SimpleList<>();

    static {
        MinecraftForge.EVENT_BUS.register(Registry.class);
    }

    public static void fake(String modid, String id, Block block) {
        Registries.block().register(block.setRegistryName(modid, id).setTranslationKey("fake." + modid + '.' + id));

        fake(modid, id, new FakeItemBlock(block));
    }

    public static void fake(String modid, String id, Item item) {
        Registries.item().register(item.setRegistryName(modid, id).setTranslationKey("fake." + modid + "." + id).setMaxStackSize(64));
    }

    /**
     * 不注册物品
     */
    public static void block(String id, Block block) {
        Registries.block().register(block.setRegistryName(NS, id).setTranslationKey(NS + '.' + id));
    }

    public static void block(String id, Block block, boolean mmodel) {
        block(id, block);
        ILBlockModel.Merged(block);
    }

    public static void block(String id, Block block, Item item, CreativeTabs tab, int stack, boolean model) {
        Registries.block().register(block.setRegistryName(NS, id).setTranslationKey(NS + '.' + id));
        if (tab != null) block.setCreativeTab(tab);

        if (item != null) {
            item(id, item, tab, stack, false);
            if (model && !(item instanceof ItemBlock))
                ILItemModel.Vanilla(item);
        }
    }

    public static void item(String id, Item item, CreativeTabs tab, int stack, boolean model) {
        Registries.item().register(item.setRegistryName(NS, id).setTranslationKey(NS + '.' + id).setCreativeTab(tab));
        if (model) ILItemModel.Vanilla(item);

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
                Registries.item().register(item);
            }
        }
        return items;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void postRegItem(RegistryEvent.Register<Item> event) {
        if (!ImpLib.isClient) ImpLib.HOOK.remove(Hook.MODEL_REGISTER);

        if (Config.enableMissingItemCreation) {
            for (Item item : generateMissingItems())
                event.getRegistry().register(item);
        }
    }

    public static void registerFluidBlock(String fluid, BlockFluidBase block) {
        String modid = ForgeUtil.getCurrentModId();

        Registries.block().register(block.setRegistryName(modid, fluid).setTranslationKey(modid + '.' + fluid));

        MODELS.add(new ILFluidModel(fluid, block));
    }


    public static void model(ModelInfo info) {
        if (info != null) MODELS.add(info);
    }

    public static List<ModelInfo> getModels() {
        if (MODELS != null) {
            List<ModelInfo> models = MODELS;
            MODELS = null;
            return models;
        }
        return Collections.emptyList();
    }

    public static void namespace(String namespace) {
        NS = namespace;
    }
}
