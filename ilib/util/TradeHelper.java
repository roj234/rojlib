package ilib.util;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipe;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class TradeHelper {
	public static EntityVillager.ITradeList withChance(EntityVillager.ITradeList list, double chance) {
		return (merchant, recipeList, random) -> {
			boolean i = random.nextDouble() < chance;
			if (i) {
				list.addMerchantRecipe(merchant, recipeList, random);
			}
		};
	}

	public static EntityVillager.ITradeList toEmerald(ItemStack item, int min, int max) {
		return toEmerald(item, new EntityVillager.PriceInfo(min, max));
	}

	public static EntityVillager.ITradeList toEmerald(ItemStack item, EntityVillager.PriceInfo buy) {
		return (merchant, recipeList, random) -> {
			int i = buy.getPrice(random);
			recipeList.add(new MerchantRecipe(getStack(item, (i < 0) ? 1 : i), getStack(Items.EMERALD, (i < 0) ? -i : 1)));
		};
	}

	public static EntityVillager.ITradeList toEmerald(ItemStack item, ItemStack item2, int min, int max) {
		return toEmerald(item, item2, new EntityVillager.PriceInfo(min, max));
	}

	public static EntityVillager.ITradeList toEmerald(ItemStack item, ItemStack item2, EntityVillager.PriceInfo buy) {
		return (merchant, recipeList, random) -> {
			int i = buy.getPrice(random);
			int i2 = buy.getPrice(random);
			recipeList.add(new MerchantRecipe(getStack(item, (i < 0) ? 1 : i), getStack(item, (i2 < 0) ? 1 : i2), getStack(Items.EMERALD, (i < 0) ? -i : 1)));
		};
	}

	public static EntityVillager.ITradeList fromEmerald(ItemStack item, int min, int max) {
		return fromEmerald(item, new EntityVillager.PriceInfo(min, max));
	}

	public static EntityVillager.ITradeList fromEmerald(ItemStack item, EntityVillager.PriceInfo sell) {
		return (merchant, recipeList, random) -> {
			int i = sell.getPrice(random);
			recipeList.add(new MerchantRecipe(getStack(Items.EMERALD, (i < 0) ? 1 : i), getStack(item, (i < 0) ? -i : 1)));
		};
	}

	public static EntityVillager.ITradeList std(ItemStack in1, ItemStack in2, ItemStack out) {
		return (merchant, recipeList, random) -> {
			recipeList.add(new MerchantRecipe(in1, in2, out));
		};
	}

	public static EntityVillager.ITradeList enchanted(ItemStack item, int min, int max) {
		return enchanted(item, new EntityVillager.PriceInfo(min, max));
	}

	public static EntityVillager.ITradeList enchanted(ItemStack item, EntityVillager.PriceInfo buy) {
		return (merchant, recipeList, random) -> {
			int i = buy.getPrice(random);

			ItemStack result = EnchantmentHelper.addRandomEnchantment(random, getStack(item, 1), 5 + random.nextInt(15), false);
			recipeList.add(new MerchantRecipe(getStack(Items.EMERALD, (i < 0) ? 1 : i), result));
		};
	}

	public static ItemStack getStack(ItemStack item, int count) {
		item = item.copy();
		item.setCount(count);
		return item;
	}

	public static ItemStack getStack(Item item, int count) {
		return new ItemStack(item, count);
	}
}