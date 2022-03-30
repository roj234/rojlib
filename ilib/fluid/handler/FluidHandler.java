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
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: FluidHandler.java
 */
package ilib.fluid.handler;

import roj.util.EmptyArrays;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankPropertiesWrapper;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class FluidHandler implements IFluidHandler, ICapabilityProvider {
    public FluidTank[] tanks;

    public static class SimpleImpl extends FluidHandler {
        protected int[] inputs;
        protected int[] outputs;

        public SimpleImpl(FluidTank... tanks) {
            this.tanks = tanks;
            this.inputs = this.outputs = EmptyArrays.INTS;
        }

        public SimpleImpl(int[] inputs, int[] outputs, FluidTank... tanks) {
            this.tanks = tanks;
            this.inputs = inputs;
            this.outputs = outputs;
        }

        @Override
        protected void setupTanks() {}

        @Override
        protected int[] getInputTanks() {
            return inputs;
        }

        @Override
        protected int[] getOutputTanks() {
            return outputs;
        }
    }

    /**
     * Default constructor, calls the setupTanks method to setup the tanks
     */
    public FluidHandler() {
        setupTanks();
    }


    /*******************************************************************************************************************
     * Abstract Methods                                                                                                *
     *******************************************************************************************************************/

    protected abstract void setupTanks();

    protected abstract int[] getInputTanks();

    protected abstract int[] getOutputTanks();

    /*******************************************************************************************************************
     * FluidHandler                                                                                                    *
     *******************************************************************************************************************/

    public void onTankChanged(FluidTank tank) {}

    protected boolean canFill(Fluid fluid) {
        for (int id : getInputTanks()) {
            if (id < tanks.length) {
                FluidStack fluid1 = tanks[id].getFluid();
                if (fluid1 == null || fluid1.getFluid() == null || fluid1.getFluid() == fluid)
                    return true;
            }
        }
        return false;
    }

    protected boolean canDrain(Fluid fluid) {
        for (int id : getOutputTanks()) {
            FluidStack fluid1 = tanks[id].getFluid();
            if (fluid1 != null && (fluid == null || fluid1.getFluid() == fluid))
                return true;
        }
        return false;
    }

    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (FluidTank tank : tanks) {
            if (tank != null && tank.getFluid() != null) {
                NBTTagCompound tag1 = new NBTTagCompound();
                tank.writeToNBT(tag1);
                list.appendTag(tag1);
            } else {
                list.appendTag(new NBTTagCompound());
            }
        }
        tag.setTag("Tanks", list);
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        NBTTagList list = tag.getTagList("Tanks", 10);
        int x = Math.min(tanks.length, list.tagCount());

        for (int i = 0; i < x; i++) {
            NBTTagCompound tag1 = list.getCompoundTagAt(i);
            if (tag1.isEmpty()) {
                tanks[i].setFluid(null);
            } else {
                tanks[i].readFromNBT(tag1);
            }
        }
    }

    public boolean hasCapability(@Nonnull Capability<?> c, EnumFacing facing) {
        return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Capability<T> c, EnumFacing facing) {
        return c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ? (T) this : null;
    }

    /**
     * Returns an array of objects which represent the internal tanks.
     * These objects cannot be used to manipulate the internal tanks.
     *
     * @return Properties for the relevant internal tanks.
     */
    @Override
    public IFluidTankProperties[] getTankProperties() {
        IFluidTankProperties[] prop = new IFluidTankProperties[tanks.length];
        for (int i = 0; i < tanks.length; i++) {
            prop[i] = new FluidTankPropertiesWrapper(tanks[i]);
        }
        return prop;
    }

    @Override
    public int fill(FluidStack stack, boolean doFill) {
        if (stack != null && stack.getFluid() != null) {
            for (int id : getInputTanks()) {
                FluidTank tank = tanks[id];
                int filled = tank.fill(stack, false);
                if (filled > 0) {
                    if (!doFill) return filled;

                    int actual = tank.fill(stack, true);
                    onTankChanged(tank);
                    return actual;
                }
            }
        }
        return 0;
    }

    public boolean fillAtomically(int x, FluidStack stack, boolean doFill) {
        if (stack != null && stack.getFluid() != null) {
            FluidTank tank = tanks[x];
            if (tank.fill(stack, false) == stack.amount) {
                if (!doFill) return true;
                tank.fill(stack, true);
                onTankChanged(tank);
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public FluidStack drain(int amount, boolean doDrain) {
        for (int id : getOutputTanks()) {
            FluidStack stack = tanks[id].drain(amount, false);
            if (stack != null) {
                if (!doDrain) return stack;
                tanks[id].drain(amount, true);
                onTankChanged(tanks[id]);
                return stack;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public FluidStack drain(FluidStack stack, boolean doDrain) {
        for (int id : getOutputTanks()) {
            FluidStack in = tanks[id].getFluid();
            if (in == null || in.getFluid() != stack.getFluid()) continue;
            in = tanks[id].drain(stack.amount, false);

            if (in != null) {
                if (!doDrain) return in;
                tanks[id].drain(stack.amount, true);
                onTankChanged(tanks[id]);
                return in;
            }
        }
        return null;
    }

    public boolean drainAtomically(int id, int amount, boolean doDrain) {
        FluidTank tank = tanks[id];
        FluidStack stack = tank.drain(amount, false);
        if (stack != null && stack.amount == amount) {
            if (!doDrain) return true;
            tank.drain(amount, true);
            onTankChanged(tank);
            return true;
        }
        return false;
    }
}
