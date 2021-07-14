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
package ilib.tile;

import ilib.autoreg.AutoRegTile;
import ilib.block.BlockLootrChest;
import ilib.item.handler.StandardItemHandler;
import ilib.item.handler.inv.InventoryArray;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import roj.collect.MyHashSet;
import roj.util.MyRandom;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;
import java.util.Set;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/18 13:41
 */
@AutoRegTile("ilib:loot_chest")
public class TileEntityLootrChest extends TileEntityChest {
    static Random random = new MyRandom();
    Set<String> opened = new MyHashSet<>();

    //@Override
    //public void setLootTable(ResourceLocation loc, long seed) {
    //    super.setLootTable(loc, seed);
    //}

    @Override
    public void fillWithLoot(@Nullable EntityPlayer player) {
    }

    public void fillWithLoot(@Nonnull EntityPlayer player, IInventory inventory) {
        if (this.lootTable != null) {
            LootTable table = this.world.getLootTableManager().getLootTableFromLocation(this.lootTable);
            random.setSeed(this.lootTableSeed);

            LootContext.Builder builder = new LootContext.Builder((WorldServer) this.world);
            builder.withLuck(player.getLuck()).withPlayer(player);

            table.fillInventory(inventory, random, builder.build());
        }
    }

    public boolean addPlayerOpened(EntityPlayer player) {
        return this.lootTable == null || opened.add(player.getName().toLowerCase());
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        if (opened.size() != 0) {
            NBTTagList list = new NBTTagList();
            for (String s : opened) {
                list.appendTag(new NBTTagString(s));
            }
            compound.setTag("opened", list);
        }
        super.readFromNBT(compound);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = compound.getTagList("opened", 8);
        for (int i = 0; i < list.tagCount(); i++) {
            opened.add(list.getStringTagAt(i));
        }
        return super.writeToNBT(compound);
    }

    public IInventory getInv(EntityPlayer player) {
        InventoryArray fakeInventory = new InventoryArray(3 * 9, ItemStack.EMPTY);
        IInventory mcInventory = new BlockLootrChest.MyInv(new StandardItemHandler(fakeInventory));
        if (addPlayerOpened(player)) {
            fillWithLoot(player, mcInventory);
            markDirty();
        }

        return mcInventory;
    }
}
