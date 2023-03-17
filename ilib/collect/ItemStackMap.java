package ilib.collect;

import roj.collect.MyHashMap;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * @author solo6975
 * @since 2022/4/6 20:35
 */
public final class ItemStackMap<V> extends MyHashMap<ItemStack, V> {
	public ItemStackMap() {}

	public ItemStackMap(Map<ItemStack, V> map) {
		putAll(map);
	}

	@Override
	protected int hash(ItemStack id) {
		int v;
		return id == null ? 0 : ((v = (Item.getIdFromItem(id.getItem()) * 31 + id.getItemDamage())) ^ (v >>> 16));
	}

	@Override
	public Entry<ItemStack, V> getEntry(ItemStack id) {
		Entry<ItemStack, V> entry = getEntryFirst(id, false);
		int dmg = id.getItemDamage();
		while (entry != null) {
			ItemStack s = entry.k;
			if (id.getItem() == s.getItem() && id.getItemDamage() == s.getItemDamage()) {
				return entry;
			}
			entry = entry.next;
		}
		if (dmg != 32767) {
			id.setItemDamage(32767);
			entry = getEntry(id);
			id.setItemDamage(dmg);
		}
		return entry;
	}
}
