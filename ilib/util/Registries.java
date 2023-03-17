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
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class Registries {
	public static <E extends IForgeRegistryEntry<E>> IForgeRegistry<E> create(ResourceLocation name, Class<E> type, AddCallback<E> callback) {
		return create(name, type, 0, 127, callback);
	}

	public static <E extends IForgeRegistryEntry<E>> IForgeRegistry<E> create(ResourceLocation name, Class<E> type, int minId, int maxId, AddCallback<E> callback) {
		return new RegistryBuilder<E>().setName(name).addCallback(callback).setType(type).setIDRange(minId, maxId).create();
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
