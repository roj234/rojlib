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
 * Filename: SidedFluidHandler.java
 */
package ilib.fluid.handler;

import ilib.api.tile.AdvSide;
import ilib.util.EnumIO;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;

public abstract class SidedFluidHandler implements IFluidHandler {
    public final FluidHandler handler;

    public static class StdSidedFluidHandler<T extends AdvSide & IFluidProvider> extends SidedFluidHandler {
        private final AdvSide tile;
        private final EnumFacing face;
        private final int[] inputs;
        private final int[] outputs;

        public StdSidedFluidHandler(EnumFacing face, T tile, int[] inputs, int[] outputs) {
            super(tile.getFluidHandler());
            this.tile = tile;
            this.face = face;
            this.inputs = inputs;
            this.outputs = outputs;
        }

        @Override
        protected int[] getInputTanks() {
            return inputs;
        }

        @Override
        protected int[] getOutputTanks() {
            return outputs;
        }

        @Override
        protected boolean canFill() {
            EnumIO mode = tile.getSideMode(EnumIO.TYPE_FLUID, face);
            return mode == EnumIO.ALL || mode == EnumIO.INPUT;
        }

        @Override
        protected boolean canDrain() {
            EnumIO mode = tile.getSideMode(EnumIO.TYPE_FLUID, face);
            return mode == EnumIO.ALL || mode == EnumIO.OUTPUT;
        }
    }

    /**
     * Default constructor, calls the setupTanks method to setup the tanks
     */
    public SidedFluidHandler(FluidHandler handler) {
        this.handler = handler;
    }

    /*******************************************************************************************************************
     * Abstract Methods                                                                                                *
     *******************************************************************************************************************/

    protected abstract int[] getInputTanks();

    protected abstract int[] getOutputTanks();

    protected abstract boolean canFill();

    protected abstract boolean canDrain();

    /*******************************************************************************************************************
     * FluidHandler                                                                                                    *
     *******************************************************************************************************************/

    protected boolean canFill(Fluid fluid) {
        return canFill() && handler.canFill(fluid);
    }

    protected boolean canDrain(Fluid fluid) {
        return canDrain() && handler.canDrain(fluid);
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        FluidTank[] tanks = handler.tanks;
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
//                    FluidStack stack = tank.getFluid();
//                    if(stack == null) return SidedFluidHandler.this.canFill();
//                    return SidedFluidHandler.this.canFill(stack.getFluid()) && tank.canFill();
                    return SidedFluidHandler.this.canFill() && tank.canFill();
                }

                @Override
                public boolean canDrain() {
//                    FluidStack stack = tank.getFluid();
//                    if(stack == null) return false;
//                    return SidedFluidHandler.this.canDrain(stack.getFluid()) && tank.canDrain();
                    return SidedFluidHandler.this.canDrain() && tank.canDrain();
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

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (!this.canFill()) return 0;
        return handler.fill(resource, doFill);
    }

    @Nullable
    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (!this.canDrain()) return null;
        return handler.drain(maxDrain, doDrain);
    }

    @Nullable
    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        if (!this.canDrain()) return null;
        return handler.drain(resource, doDrain);
    }
}
