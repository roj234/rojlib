package ilib.util;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class EnchantmentHelper extends net.minecraft.enchantment.EnchantmentHelper {
	public static boolean hasEnchantment(Enchantment ench, ItemStack is) {
		NBTTagList list = is.getItem() == Items.ENCHANTED_BOOK ? ItemEnchantedBook.getEnchantments(is) : is.getEnchantmentTagList();
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound tag = list.getCompoundTagAt(i);
			Enchantment found = Enchantment.getEnchantmentByID(tag.getShort("id"));
			if (found == ench) return true;
		}
		return false;
	}

	public static boolean canStackEnchant(ItemStack stack, Enchantment ench) {
		if (Items.ENCHANTED_BOOK == stack.getItem()) return true;
		if (!ench.canApply(stack)) return false;
		for (Enchantment ench1 : net.minecraft.enchantment.EnchantmentHelper.getEnchantments(stack).keySet()) {
			if (ench1 != null && ench1 != ench && !ench1.isCompatibleWith(ench)) return false;
		}
		return true;
	}

	public static int getApplyCost(Enchantment enchantment, int lvl) {
		int rarityFactor = 10;
		Enchantment.Rarity rarity = enchantment.getRarity();
		switch (rarity) {
			case COMMON:
				rarityFactor = 1;
				break;
			case UNCOMMON:
				rarityFactor = 2;
				break;
			case RARE:
				rarityFactor = 4;
				break;
			case VERY_RARE:
				rarityFactor = 8;
				break;
		}
		return rarityFactor * lvl;
	}
}