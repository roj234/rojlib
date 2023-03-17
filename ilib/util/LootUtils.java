/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: LootUtils.java
 */
package ilib.util;

import roj.reflect.ReflectionUtils;

import net.minecraft.item.Item;
import net.minecraft.world.storage.loot.*;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;

import java.util.Iterator;
import java.util.List;

public class LootUtils {
	public static final String IF_YOU_WANT_TO_ADD_NEW_LOOT = "WHY_NOT_DESERIALIZE_JSON";

	/***
	 * Removes the specified item from the indicated loot table
	 * @return returns if any entries were removed
	 */
	public static boolean removeLootFromTable(LootTable table, Item toRemove) {
		List<LootPool> pools = ReflectionUtils.getValue(null, LootTable.class, "field_186466_c"); // pools
		for (LootPool pool : pools) {
			List<LootEntry> entries = ReflectionUtils.getValue(null, LootPool.class, "field_186453_a"); // lootEntries
			Iterator<LootEntry> it = entries.iterator();
			while (it.hasNext()) {
				LootEntry entry = it.next();
				if (entry instanceof LootEntryItem) {
					LootEntryItem lei = (LootEntryItem) entry;
					Item i = ReflectionUtils.getValue(lei, "field_186368_a"); // item
					if (i == toRemove) {
						it.remove();
						return true;
					}
				}
			}
		}
		return false;
	}

	public static LootPool createLootPool(LootEntryItem[] itemEntries, LootFunction[] functions, LootCondition[] conditions, RandomValueRange numRolls, RandomValueRange bonusRolls, String name) {
		return new LootPool(itemEntries, conditions, numRolls, bonusRolls, name);
	}

	public static void addItemToTable(LootTable table, LootPool pool) {
		table.addPool(pool);
	}
}
