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

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class FluidHandler implements IFluidHandler, ICapabilityProvider {

    public static final int[] E = new int[0];
    // NBT Tags
    protected static final String SIZE_NBT_TAG = "Size";
    protected static final String TANK_ID_NBT_TAG = "TankID";
    protected static final String TANKS_NBT_TAG = "Tanks";

    // Tanks
    public FluidTank[] tanks;

    public static class StdFluidHandler extends FluidHandler {
        protected int[] inputs;
        protected int[] outputs;

        public StdFluidHandler(FluidTank... tanks) {
            this.tanks = tanks;
            this.inputs = this.outputs = E;
        }

        public StdFluidHandler(int[] inputs, int[] outputs, FluidTank... tanks) {
            this.tanks = tanks;
            this.inputs = inputs;
            this.outputs = outputs;
        }

        @Override
        protected void setupTanks() {
        }

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

    /**
     * Used to set up the tanks needed. You can insert any number of tanks
     */
    protected abstract void setupTanks();

    /**
     * Which tanks can input
     *
     * @return An array with the indexes of the input tanks
     */
    protected abstract int[] getInputTanks();

    /**
     * Which tanks can output
     *
     * @return An array with the indexes of the output tanks
     */
    protected abstract int[] getOutputTanks();

    /*******************************************************************************************************************
     * FluidHandler                                                                                                    *
     *******************************************************************************************************************/

    /**
     * Called when something happens to the tank, you should mark the block for update here if a tile
     */
    public void onTankChanged(FluidTank tank, boolean superUpdate) {
        //markForUpdate(3);
    }

    /**
     * Used to convert a number of buckets into MB
     *
     * @param buckets How many buckets
     * @return The amount of buckets in MB
     */
    public int bucketsToMB(int buckets) {
        return Fluid.BUCKET_VOLUME * buckets;
    }

    /**
     * Returns true if the given fluid can be inserted
     * <p>
     * More formally, this should return true if fluid is able to enter
     */
    protected boolean canFill(Fluid fluid) {
        for (Integer x : getInputTanks()) {
            if (x < tanks.length)
                if ((tanks[x].getFluid() == null || tanks[x].getFluid().getFluid() == null) ||
                        (tanks[x].getFluid() != null && tanks[x].getFluid().getFluid() == fluid))
                    return true;
        }
        return false;
    }

    /**
     * Returns true if the given fluid can be extracted
     * <p>
     * More formally, this should return true if fluid is able to leave
     */
    protected boolean canDrain(Fluid fluid) {
        for (Integer x : getOutputTanks()) {
            if (x < tanks.length)
                if (tanks[x].getFluid() != null && tanks[x].getFluid().getFluid() != null)
                    return true;
        }
        return false;
    }

    /*******************************************************************************************************************
     * Tile Methods                                                                                                    *
     *******************************************************************************************************************/

    /**
     * Used to save the object to an NBT tag
     *
     * @param compound The tag to save to
     */
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        int id = 0;
        compound.setInteger(SIZE_NBT_TAG, tanks.length);
        NBTTagList tagList = new NBTTagList();
        for (FluidTank tank : tanks) {
            if (tank != null) {
                NBTTagCompound tankCompound = new NBTTagCompound();
                tankCompound.setByte(TANK_ID_NBT_TAG, (byte) id);
                id += 1;
                tank.writeToNBT(tankCompound);
                tagList.appendTag(tankCompound);
            }
        }
        compound.setTag(TANKS_NBT_TAG, tagList);
        return compound;
    }

    /**
     * Used to read from an NBT tag
     *
     * @param compound The tag to read from
     */
    public void readFromNBT(NBTTagCompound compound) {
        NBTTagList tagList = compound.getTagList(TANKS_NBT_TAG, 10);
        int size = compound.getInteger(SIZE_NBT_TAG);
        if (size != tanks.length && compound.hasKey(SIZE_NBT_TAG)) tanks = new FluidTank[size];
        for (int x = 0; x < tagList.tagCount(); x++) {
            NBTTagCompound tankCompound = tagList.getCompoundTagAt(x);
            byte position = tankCompound.getByte(TANK_ID_NBT_TAG);
            if (position < tanks.length)
                tanks[position].readFromNBT(tankCompound);
        }
    }

    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
            return (T) this;
        else
            return null;
    }

    /*******************************************************************************************************************
     * IFluidHandler                                                                                                   *
     *******************************************************************************************************************/

    /**
     * Returns an array of objects which represent the internal tanks.
     * These objects cannot be used to manipulate the internal tanks.
     *
     * @return Properties for the relevant internal tanks.
     */
    @Override
    public IFluidTankProperties[] getTankProperties() {
        IFluidTankProperties[] properties = new IFluidTankProperties[tanks.length];
        for (int x = 0; x < tanks.length; x++) {
            FluidTank tank = tanks[x];
            properties[x] = new IFluidTankProperties() {
                @Nullable
                @Override
                public FluidStack getContents() {
                    return tank.getFluid();
                }

                @Override
                public int getCapacity() {
                    return tank.getCapacity();
                }

                @Override
                public boolean canFill() {
                    return tank.canFill();
                }

                @Override
                public boolean canDrain() {
                    return tank.canDrain();
                }

                @Override
                public boolean canFillFluidType(FluidStack fluidStack) {
                    return tank.canFillFluidType(fluidStack);
                }

                @Override
                public boolean canDrainFluidType(FluidStack fluidStack) {
                    return tank.canDrainFluidType(fluidStack);
                }
            };
        }
        return properties;
    }

    public boolean canAndFill(int x, FluidStack resource, boolean doFill) {
        if (resource != null && resource.getFluid() != null) {
            if (tanks[x].fill(resource, false) == resource.amount) {
                if (!doFill)
                    return true;
                boolean flag = tanks[x].getFluid() == null || tanks[x].getFluidAmount() == 0;
                tanks[x].fill(resource, true);
                onTankChanged(tanks[x], flag);
                return true;
            }
        }
        return false;
    }

    /**
     * Fills fluid into internal tanks, distribution is left entirely to the IFluidHandler.
     *
     * @param resource FluidStack representing the Fluid and maximum amount of fluid to be filled.
     * @param doFill   If false, fill will only be simulated.
     * @return Amount of resource that was (or would have been, if simulated) filled.
     */
    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource != null && resource.getFluid() != null && canFill(resource.getFluid())) {
            for (Integer x : getInputTanks()) {
                if (x < tanks.length) {
                    int filled = tanks[x].fill(resource, false);
                    if (filled > 0) {
                        if (!doFill)
                            return filled;
                        boolean flag = tanks[x].getFluid() == null || tanks[x].getFluidAmount() == 0;
                        int actual = tanks[x].fill(resource, true);
                        onTankChanged(tanks[x], flag);
                        return actual;
                    }
                }
            }
        }
        return 0;
    }

    public boolean canAndDrain(int x, int amount, boolean doDrain) {
        if (x < tanks.length) {
            FluidStack stack = tanks[x].drain(amount, false);
            if (stack != null && stack.amount == amount) {
                if (!doDrain)
                    return true;
                tanks[x].drain(amount, true);

                onTankChanged(tanks[x], tanks[x].getFluid() == null || tanks[x].getFluidAmount() == 0);
                return true;
            }
        }
        return false;
    }

    /**
     * Drains fluid out of internal tanks, distribution is left entirely to the IFluidHandler.
     * <p/>
     * This method is not Fluid-sensitive.
     *
     * @param maxDrain Maximum amount of fluid to drain.
     * @param doDrain  If false, drain will only be simulated.
     * @return FluidStack representing the Fluid and amount that was (or would have been, if
     * simulated) drained.
     */
    @Nullable
    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        FluidStack fluidStack;
        for (Integer x : getOutputTanks()) {
            if (x < tanks.length) {
                fluidStack = tanks[x].drain(maxDrain, false);
                if (fluidStack != null) {
                    if (!doDrain)
                        return fluidStack;
                    tanks[x].drain(maxDrain, true);
                    onTankChanged(tanks[x], tanks[x].getFluid() == null || tanks[x].getFluidAmount() == 0);
                    return fluidStack;
                }
            }
        }
        return null;
    }

    /**
     * Drains fluid out of internal tanks, distribution is left entirely to the IFluidHandler.
     *
     * @param resource FluidStack representing the Fluid and maximum amount of fluid to be drained.
     * @param doDrain  If false, drain will only be simulated.
     * @return FluidStack representing the Fluid and amount that was (or would have been, if
     * simulated) drained.
     */
    @Nullable
    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        FluidStack fluidStack;
        for (Integer x : getOutputTanks()) {
            if (x < tanks.length) {
                fluidStack = tanks[x].getFluid();
                if (fluidStack == null || fluidStack.getFluid() != resource.getFluid()) continue;
                fluidStack = tanks[x].drain(resource.amount, false);

                if (fluidStack != null) {
                    if (!doDrain)
                        return fluidStack;
                    tanks[x].drain(resource.amount, true);
                    onTankChanged(tanks[x], tanks[x].getFluid() == null || tanks[x].getFluidAmount() == 0);
                    return fluidStack;
                }
            }
        }
        return null;
    }
}
