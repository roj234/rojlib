package ilib.util.crt;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.data.IData;
import crafttweaker.api.entity.IEntity;
import crafttweaker.api.entity.IEntityDefinition;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.liquid.ILiquidDefinition;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.mc1120.data.NBTConverter;
import crafttweaker.mc1120.item.MCItemStack;
import crafttweaker.mc1120.liquid.MCLiquidDefinition;
import crafttweaker.mc1120.liquid.MCLiquidStack;
import ilib.util.NBTType;
import ilib.util.RecipeUtil;
import ilib.util.Registries;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;


/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@ZenClass("mods.implib.ILUtil")
@ZenRegister
public final class ILCrt {
	static final Map<Class<?>, Map<IAttribute, Double>> entityModifications = new MyHashMap<>();

	@ZenMethod
	public static void dumpAllBlocks() {
		dump(Registries.block());
	}

	@ZenMethod
	public static void dumpAllItems() {
		dump(Registries.item());
	}

	public static void dump(IForgeRegistry<?> registry) {
		StringBuilder sb = new StringBuilder();
		for (IForgeRegistryEntry<?> block : registry) {
			sb.append("注册名: ").append(block.getRegistryName()).append(" 对象: ").append(block);
		}
		CraftTweakerAPI.logInfo(sb.toString());
	}

	@ZenMethod
	public static void dumpAllEntities() {
		dump(Registries.entity());
	}

	@ZenMethod
	public static void setEntityModifications(IEntity entity, String name, double value) {
		Entity entry = (Entity) entity.getInternal();
		if (entry instanceof EntityLivingBase) {
			EntityLivingBase base = ((EntityLivingBase) entry);

			IAttribute attribute = getAttribute(name, base);

			AbstractAttributeMap attributeMap = base.getAttributeMap();
			IAttributeInstance instance = attributeMap.getAttributeInstance(attribute);
			if (instance == null) instance = attributeMap.registerAttribute(attribute);
			instance.setBaseValue(value);
		}
	}

	@ZenMethod
	public static void setSpawnEntityModifications(IEntityDefinition entity, String name, double value) {
		EntityEntry entry = (EntityEntry) entity.getInternal();

		IAttribute attribute = getAttribute(name, null);

		insert0(entry).put(attribute, value);
	}

	public static IAttribute getAttribute(String name, @Nullable EntityLivingBase entity) {
		IAttribute attribute;

		switch (name) {
			case "maxHealth":
				attribute = SharedMonsterAttributes.MAX_HEALTH;
				break;
			case "followRange":
				attribute = SharedMonsterAttributes.FOLLOW_RANGE;
				break;
			case "knockbackResistance":
				attribute = SharedMonsterAttributes.KNOCKBACK_RESISTANCE;
				break;
			case "movementSpeed":
				attribute = SharedMonsterAttributes.MOVEMENT_SPEED;
				break;
			case "flyingSpeed":
				attribute = SharedMonsterAttributes.FLYING_SPEED;
				break;
			case "attackDamage":
				attribute = SharedMonsterAttributes.ATTACK_DAMAGE;
				break;
			case "attackSpeed":
				attribute = SharedMonsterAttributes.ATTACK_SPEED;
				break;
			case "armor":
				attribute = SharedMonsterAttributes.ARMOR;
				break;
			case "armorToughness":
				attribute = SharedMonsterAttributes.ARMOR_TOUGHNESS;
				break;
			case "luck":
				attribute = SharedMonsterAttributes.LUCK;
				break;
			default:
				if (entity == null) {
					throw new IllegalArgumentException(
						"未知属性值: " + name + " 可用： maxHealth followRange knockbackResistance movementSpeed flyingSpeed" + " attackDamage attackSpeed armor armorToughness luck");
				} else {
					IAttributeInstance instance = entity.getAttributeMap().getAttributeInstanceByName(name);
					if (instance == null) {
						StringBuilder sb = new StringBuilder("未知属性值: " + name + " 可用： ");
						for (IAttributeInstance instance1 : entity.getAttributeMap().getAllAttributes()) {
							sb.append(instance1.getAttribute().getName()).append(' ');
						}
						throw new IllegalArgumentException(sb.toString());
					}
					attribute = instance.getAttribute();
				}
		}
		return attribute;
	}

	@Nonnull
	public static Map<IAttribute, Double> insert0(EntityEntry entry) {
		return entityModifications.computeIfAbsent(entry.getEntityClass(), (t) -> new MyHashMap<>(4));
	}

	@SubscribeEvent
	public static void onEntitySpawn(LivingSpawnEvent event) {
		Map<IAttribute, Double> map = entityModifications.get(event.getEntityLiving().getClass());
		if (map != null) {
			AbstractAttributeMap attributeMap = event.getEntityLiving().getAttributeMap();
			for (Map.Entry<IAttribute, Double> entry : map.entrySet()) {
				final IAttribute attr = entry.getKey();
				IAttributeInstance instance = attributeMap.getAttributeInstance(attr);
				if (instance == null) instance = attributeMap.registerAttribute(attr);
				instance.setBaseValue(entry.getValue());
				if (attr == SharedMonsterAttributes.MAX_HEALTH) {
					event.getEntityLiving().setHealth((float) (double) entry.getValue());
				}
			}
		}
	}


	@ZenMethod
	public static void removeRecipeByModId(String modId) {
		RecipeUtil.removeRecipeByModId(modId);
	}


	//https://crafttweaker.readthedocs.io/zh_CN/latest/Dev_Area/ZenAnnotations/

	// @ZenGetter("name") on a method
	// as final variable

	// @ZenSetter("name") on a method
	// called when variable "=" any
	// require: return void

	// @ZenProperty

	// @ZenMethod
	// mark this as a function
	// Optional: @ZenOperator(OperatorType.MOD) MUL ADD DIVIDE ... by such as <mi:block> * 5
	// Optional: @ZenCaster cast it to an [return_type]

	@ZenMethod
	public static ILiquidDefinition getLiquid(String name) {
		Fluid fluid = FluidRegistry.getFluid(name);
		if (fluid == null) throw new IllegalArgumentException("流体" + name + "不存在");
		return new MCLiquidDefinition(fluid);
	}

	@ZenMethod
	public static ILiquidStack getLiquidStack(String name, int amount) {
		return getLiquidStack(name, amount, null);
	}

	@ZenMethod
	public static ILiquidStack getLiquidStack(String name, int amount, IData nbt) {
		Fluid fluid = FluidRegistry.getFluid(name);
		if (fluid == null) throw new IllegalArgumentException("流体" + name + "不存在");
		return new MCLiquidStack(new FluidStack(fluid, amount, nbt == null ? null : (NBTTagCompound) NBTConverter.from(nbt)));
	}

	@ZenMethod
	public static List<IItemStack> getStackForOD(String od) {
		List<IItemStack> list = new SimpleList<>(8);
		List<ItemStack> stacks = OreDictionary.getOres(od, false);
		for (ItemStack stack : stacks) {
			list.add(new MCItemStack(stack));
		}
		return list;
	}

	@ZenMethod
	public static void hideCrTWarning() {
		CraftTweakerAPI.noWarn = true;
	}

	private static ItemStack addNS(ItemStack stack) {
		NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
		NBTTagCompound names = tag.getCompoundTag("display");
		NBTTagList lores = names.getTagList("Lore", NBTType.STRING);
		lores.appendTag(new NBTTagString("\u00a73ERROR!"));
		lores.appendTag(new NBTTagString("\u00a73invalid crafting!"));

		names.setTag("Lore", lores);
		tag.setTag("display", names);

		stack.setTagCompound(tag);
		return stack;
	}
}
