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

package ilib.capabilities;

import ilib.api.energy.IMEnergyCap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;
import java.util.Objects;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class IMEnergyImpl implements IMEnergyCap, ICapabilitySerializable<NBTTagCompound> {
    protected int power;
    protected int maxME;
    protected int receiveSpeed;
    protected int extractSpeed;
    protected int volRequired;

    protected final ItemStack stack;

    public IMEnergyImpl() {
        this.stack = null;
    }

    public IMEnergyImpl(ItemStack stack, NBTTagCompound tag) {
        if (tag != null)
            deserializeNBT(tag);
        this.stack = stack;
    }

    public IMEnergyImpl setMaxME(int i) {
        maxME = i;
        return this;
    }

    public IMEnergyImpl setReceiveSpeed(int i) {
        receiveSpeed = i;
        return this;
    }

    public IMEnergyImpl setExtractSpeed(int i) {
        extractSpeed = i;
        return this;
    }

    public IMEnergyImpl setVolRequired(int volRequired) {
        this.volRequired = volRequired;
        return this;
    }

    public int currentME() {
        return power;
    }

    public int maxME() {
        return maxME;
    }

    public boolean canExtract() {
        return power > 0 && extractSpeed() > 0;
    }

    public boolean canReceive() {
        return power < maxME() && receiveSpeed() > 0;
    }

    public IMEnergyImpl setME(int count) {
        power = count;
        sendCapability();
        return this;
    }

    public int extractME(int count, boolean simulate) {
        if (power < count) {
            int power2 = power;
            if (!simulate) {
                power = 0;
                sendCapability();
            }

            return power2;
        } else {
            if (!simulate) {
                power -= count;
                sendCapability();
            }

            return count;
        }
    }

    public int receiveME(int count, boolean simulate) {
        if ((power + count) > maxME()) {
            int power2 = power;
            if (!simulate) {
                power = maxME();
                sendCapability();
            }

            return maxME() - power2;
        }

        if (!simulate) {
            power += count;
            sendCapability();
        }

        return count;
    }

    public int volRequired() {
        return volRequired;
    }

    public int receiveSpeed() {
        return receiveSpeed;
    }

    public int extractSpeed() {
        return extractSpeed;
    }

    public boolean hasCapability(@Nonnull Capability<?> cap, EnumFacing facing) {
        return cap == Capabilities.MENERGY;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Capability<T> cap, EnumFacing facing) {
        if (cap == Capabilities.MENERGY) {
            return (T) this;
        }
        return null;
    }

    public int hashCode() {
        return power;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("power", power);
        tag.setInteger("maxBlock", maxME);
        tag.setInteger("rec", receiveSpeed);
        tag.setInteger("ext", extractSpeed);
        tag.setInteger("vol", volRequired);
        return tag;
    }

    public void deserializeNBT(NBTTagCompound tag) {
        power = tag.getInteger("power");
        maxME = tag.getInteger("maxBlock");
        receiveSpeed = tag.getInteger("rec");
        extractSpeed = tag.getInteger("ext");
        volRequired = tag.getInteger("vol");
    }

    protected void sendCapability() {
        NBTTagCompound tag = this.stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            this.stack.setTagCompound(tag);
        }
        tag.setInteger("clientPower", power);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IMEnergyImpl imEnergy = (IMEnergyImpl) o;
        return power == imEnergy.power &&
                Objects.equals(stack, imEnergy.stack);
    }
}
