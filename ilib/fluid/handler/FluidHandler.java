package ilib.fluid.handler;

import roj.collect.IntIterator;
import roj.collect.MyBitSet;

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
		protected MyBitSet inputs, outputs;
		public boolean changed;

		public SimpleImpl(int[] inputs, int[] outputs, FluidTank... tanks) {
			this.tanks = tanks;
			this.inputs = MyBitSet.from(inputs);
			this.outputs = MyBitSet.from(outputs);
		}

		public SimpleImpl(int inputs, int outputs, FluidTank... tanks) {
			this.tanks = tanks;
			this.inputs = MyBitSet.fromRange(0, inputs);
			this.outputs = MyBitSet.fromRange(inputs, inputs + outputs);
		}

		public SimpleImpl(MyBitSet inputs, MyBitSet outputs, FluidTank... tanks) {
			this.tanks = tanks;
			this.inputs = inputs;
			this.outputs = outputs;
		}

		@Override
		public void onTankChanged(FluidTank tank) {
			changed = true;
		}

		@Override
		protected MyBitSet getInputTanks() {
			return inputs;
		}

		@Override
		protected MyBitSet getOutputTanks() {
			return outputs;
		}
	}

	public FluidHandler() {}

	protected abstract MyBitSet getInputTanks();

	protected abstract MyBitSet getOutputTanks();

	/*******************************************************************************************************************
	 * FluidHandler                                                                                                    *
	 *******************************************************************************************************************/

	public void onTankChanged(FluidTank tank) {}

	protected boolean canFill(Fluid fluid) {
		for (IntIterator i = getInputTanks().iterator(); i.hasNext(); ) {
			FluidStack fluid1 = tanks[i.nextInt()].getFluid();
			if (fluid1 == null || fluid1.getFluid() == null || fluid1.getFluid() == fluid) return true;
		}
		return false;
	}

	protected boolean canDrain(Fluid fluid) {
		for (IntIterator i = getOutputTanks().iterator(); i.hasNext(); ) {
			FluidStack fluid1 = tanks[i.nextInt()].getFluid();
			if (fluid1 != null && (fluid == null || fluid1.getFluid() == fluid)) return true;
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
			for (IntIterator i = getInputTanks().iterator(); i.hasNext(); ) {
				FluidTank tank = tanks[i.nextInt()];
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
		for (IntIterator i = getOutputTanks().iterator(); i.hasNext(); ) {
			FluidTank tank = tanks[i.nextInt()];
			FluidStack stack = tank.drain(amount, false);
			if (stack != null) {
				if (!doDrain) return stack;

				tank.drain(amount, true);
				onTankChanged(tank);
				return stack;
			}
		}
		return null;
	}

	@Nullable
	@Override
	public FluidStack drain(FluidStack stack, boolean doDrain) {
		for (IntIterator i = getOutputTanks().iterator(); i.hasNext(); ) {
			FluidTank tank = tanks[i.nextInt()];

			FluidStack in = tank.getFluid();
			if (in == null || in.getFluid() != stack.getFluid()) continue;
			in = tank.drain(stack.amount, false);

			if (in != null) {
				if (!doDrain) return in;
				tank.drain(stack.amount, true);
				onTankChanged(tank);
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
