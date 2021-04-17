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

package ilib.util;

import com.google.common.collect.BiMap;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerProfession;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry.AddCallback;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.registries.RegistryBuilder;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class Registries {
    public static <E extends IForgeRegistryEntry<E>> IForgeRegistry<E> create(ResourceLocation registryName, Class<E> registryType, AddCallback<E> callback) {
        return create(registryName, registryType, 0, 127, callback);
    }

    public static <E extends IForgeRegistryEntry<E>> IForgeRegistry<E> create(ResourceLocation registryName, Class<E> registryType, int minId, int maxId, AddCallback<E> callback) {
        return (new RegistryBuilder<E>()).setName(registryName).addCallback(callback).setType(registryType).setIDRange(minId, maxId).create();
    }

    public static IForgeRegistry<Block> block() {
        return ForgeRegistries.BLOCKS;
    }

    public static IForgeRegistry<Item> item() {
        return ForgeRegistries.ITEMS;
    }

    public static IForgeRegistry<Potion> potion() {
        return ForgeRegistries.POTIONS;
    }

    public static IForgeRegistry<Biome> biome() {
        return ForgeRegistries.BIOMES;
    }

    public static IForgeRegistry<SoundEvent> soundEvent() {
        return ForgeRegistries.SOUND_EVENTS;
    }

    public static IForgeRegistry<PotionType> potionType() {
        return ForgeRegistries.POTION_TYPES;
    }

    public static IForgeRegistry<Enchantment> enchantment() {
        return ForgeRegistries.ENCHANTMENTS;
    }

    public static IForgeRegistry<VillagerProfession> villagerProfession() {
        return ForgeRegistries.VILLAGER_PROFESSIONS;
    }

    public static IForgeRegistry<IRecipe> recipe() {
        return ForgeRegistries.RECIPES;
    }

    public static IForgeRegistry<EntityEntry> entity() {
        return ForgeRegistries.ENTITIES;
    }

    public static BiMap<Block, Item> b2i() {
        return GameData.getBlockItemMap();
    }
}
