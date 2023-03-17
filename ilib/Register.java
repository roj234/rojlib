package ilib;

import com.google.common.collect.BiMap;
import ilib.client.CreativeTabsMy;
import ilib.client.model.ILBlockModel;
import ilib.client.model.ILFluidModel;
import ilib.client.model.ILItemModel;
import ilib.client.model.ModelInfo;
import ilib.item.ItemBlockMI;
import ilib.util.ForgeUtil;
import ilib.util.Hook;
import ilib.util.Registries;
import roj.collect.SimpleList;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
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

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class Register {
	public static String NS = null;

	private static SimpleList<ModelInfo> MODELS = new SimpleList<>();

	static {
		MinecraftForge.EVENT_BUS.register(Register.class);
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
		try {
			Registries.block().register(block.setRegistryName(NS, id).setTranslationKey(NS + '.' + id));
		} catch (ArrayIndexOutOfBoundsException e) {
			System.err.println("Block " + id + " meta overflow");
		}
		if (tab != null) block.setCreativeTab(tab);

		if (item != null) {
			item(id, item, tab, stack, false);
			if (model && !(item instanceof ItemBlock)) ILItemModel.Vanilla(item);
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
		final VillagerRegistry.VillagerProfession profession = new VillagerRegistry.VillagerProfession(NS + ':' + name, NS + ":textures/entity/villager/" + texName + ".png",
																									   NS + ":textures/entity/villager/" + texName + "_zombie.png");
		Registries.villagerProfession().register(profession);
		return profession;
	}

	public static VillagerRegistry.VillagerProfession prof(String name) {
		return prof(name, name);
	}

	private static void generateMissingItems() {
		CreativeTabsMy tab = new CreativeTabsMy("ilib.missing").setIcon(new ItemStack(Blocks.BEDROCK));

		IForgeRegistry<Item> items = Registries.item();
		BiMap<Block, Item> b2i = Registries.b2i();

		for (Block block : Registries.block()) {
			if (b2i.get(block) == null && items.getValue(block.getRegistryName()) == null) {
				items.register(new ItemBlockMI(block).setRegistryName(block.getRegistryName()).setCreativeTab(tab));
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void postRegItem(RegistryEvent.Register<Item> event) {
		if (!ImpLib.isClient) ImpLib.EVENT_BUS.remove(Hook.MODEL_REGISTER);

		if (Config.enableMissingItemCreation) {
			generateMissingItems();
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
