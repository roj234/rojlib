package ilib.collect;

import roj.collect.UnsortedMultiKeyMap;

import net.minecraft.item.ItemStack;

import net.minecraftforge.oredict.OreDictionary;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/4/6 20:40
 */
public class StackComparator implements UnsortedMultiKeyMap.Keys<ItemStack, String> {
	public static final StackComparator ALL = new StackComparator();
	public static final StackComparator DAMAGE_ONLY = new StackComparator();

	@Override
	public List<String> getKeys(ItemStack key, List<String> holder) {
		if (key.isEmpty()) {
			holder.add("minecraft:air");
		} else {
			int[] oreIds = OreDictionary.getOreIDs(key);
			for (int i : oreIds) holder.add(OreDictionary.getOreName(i));

			String s = key.getItem().getRegistryName().toString();
			holder.add(s + ':' + key.getItemDamage());
			if (this == ALL) holder.add(s);
		}
		return holder;
	}

	@Override
	public String getPrimaryKey(ItemStack key) {
		if (key.isEmpty()) return "minecraft:air";

		int[] oreIds = OreDictionary.getOreIDs(key);
		if (oreIds.length > 0) return OreDictionary.getOreName(oreIds[0]);

		return key.getItem().getRegistryName().toString() + ':' + key.getItemDamage();
	}

	@Override
	public int compare(String o1, String o2) {
		return o1.compareTo(o2);
	}
}
