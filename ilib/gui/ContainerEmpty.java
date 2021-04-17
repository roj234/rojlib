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
import ilib.util.InventoryUtil;
import invtweaks.api.container.ChestContainer;
import invtweaks.api.container.ContainerSection;
import invtweaks.api.container.ContainerSectionCallback;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional.Method;
import roj.collect.MyHashMap;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * @author Roj233
 * @since ?
 */
@ChestContainer
public class ContainerEmpty extends Container {
    public final InventoryPlayer playerInv;

    int rowSize;

    public ContainerEmpty(InventoryPlayer player) {
        this.playerInv = player;
    }

    /**
     * 获取TileEntity + Upgrade物品栏大小
     *
     * @return size
     */
    public int getInventorySize() {
        return 0;
    }

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

    @Override
    public ItemStack slotClick(final int slotId, final int button, final ClickType type, final EntityPlayer player) {
        ItemStack stack = ItemStack.EMPTY;

        final InventoryPlayer invPlayer = player.inventory;
        ItemStack hoverStack = invPlayer.getItemStack();

        if (type == ClickType.QUICK_CRAFT) {
            final int old = this.dragEvent;

            final int de = this.dragEvent = getDragEvent(button);
            if ((old != 1 || de != 2) && old != de) {
                this.resetDrag();
            } else if (hoverStack.isEmpty()) {
                this.resetDrag();
            }

            switch (de) {
                case 0: {
                    if (isValidDragMode(this.dragMode = extractDragMode(button), player)) {
                        this.dragEvent = 1;
                        this.dragSlots.clear();
                    } else {
                        this.resetDrag();
                    }
                }
                break;
                case 1: {
                    final Slot slot = this.inventorySlots.get(slotId);
                    if (slot != null && canAddItemToSlot(slot, hoverStack, true) && slot.isItemValid(hoverStack) && hoverStack.getCount() > this.dragSlots.size() && this.canDragIntoSlot(slot)) {
                        this.dragSlots.add(slot);
                    }
                }
                break;
                case 2: {
                    if (!this.dragSlots.isEmpty()) {
                        int num = hoverStack.getCount();
                        for (final Slot target : this.dragSlots) {
                            if(target == null) {
                                continue;
                            }
                            final ItemStack inSlot = target.getStack();
                            if((!target.getHasStack() &&
                                    (!InventoryUtil.areItemStacksEqual(inSlot, hoverStack) ||
                                    inSlot.getCount() + hoverStack.getCount() > inSlot.getMaxStackSize())) ||
                                    !target.isItemValid(hoverStack)) {
                                continue;
                            }

                            if (hoverStack.getCount() >= this.dragSlots.size() && this.canDragIntoSlot(target)) {
                                final ItemStack copy = hoverStack.copy();
                                final int count = target.getHasStack() ? inSlot.getCount() : 0;

                                computeStackSize(this.dragSlots, this.dragMode, copy, count);

                                int max = Math.min(copy.getMaxStackSize(), target.getItemStackLimit(hoverStack));
                                if (copy.getCount() > max) {
                                    copy.setCount(max);
                                }

                                num -= copy.getCount() - count;
                                target.putStack(copy);
                            }
                        }

                        hoverStack = num <= 0 ? ItemStack.EMPTY : hoverStack.copy();
                        if (num > 0) {
                            hoverStack.setCount(num);
                        }
                        invPlayer.setItemStack(hoverStack);
                    }
                    this.resetDrag();
                }
                break;
                default:
                    this.resetDrag();
            }
        } else if (this.dragEvent != 0) {
            this.resetDrag();
        } else if ((type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) && (button == 0 || button == 1)) {
            if (slotId == -999) {
                if(!onClickBorder(button, hoverStack, player)) {
                    if (!hoverStack.isEmpty()) {
                        if (button == 0) {
                            player.dropItem(hoverStack, true);
                            invPlayer.setItemStack(ItemStack.EMPTY);
                        } else {
                            player.dropItem(hoverStack.splitStack(1), true);
                            if (hoverStack.getCount() == 0) {
                                invPlayer.setItemStack(ItemStack.EMPTY);
                            }
                        }
                    }
                }
            } else if (type == ClickType.QUICK_MOVE) {
                if (slotId < 0) {
                    return ItemStack.EMPTY;
                }

                final Slot slot = this.inventorySlots.get(slotId);
                if (slot != null && slot.canTakeStack(player)) {
                    final ItemStack fromSlot = this.transferStackInSlot(player, slotId);
                    if (!fromSlot.isEmpty()) {
                        final Item item = fromSlot.getItem();
                        stack = fromSlot.copy();
                        if (slot.getHasStack() && InventoryUtil.areItemStacksEqual(slot.getStack(), fromSlot)) {
                            this.slotClick(slotId, button, ClickType.QUICK_MOVE, player);
                        }
                    }
                }
            } else {
                if (slotId < 0) {
                    return ItemStack.EMPTY;
                }
                final Slot slot = this.inventorySlots.get(slotId);
                if (slot != null) {
                    ItemStack inSlot = slot.getStack();
                    if (!inSlot.isEmpty()) {
                        stack = inSlot.copy();

                        if (slot.canTakeStack(player)) {
                            if (hoverStack.isEmpty()) {
                                final int count = (button == 0) ? inSlot.getCount() : ((inSlot.getCount() + 1) / 2);
                                final ItemStack extract = slot.decrStackSize(count);
                                invPlayer.setItemStack(extract);
                                if (inSlot.getCount() == 0) {
                                    slot.putStack(ItemStack.EMPTY);
                                }
                                slot.onTake(player, hoverStack);
                            } else if (slot.isItemValid(hoverStack)) {
                                if (InventoryUtil.areItemStacksEqual(hoverStack, inSlot)) {
                                    int count = (button == 0) ? hoverStack.getCount() : 1;
                                    if (count > slot.getSlotStackLimit() - inSlot.getCount()) {
                                        count = slot.getSlotStackLimit() - inSlot.getCount();
                                    }
                                    if (count > hoverStack.getMaxStackSize() - inSlot.getCount()) {
                                        count = hoverStack.getMaxStackSize() - inSlot.getCount();
                                    }
                                    hoverStack.splitStack(count);
                                    if (hoverStack.getCount() == 0) {
                                        invPlayer.setItemStack(ItemStack.EMPTY);
                                    }
                                    inSlot.grow(count);
                                    slot.putStack(inSlot);
                                } else if (hoverStack.getCount() <= slot.getSlotStackLimit()) {
                                    slot.putStack(hoverStack);
                                    invPlayer.setItemStack(inSlot);
                                }
                            } else if (hoverStack.getMaxStackSize() > 1 && InventoryUtil.areItemStacksEqual(hoverStack, inSlot)) {
                                final int count = inSlot.getCount();
                                if (count > 0 && count + hoverStack.getCount() <= hoverStack.getMaxStackSize()) {
                                    hoverStack.grow(count);
                                    inSlot = slot.decrStackSize(count);
                                    if (inSlot.getCount() == 0) {
                                        slot.putStack(ItemStack.EMPTY);
                                    }
                                    slot.onTake(player, hoverStack);
                                }
                            }
                        }
                    } else {
                        if (!hoverStack.isEmpty() && slot.isItemValid(hoverStack)) {
                            int l2 = (button == 0) ? hoverStack.getCount() : 1;
                            if (l2 > slot.getSlotStackLimit()) {
                                l2 = slot.getSlotStackLimit();
                            }
                            if (hoverStack.getCount() >= l2) {
                                slot.putStack(hoverStack.splitStack(l2));
                            }
                            if (hoverStack.getCount() == 0) {
                                invPlayer.setItemStack(ItemStack.EMPTY);
                            }
                        }
                    }
                    slot.onSlotChanged();
                }
            }
        } else {
            if (slotId < 0 || slotId >= inventorySlots.size()) {
                return stack;
            }

            Slot slot = this.inventorySlots.get(slotId);
            if (slot == null) {
                return stack;
            }

            switch (type) {
                case SWAP: {
                    if (button >= 0 && button < 9) {
                        if(isHotbarItem(button)) {
                            return stack;
                        }

                        if (slot.canTakeStack(player)) {
                            final ItemStack quick = invPlayer.getStackInSlot(button);
                            boolean flag = quick.isEmpty() || (slot.inventory == invPlayer && slot.isItemValid(quick));
                            int emptyId = -1;
                            if (!flag) {
                                emptyId = invPlayer.getFirstEmptyStack();
                                flag = (emptyId > -1);
                            }

                            if (slot.getHasStack() && flag) {
                                final ItemStack itemstack6 = slot.getStack();
                                invPlayer.setInventorySlotContents(button, itemstack6.copy());
                                if ((slot.inventory != invPlayer || !slot.isItemValid(quick)) && !quick.isEmpty()) {
                                    if (emptyId > -1) {
                                        invPlayer.addItemStackToInventory(quick);
                                        slot.decrStackSize(itemstack6.getCount());
                                        slot.putStack(ItemStack.EMPTY);
                                        slot.onTake(player, itemstack6);
                                    }
                                } else {
                                    slot.decrStackSize(itemstack6.getCount());
                                    slot.putStack(quick);
                                    slot.onTake(player, itemstack6);
                                }
                            } else if (!slot.getHasStack() && !quick.isEmpty() && slot.isItemValid(quick)) {
                                invPlayer.setInventorySlotContents(button, ItemStack.EMPTY);
                                slot.putStack(quick);
                            }
                        }
                    }
                }
                break;
                case CLONE: {
                    if (canCloneItem(player) && hoverStack.isEmpty()) {
                        if (slot.getHasStack()) {
                            final ItemStack copy = slot.getStack().copy();
                            copy.setCount(copy.getMaxStackSize());
                            invPlayer.setItemStack(copy);
                        }
                    }
                }
                break;
                case THROW: {
                    if (slot.getHasStack() && slot.canTakeStack(player)) {
                        final ItemStack extract = slot.decrStackSize((button == 0) ? 1 : slot.getStack().getCount());
                        if (!extract.isEmpty()) {
                            slot.onTake(player, extract);
                            player.dropItem(extract, true);
                        }
                    }
                }
                break;
                case PICKUP_ALL: {
                    if (!hoverStack.isEmpty() && (!slot.getHasStack() || !slot.canTakeStack(player))) {
                        final int size = this.inventorySlots.size();
                        final int str = (button == 0) ? 0 : (size - 1);
                        final int way = (button == 0) ? 1 : -1;

                        out:
                        for (int i = 0; i < 2; i++) {
                            for (int j = str; j >= 0 && j < size; j += way) {
                                slot = this.inventorySlots.get(j);

                                final ItemStack slotStack = slot.getStack();
                                if (slot.getHasStack() && InventoryUtil.areItemStacksEqual(hoverStack, slotStack) && slot.canTakeStack(player) && this.canMergeSlot(hoverStack, slot) && (i != 0 || slotStack.getCount() != slotStack.getMaxStackSize())) {
                                    final int remain = hoverStack.getMaxStackSize() - hoverStack.getCount();
                                    int count = slotStack.getCount();
                                    if(count > remain) {
                                        count = remain;
                                    }
                                    final ItemStack split = slot.decrStackSize(count);

                                    hoverStack.grow(count);

                                    if (split.getCount() <= 0) {
                                        slot.putStack(ItemStack.EMPTY);
                                    }
                                    slot.onTake(player, split);

                                    if(count == remain) {
                                        break out;
                                    }
                                }
                            }
                        }
                    }

                    this.detectAndSendChanges();
                }
                break;
            }
        }

        return stack;
    }

    protected boolean canCloneItem(EntityPlayer player) {
        return player.capabilities.isCreativeMode;
    }

    protected boolean isHotbarItem(int button) {
        return false;
    }

    protected boolean onClickBorder(int button, ItemStack hoverStack, EntityPlayer player) {
        return false;
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

    /**
     * player能否与GUI交互?
     *
     * @return can?
     */
    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        return true;
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
