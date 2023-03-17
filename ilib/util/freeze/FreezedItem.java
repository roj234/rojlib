package ilib.util.freeze;

import ilib.util.MCTexts;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 冻结的物品
 *
 * @author Roj233
 * @since 2021/8/26 19:36
 */
public final class FreezedItem extends Item {
	public FreezedItem() {
		setHasSubtypes(true);
		setNoRepair();
	}

	@Override
	public String getTranslationKey(ItemStack stack) {
		return "item.ilib.freezed";
	}

	@Override
	public String getTranslationKey() {
		return "item.ilib.freezed";
	}

	@Override
	public String getItemStackDisplayName(ItemStack stack) {
		return MCTexts.format(this.getTranslationKey(stack)).trim();
	}

	@Override
	public int hashCode() {
		return getRegistryName() == null ? 0 : getRegistryName().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof FreezedItem && ((FreezedItem) obj).getRegistryName().equals(getRegistryName());
	}
}
