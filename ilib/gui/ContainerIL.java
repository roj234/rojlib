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

package ilib.gui;

import ilib.ImpLib;
import ilib.api.Syncable;
import ilib.tile.FieldSyncer;
import ilib.tile.TileBase;
import invtweaks.api.container.ChestContainer;
import invtweaks.api.container.ContainerSection;
import invtweaks.api.container.ContainerSectionCallback;
import roj.collect.MyHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fml.common.Optional.Method;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

/**
 * @author Roj233
 */
@ChestContainer
public abstract class ContainerIL extends Container {
    public final InventoryPlayer playerInv;
    protected final TileBase tile;
    protected final FieldSyncer fs;

    public ContainerIL(InventoryPlayer player) {
        this.tile = null;
        this.fs = null;
        this.playerInv = player;
    }

    public ContainerIL(Syncable fs, InventoryPlayer player) {
        this.tile = (TileBase) fs;
        this.fs = fs.getSyncHandler();
        this.playerInv = player;
    }

    public abstract int getInventorySize();

    /**
     * Adds the player offset with Y offset
     *
     * @param offsetY How far down
     */
    protected void addPlayerSlots(int offsetY) {
        addPlayerSlots(8, offsetY);
    }

    /**
     * Adds player inventory at location, includes space between normal and hotbar
     *
     * @param offsetX X offset
     * @param offsetY Y offset
     */
    protected void addPlayerSlots(int offsetX, int offsetY) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++)
                addSlotToContainer(new Slot(playerInv,
                        column + row * 9 + 9,
                        offsetX + column * 18,
                        offsetY + row * 18));
        }

        for (int slot = 0; slot < 9; slot++)
            addSlotToContainer(new Slot(playerInv, slot, offsetX + slot * 18, offsetY + 58));
    }

    /**
     * Take a stack from the specified inventory slot.
     * SHIFT 物品
     */
    @Nonnull
    @Override
    public ItemStack transferStackInSlot(@Nonnull EntityPlayer player, int slotId) {
        if(slotId < 0 || slotId >= inventorySlots.size())
            return ItemStack.EMPTY;

        Slot slot = inventorySlots.get(slotId);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY; //EMPTY_ITEM
        }

        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();

        if (slotId < 36) { // InventoryPlayer => TE
            if (!mergeItemStack(stack, 36, 36 + getInventorySize(), false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotId < 36 + getInventorySize()) { // TE => InventoryPlayer
            if (!mergeItemStack(stack, 0, 36, false)) {
                return ItemStack.EMPTY;
            }
        } else { // Undefined
            ImpLib.logger().error("Invalid slotId:" + slotId);

            return ItemStack.EMPTY;
        }

        if (stack.getCount() == 0) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }

        slot.onTake(player, stack); //onPickupFromSlot()

        return copy;
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        return tile.isUsableByPlayer(player);
    }

    @Override
    public final void updateProgressBar(int id, int data) {
        fs.setField(id, data, FieldSyncer.GUI);
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        if (fs != null && !player.world.isRemote) fs.closeGui((EntityPlayerMP) player);
    }

    @Override
    public void addListener(IContainerListener listener) {
        if (fs != null && listener instanceof EntityPlayerMP) {
            fs.openGui((EntityPlayerMP) listener);
        }
    }

    @ContainerSectionCallback
    @Method(modid = "inventorytweaks")
    public Map<ContainerSection, List<Slot>> getContainerSections() {
        MyHashMap<ContainerSection, List<Slot>> map = new MyHashMap<>(4);
        map.put(ContainerSection.INVENTORY, this.inventorySlots.subList(0, 36));
        map.put(ContainerSection.INVENTORY_NOT_HOTBAR, this.inventorySlots.subList(0, 27));
        map.put(ContainerSection.INVENTORY_HOTBAR, this.inventorySlots.subList(27, 36));
        map.put(ContainerSection.CHEST, this.inventorySlots.subList(36, this.inventorySlots.size()));
        return map;
    }

    //@RowSizeCallback
    //@Method(modid = "inventorytweaks")
    //public int getRowSize() { return this.rowSize; }
}
