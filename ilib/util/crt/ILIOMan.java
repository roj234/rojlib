package ilib.util.crt;

import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.block.IBlock;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.api.world.IBlockPos;
import crafttweaker.api.world.IWorld;
import crafttweaker.mc1120.block.MCWorldBlock;
import crafttweaker.mc1120.item.MCItemStack;
import crafttweaker.mc1120.liquid.MCLiquidStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenConstructor;
import stanhebben.zenscript.annotations.ZenGetter;
import stanhebben.zenscript.annotations.ZenMethod;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * @author Roj234
 * @since 2022/4/28 18:28
 */
//!!AT [["crafttweaker.mc1120.block.MCWorldBlock", ["blocks", "pos"]]]
@ZenClass("mods.implib.IOManager")
@ZenRegister
public final class ILIOMan {
	private final IFluidHandler fh;
	private final IItemHandler ih;

	@ZenConstructor
	public ILIOMan(IBlock block) {
		if (!(block instanceof MCWorldBlock)) throw new IllegalStateException("Not a world block");
		MCWorldBlock block1 = (MCWorldBlock) block;
		TileEntity tile = block1.blocks.getTileEntity(block1.pos);

		ih = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
		fh = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
	}

	@ZenConstructor
	public ILIOMan(IWorld world, IBlockPos pos) {
		TileEntity tile = ((World) world.getInternal()).getTileEntity(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
		ih = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
		fh = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
	}

	@ZenMethod
	public int insertF(ILiquidStack fluid, boolean simulate) {
		return fh.fill((FluidStack) fluid.getInternal(), !simulate);
	}

	@ZenMethod
	public ILiquidStack extractF(int amount, boolean simulate) {
		return new MCLiquidStack(fh.drain(amount, !simulate));
	}

	@ZenMethod
	public IItemStack insert(IItemStack stack1, boolean simulate) {
		ItemStack stack = (ItemStack) stack1.getInternal();
		for (int i = 0; i < ih.getSlots(); i++) {
			stack = ih.insertItem(i, stack, simulate);
			if (stack.isEmpty()) return MCItemStack.EMPTY;
		}
		return MCItemStack.createNonCopy(stack);
	}

	@ZenMethod
	public IItemStack insert(int slot, IItemStack stack1, boolean simulate) {
		ItemStack stack = (ItemStack) stack1.getInternal();
		stack = ih.insertItem(slot, stack, simulate);
		return stack.isEmpty() ? MCItemStack.EMPTY : MCItemStack.createNonCopy(stack);
	}

	@ZenMethod
	public IItemStack extract(boolean simulate) {
		ItemStack stack;
		for (int i = 0; i < ih.getSlots(); i++) {
			stack = ih.extractItem(i, ih.getStackInSlot(i).getCount(), simulate);
			if (!stack.isEmpty()) return MCItemStack.createNonCopy(stack);
		}
		return MCItemStack.EMPTY;
	}

	@ZenMethod
	public IItemStack extract(int slot, boolean simulate) {
		ItemStack stack = ih.extractItem(slot, ih.getStackInSlot(slot).getCount(), simulate);
		return stack.isEmpty() ? MCItemStack.EMPTY : MCItemStack.createNonCopy(stack);
	}

	@ZenMethod
	public IItemStack extract(int slot, int amount, boolean simulate) {
		ItemStack stack = ih.extractItem(slot, amount, simulate);
		return stack.isEmpty() ? MCItemStack.EMPTY : MCItemStack.createNonCopy(stack);
	}

	@ZenGetter("slots")
	public int getSlotCount() {
		return ih.getSlots();
	}
}
