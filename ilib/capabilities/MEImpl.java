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

import ilib.api.energy.MEItem;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class MEImpl implements MEItem, ICapabilitySerializable<NBTTagCompound> {
    protected int power;
    protected int maxPower;
    protected int receiveSpeed, extractSpeed;
    protected int volRequired;

    protected final ItemStack stack;

    public MEImpl() {
        this.stack = null;
    }

    public MEImpl(ItemStack stack, NBTTagCompound tag) {
        if (tag != null)
            deserializeNBT(tag);
        this.stack = stack;
    }

    public MEImpl setMaxME(int i) {
        maxPower = i;
        return this;
    }

    public MEImpl setReceiveSpeed(int i) {
        receiveSpeed = i;
        return this;
    }

    public MEImpl setExtractSpeed(int i) {
        extractSpeed = i;
        return this;
    }

    public MEImpl setVolRequired(int volRequired) {
        this.volRequired = volRequired;
        return this;
    }

    @Override
    public EnergyType getEnergyType() {
        return EnergyType.STORAGE;
    }

    public int currentME() {
        return power;
    }

    public int maxME() {
        return maxPower;
    }

    public boolean canExtract() {
        return power > 0 && extractSpeed() > 0;
    }

    public boolean canReceive() {
        return power < maxPower && receiveSpeed() > 0;
    }

    public MEImpl setME(int count) {
        power = count;
        sendCapability();
        return this;
    }

    public int extractME(int count, boolean simulate) {
        int rem = power - count;
        if (rem < 0) {
            int p = power;
            if (!simulate) {
                power = 0;
                sendCapability();
            }

            return p;
        } else {
            if (!simulate) {
                power = rem;
                sendCapability();
            }

            return count;
        }
    }

    public int receiveME(int count, boolean simulate) {
        int rem = maxPower - power;
        if (rem < count) {
            if (!simulate) {
                power = maxPower;
                sendCapability();
            }

            return rem;
        }

        if (!simulate) {
            power += count;
            sendCapability();
        }

        return count;
    }

    public int voltage() {
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
        tag.setInteger("maxBlock", maxPower);
        tag.setInteger("rec", receiveSpeed);
        tag.setInteger("ext", extractSpeed);
        tag.setInteger("vol", volRequired);
        return tag;
    }

    public void deserializeNBT(NBTTagCompound tag) {
        power = tag.getInteger("power");
        maxPower = tag.getInteger("maxBlock");
        receiveSpeed = tag.getInteger("rec");
        extractSpeed = tag.getInteger("ext");
        volRequired = tag.getInteger("vol");
    }

    @Deprecated
    protected void sendCapability() {
        if (stack != null) {
            NBTTagCompound tag = this.stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                this.stack.setTagCompound(tag);
            }
            tag.setInteger("clientPower", power);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MEImpl imEnergy = (MEImpl) o;
        return power == imEnergy.power &&
                Objects.equals(stack, imEnergy.stack);
    }
}
