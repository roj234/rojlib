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
package ilib.fluid;

import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class FluidTankHelper {
    public static FluidTank cloneTank(FluidTank tank) {
        FluidTank newTank = new FluidTank(tank.getCapacity());
        newTank.setFluid(tank.getFluid());
        return newTank;
    }

    public static ItemStack fillOrDrainFluid(FluidTank machine, ItemStack item) {
        IFluidHandlerItem handler = item.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (handler == null) {
            return item;
        }
        return fillOrDrainFluid(machine, handler, machine.getCapacity(), machine.getCapacity(), null);
    }

    public static ItemStack fillOrDrainFluid(FluidTank machine, IFluidHandlerItem handler, int maxFill, int maxDrain, Fluid fluid) {
        FluidStack selfFluidStack = machine.getFluid();

        FluidStack drained;
        if (selfFluidStack == null) {
            if (fluid == null) {
                drained = handler.drain(maxFill, true);
            } else {
                FluidStack stack = new FluidStack(fluid, maxFill);
                drained = handler.drain(stack, true);
            }
            if (drained != null)
                machine.setFluid(drained);
        } else {
            FluidStack toDrain = new FluidStack(selfFluidStack.getFluid(), maxDrain - selfFluidStack.amount);
            drained = handler.drain(toDrain, true);
            if (drained != null)
                machine.fill(drained, true);
            else if (maxDrain != 0) {
                if (maxDrain != -1 && maxDrain < selfFluidStack.amount) {
                    selfFluidStack = selfFluidStack.copy();
                    selfFluidStack.amount = maxDrain;
                    selfFluidStack.amount -= handler.fill(selfFluidStack, true);
                    machine.setFluid(selfFluidStack);
                } else {
                    selfFluidStack.amount -= handler.fill(selfFluidStack, true);
                }
            }
        }
        return handler.getContainer();
    }

    public static ItemStack fillFluid(FluidTank machine, ItemStack item) {
        return fillFluid(machine, item, machine.getCapacity(), null);
    }

    public static ItemStack fillFluid(FluidTank machine, ItemStack item, int maxFill, Fluid fluid) {
        IFluidHandlerItem handler = item.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (handler == null) {
            return item;
        }
        return fillFluid(machine, handler, maxFill, fluid);
    }

    public static ItemStack fillFluid(FluidTank machine, IFluidHandlerItem handler, int maxFill, Fluid fluid) {
        FluidStack selfFluidStack = machine.getFluid();

        FluidStack drained;
        if (selfFluidStack == null) {
            if (fluid == null) {
                drained = handler.drain(maxFill, true);
            } else {
                FluidStack stack = new FluidStack(fluid, maxFill);
                drained = handler.drain(stack, true);
            }
            if (drained != null)
                machine.setFluid(drained);
        } else {
            FluidStack toDrain = new FluidStack(selfFluidStack.getFluid(), Math.max(0, maxFill - selfFluidStack.amount));
            drained = handler.drain(toDrain, true);
            if (drained != null)
                machine.fill(drained, true);
        }
        return handler.getContainer();
    }

    public static ItemStack drainFluid(FluidTank machine, ItemStack item) {
        return drainFluid(machine, item, -1);
    }

    public static ItemStack drainFluid(FluidTank machine, ItemStack item, int maxDrain) {
        IFluidHandlerItem handler = item.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (handler == null) {
            return item;
        }
        return drainFluid(machine, handler, maxDrain);
    }

    public static ItemStack drainFluid(FluidTank machine, IFluidHandlerItem handler, int maxDrain) {
        FluidStack selfFluidStack = machine.getFluid();

        if (selfFluidStack == null) {
            return handler.getContainer();
        } else {
            if (maxDrain != -1 && maxDrain < selfFluidStack.amount) {
                selfFluidStack = selfFluidStack.copy();
                selfFluidStack.amount = maxDrain;
            }
            machine.getFluid().amount -= handler.fill(selfFluidStack, true);
        }
        return handler.getContainer();
    }
}
