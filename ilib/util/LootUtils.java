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
/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: LootUtils.java
 */
package ilib.util;

import net.minecraft.item.Item;
import net.minecraft.world.storage.loot.*;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import roj.reflect.ReflectionUtils;

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
