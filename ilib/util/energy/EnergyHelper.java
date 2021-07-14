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

package ilib.util.energy;

import ilib.api.energy.IMEnergy;
import ilib.api.energy.IMEnergyCap;
import ilib.api.mark.MInIntractable;
import ilib.capabilities.Capabilities;
import ilib.util.ItemNBT;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import roj.collect.SimpleList;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.util.List;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class EnergyHelper {
    private EnergyHelper() {
    }

    public static final IMEnergyCap EMPTYCAP = new IMEnergyCap() {
        @Override
        public NBTTagCompound serializeNBT() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deserializeNBT(NBTTagCompound nbtTagCompound) {
            throw new UnsupportedOperationException();
        }

        public IMEnergyCap setMaxME(int i) {
            return this;
        }

        public IMEnergyCap setReceiveSpeed(int i) {
            return this;
        }

        public IMEnergyCap setExtractSpeed(int i) {
            return this;
        }

        public IMEnergyCap setVolRequired(int volRequired) {
            return this;
        }

        public int currentME() {
            return 0;
        }

        public int maxME() {
            return 0;
        }

        public boolean canExtract() {
            return false;
        }

        public boolean canReceive() {
            return false;
        }

        public IMEnergyCap setME(int count) {
            return this;
        }

        public int extractME(int count, boolean simulate) {
            return 0;
        }

        public int receiveME(int count, boolean simulate) {
            return 0;
        }

        public int volRequired() {
            return -1;
        }

        public int receiveSpeed() {
            return 0;
        }

        public int extractSpeed() {
            return 0;
        }
    };

    /* NBT TAG HELPER */
    public static void addEnergyInformation(@Nonnull ItemStack stack, @Nonnull List<String> list) {
        IMEnergyCap cap = stack.getCapability(Capabilities.MENERGY, null);
        if (cap != null) {
            list.add(I18n.format("tooltip.mi.energy") +
                    TextUtil.getScaledNumber(getEnergyStored(stack)) +
                    " / " +
                    TextUtil.getScaledNumber(cap.maxME()) + " ME");
        } else if (ItemNBT.getDataMap(stack).hasKey("Power")) {
            list.add(I18n.format("tooltip.mi.energy") +
                    TextUtil.getScaledNumber(ItemNBT.getInt(stack, "Power")) +
                    " / " +
                    TextUtil.getScaledNumber(ItemNBT.getInt(stack, "MaxME")) + " ME");
        } else
            list.add("NaN/NaN ME");
    }

    @Nonnull
    public static String addEnergyInformation(@Nonnull TileEntity te) {
        IMEnergy cap = te.getCapability(Capabilities.MENERGY_TILE, null);
        if (cap != null) {
            return I18n.format("tooltip.mi.energy") +
                    TextUtil.getScaledNumber(cap.currentME()) + " / " +
                    TextUtil.getScaledNumber(cap.maxME()) + " ME";
        }
        return "NaN/NaN ME";
    }

    /* IMEnergyItemContainer Interaction */
    public static int extractMEFromHeldStack(EntityPlayer player, int maxExtract, boolean simulate) {
        ItemStack stack = player.getHeldItemMainhand();

        return getCapabilityDefault(stack).extractME(maxExtract, simulate);
    }

    public static int insertMEIntoHeldStack(EntityPlayer player, int maxReceive, boolean simulate) {
        ItemStack stack = player.getHeldItemMainhand();
        return getCapabilityDefault(stack).receiveME(maxReceive, simulate);
    }

    public static IMEnergyCap getFirstUsableBattery(EntityPlayer player) {
        final int LAST = InventoryPlayer.getHotbarSize();

        IMEnergyCap cap;
        for (int i = 0; i < LAST; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if ((cap = stack.getCapability(Capabilities.MENERGY, null)) != null) {
                if (cap.canExtract() && cap.currentME() > 0)
                    return cap;
            }
        }
        return null;
    }

    private static final List<IMEnergyCap> tempList = new SimpleList<>(15);

    public static void batteryTick(EntityPlayer player, int searchBatterySlot, int searchItemSlot) {
        final int LAST = Math.max(searchBatterySlot, searchItemSlot);
        tempList.clear();

        IMEnergyCap capr;
        IMEnergyCap battery = null;

        InventoryPlayer inv = player.inventory;
        for (int i = 0; i < LAST; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.getItem() instanceof MInIntractable) {
                inv.setInventorySlotContents(i, ItemStack.EMPTY);
                continue;
            }
            if ((capr = stack.getCapability(Capabilities.MENERGY, null)) != null) {
                if (i < searchBatterySlot && battery == null && capr.canExtract())
                    battery = capr;
                else if (i < searchItemSlot) {
                    if (!isBattery(capr) && capr.canReceive())
                        tempList.add(capr);
                }
            }
        }
        int len = tempList.size();
        if (len == 0 || battery == null)
            return;

        int maxExtract = Math.min(battery.currentME(), battery.extractSpeed());
        int forEach = maxExtract / len;
        for (IMEnergyCap cap : tempList) {
            if (!battery.canExtract())
                return;
            EnergyHelper.safeTransfer(battery, cap, forEach);
        }
    }

    public static boolean isBattery(IMEnergyCap cap) {
        int me = cap.currentME();
        if (me != 0) return false;
        cap.setME(1);
        if (cap.canExtract()) {
            cap.setME(0);
            return !cap.canExtract();
        }
        return false;
    }

    public static int safeTransfer(IMEnergyCap from, IMEnergyCap to, int count) {
        return from.extractME(to.receiveME(from.extractME(count, true), false), false);
    }

    public static boolean isHoldingEnergyItem(EntityPlayer player) {
        return getCapabilityDefault(player.getHeldItemMainhand()) != EMPTYCAP;
    }

    public static int getEnergyStored(ItemStack stack) {
        IMEnergyCap cap = getCapabilityDefault(stack);
        NBTTagCompound tag = stack.getTagCompound();
        if (cap.currentME() != 0) {
            return cap.currentME();
        } else if (tag != null && tag.hasKey("clientPower", 99)) {
            return tag.getInteger("clientPower");
        } else {
            return 0;
        }
    }

    public static IMEnergyCap getCapabilityDefault(ItemStack stack) {
        IMEnergyCap cap = stack.getCapability(Capabilities.MENERGY, null);
        return stack.isEmpty() ? EMPTYCAP : (cap == null ? EMPTYCAP : cap);
    }
}