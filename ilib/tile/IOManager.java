package ilib.tile;

import ilib.fluid.handler.FluidHandler;
import ilib.item.handler.ListInventory;
import ilib.item.handler.SimpleInventory;
import ilib.util.EnumIO;
import roj.collect.IntIterator;
import roj.collect.MyBitSet;
import roj.reflect.DirectAccessor;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/4/15 15:05
 */
public final class IOManager {
	public static final byte ITEM_IN = 0, ITEM_OUT = 1, FLUID_IN = 2, FLUID_OUT = 3;
	public static final int TYPE_ITEM = 0, TYPE_ENERGY = 1, TYPE_FLUID = 2;

	private final TileEntity tile;
	private IItemHandler ih;
	private IFluidHandler fh;

	private final int[] ioMask, ioMode;

	private byte autoIO, autoIOFilter;

	private MyBitSet itemIn, itemOut;
	private ItemProxy itemProxy;
	private int itemIOSpeed;

	private MyBitSet fluidIn, fluidOut;
	private FluidProxy fluidProxy;
	private int fluidIOSpeed;

	public IOManager(TileEntity tile) {
		this(tile, tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null), tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null));
	}

	public IOManager(TileEntity tile, IItemHandler ih, IFluidHandler fh) {
		this.tile = tile;
		this.ioMode = new int[3];
		this.ioMask = new int[] {0x00010001, 0x00010001, 0x00010001};

		itemIOSpeed = 4;
		fluidIOSpeed = 100;

		setItemHandler(ih);
		setFluidHandler(fh);
	}

	// region 设置

	public void setDefault(int type, EnumIO mode) {
		ioMask[type] = (ioMask[type] & 0xFFFF) | (1 << (mode.ordinal() + 16));
	}

	public void addValidMode(int type, EnumIO mode) {
		ioMask[type] |= 1 << mode.ordinal();
	}

	public void clearValidModes(int type) {
		ioMask[type] &= 0xFFFF0000;
	}

	public MyBitSet getItemInputs() {
		return itemIn;
	}

	public MyBitSet getItemOutputs() {
		return itemOut;
	}

	public MyBitSet getFluidInputs() {
		return fluidIn;
	}

	public MyBitSet getFluidOutputs() {
		return fluidOut;
	}

	public int getFluidIOSpeed() {
		return fluidIOSpeed;
	}

	public void setFluidIOSpeed(int fluidIOSpeed) {
		this.fluidIOSpeed = fluidIOSpeed;
	}

	public int getItemIOSpeed() {
		return itemIOSpeed;
	}

	public void setItemIOSpeed(int itemIOSpeed) {
		this.itemIOSpeed = itemIOSpeed;
	}

	public void setItemHandler(IItemHandler inv) {
		this.ih = inv;
		if (inv != null) {
			itemIn = new MyBitSet();
			itemOut = new MyBitSet();
		}
	}

	public void setFluidHandler(IFluidHandler fluids) {
		this.fh = fluids;
		if (fluids instanceof FluidHandler) {
			fluidIn = new MyBitSet();
			fluidOut = new MyBitSet();
		}
	}

	public void setAutoIOFilter(int autoIOFilter) {
		this.autoIOFilter = (byte) autoIOFilter;
		this.autoIO &= autoIOFilter;
	}

	public int getAutoIOFilter() {
		return autoIOFilter;
	}

	// endregion

	public void setAutoIO(int newIO) {
		if (fh == null) newIO &= ~((1 << FLUID_IN) | (1 << FLUID_OUT));
		if (ih == null) newIO &= ~((1 << ITEM_IN) | (1 << ITEM_OUT));
		this.autoIO = (byte) (newIO & autoIOFilter);
	}

	public int getAutoIO() {
		return autoIO & 0xFF;
	}

	public EnumIO getMode(int type, EnumFacing face) {
		return EnumIO.VALUES[(ioMode[type] >>> (face.ordinal() << 2)) & 15];
	}

	public void setMode(int type, EnumFacing face, EnumIO mode) {
		int id = mode.ordinal();
		if (((1 << id) & ioMask[type]) == 0) {
			System.out.println("mode is not allowed: " + type + " " + face + " " + mode);
			return;
		}

		int field = ioMode[type] & ~(15 << (face.ordinal() << 2));
		ioMode[type] = field | (id << (face.ordinal() << 2));
	}

	public void resetMode(int type) {
		if (type == -1) {
			for (int i = 0; i < 3; i++) {
				ioMode[i] = getDefault(i);
			}
		} else {
			ioMode[type] = getDefault(type);
		}
	}

	private int getDefault(int type) {
		int mode = 0;
		int idx = getIdx(ioMask[type] >>> 16);
		for (int j = 0; j < 24; j += 4) {
			mode |= idx << j;
		}
		return mode;
	}

	public void nextMode(int type, EnumFacing face) {
		int mode = ioMode[type];
		int mask = ioMask[type] & 0xFFFF;

		int j = face.ordinal() << 2;
		int ord = (mode >>> j) & 15;

		while (((1 << ++ord) & mask) == 0) {
			if (ord == 15) ord = -1;
		}

		ioMode[type] = (mode & ~(15 << j)) | (ord << j);
	}

	public void checkViolation() {
		for (int i = 0; i < 3; i++) {
			int mode = ioMode[i];
			int mask = ioMask[i];
			int idx = getIdx(mask >>> 16);

			for (int j = 0; j < 24; j += 4) {
				int ord = (mode >>> j) & 15;
				if (((1 << ord) & mask) == 0) {
					mode = (mode & ~(15 << j)) | (idx << j);
				}
			}
			ioMode[i] = mode;
		}
	}

	private static int getIdx(long l) {
		int i = 0;
		while (l != 0) {
			if ((l & 1) != 0) return l == 1 ? i : -1;
			l >>>= 1;
			i++;
		}
		return -1;
	}

	public int getField(int type) {
		return ioMode[type];
	}

	public void setField(int type, int value) {
		ioMode[type] = value;
	}

	public void doAutoIO() {
		if (tile.getWorld().isRemote) return;

		int io = autoIO & 0xF;
		if ((io & (1 << ITEM_OUT)) != 0) {
			int max = itemIOSpeed;
			// 物品自动输出
			for (IntIterator i = itemOut.iterator(); i.hasNext(); ) {
				max -= outputItem(max, i.nextInt()).getCount();
				if (max <= 0) break;
			}
		}

		if ((io & (1 << ITEM_IN)) != 0) {
			// 物品自动输入
			inputItem(itemIOSpeed);
		}

		if ((io & (1 << FLUID_OUT)) != 0) {
			if (fluidOut != null) {
				FluidTank[] tanks = ((FluidHandler) fh).tanks;

				int max = fluidIOSpeed;
				// 流体自动输出
				for (IntIterator i = fluidOut.iterator(); i.hasNext(); ) {
					FluidStack stack = tanks[i.nextInt()].getFluid();
					if (stack != null) {
						int amount = stack.amount;
						if (amount > max) stack.amount = max;

						int drained = drainFluid(stack);
						stack.amount = amount - drained;

						max -= drained;
						if (max <= 0) break;
					}
				}
			} else {
				FluidStack st = fh.drain(fluidIOSpeed, false);
				if (st != null) {
					int drained = drainFluid(st);
					if (drained > 0) fh.drain(drained, true);
				}
			}
		}

		if ((io & (1 << FLUID_IN)) != 0) {
			if (fluidIn != null) {
				FluidTank[] tanks = ((FluidHandler) fh).tanks;

				int max = fluidIOSpeed;
				// 流体自动输入
				for (IntIterator i = fluidIn.iterator(); i.hasNext(); ) {
					FluidTank tank = tanks[i.nextInt()];
					int remain = tank.getCapacity() - tank.getFluidAmount();
					if (remain > 0) max -= fillFluid(remain > max ? max : remain, tank);

					if (max <= 0) break;
				}
			} else {
				fillFluid(fluidIOSpeed, fh);
			}
		}
	}

	// region Sided Handler

	public void clearHandlers() {
		fluidProxy = null;
		itemProxy = null;
	}

	public IItemHandler getItemHandler(EnumFacing face) {
		EnumIO mode = getMode(TYPE_ITEM, face);
		if (mode.canInput() == mode.canOutput()) return mode.canInput() ? ih : new ListInventory(0);

		if (itemProxy == null) itemProxy = new ItemProxy();
		itemProxy.input = mode.canInput();

		return itemProxy;
	}

	public IFluidHandler getFluidHandler(EnumFacing face) {
		if (face == null) return fh;

		EnumIO mode = getMode(TYPE_FLUID, face);
		if (mode.canInput() == mode.canOutput()) return mode.canInput() ? fh : null;

		if (fluidProxy == null) fluidProxy = fh instanceof FluidHandler ? new FluidProxy() : new FHProxy();
		fluidProxy.input = mode.canInput();

		return fluidProxy;
	}

	// endregion
	// region 自动输入/输出

	public int drainFluid(FluidStack stack) {
		if (stack.amount == 0) return 0;
		int begin = stack.amount;

		World w = tile.getWorld();
		BlockPos p = tile.getPos();

		PooledMutableBlockPos pos1 = PooledMutableBlockPos.retain();
		for (EnumFacing face : EnumFacing.VALUES) {
			EnumIO type = getMode(TYPE_FLUID, face);
			if (type != EnumIO.OUTPUT && type != EnumIO.ALL) continue;

			TileEntity t = w.getTileEntity(pos1.setPos(p).move(face));
			if (t != null) {
				IFluidHandler fh = t.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite());
				if (fh != null) {
					stack.amount -= fh.fill(stack, true);
					if (stack.amount == 0) break;
				}
			}
		}
		pos1.release();
		return begin - stack.amount;
	}

	public void fillFluid(List<FluidStack> fluids) {
		IFluidHandler fh = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
		if (fh == null) return;

		int[] prevAmount = new int[fluids.size()];
		for (int i = 0; i < fluids.size(); i++) {
			prevAmount[i] = fluids.get(i).amount;
		}

		World w = tile.getWorld();
		BlockPos p = tile.getPos();

		PooledMutableBlockPos pos1 = PooledMutableBlockPos.retain();
		for (EnumFacing face : EnumFacing.VALUES) {
			EnumIO type = getMode(TYPE_FLUID, face);
			if (type != EnumIO.INPUT && type != EnumIO.ALL) continue;

			TileEntity t = w.getTileEntity(pos1.setPos(p).move(face));
			if (t != null) {
				IFluidHandler src = t.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite());
				if (src != null) {
					for (int i = 0; i < fluids.size(); i++) {
						FluidStack fs = fluids.get(i);
						if (fs.amount > 0) fluids.set(i, src.drain(fs, true));
					}
				}
			}
		}
		pos1.release();

		for (int i = 0; i < fluids.size(); i++) {
			FluidStack fs = fluids.get(i);
			fs.amount = prevAmount[i] - fs.amount;
			fh.fill(fs, true);
		}
	}

	public int fillFluid(int maxFill, IFluidHandler fh) {
		int remain = maxFill;
		World w = tile.getWorld();
		BlockPos p = tile.getPos();

		PooledMutableBlockPos pos1 = PooledMutableBlockPos.retain();
		for (EnumFacing face : EnumFacing.VALUES) {
			EnumIO type = getMode(TYPE_FLUID, face);
			if (type != EnumIO.INPUT && type != EnumIO.ALL) continue;

			TileEntity t = w.getTileEntity(pos1.setPos(p).move(face));
			if (t != null) {
				IFluidHandler src = t.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite());
				if (src != null) {
					FluidStack fs = src.drain(remain, false);
					int filled = fh.fill(fs, true);
					src.drain(filled, true);

					remain -= filled;
					if (remain == 0) break;
				}
			}
		}
		pos1.release();
		return maxFill - remain;
	}


	public ItemStack outputItem(int maxOut, int slot) {
		int count = Math.min(maxOut, ih.getStackInSlot(slot).getCount());
		ItemStack stack = ih.extractItem(slot, count, true);
		stack = outputItem(stack);

		return ih.extractItem(slot, count - stack.getCount(), false);
	}

	public ItemStack outputItem(ItemStack stack) {
		if (stack.isEmpty()) return ItemStack.EMPTY;

		World w = tile.getWorld();
		BlockPos p = tile.getPos();

		PooledMutableBlockPos pos1 = PooledMutableBlockPos.retain();
		for (EnumFacing face : EnumFacing.VALUES) {
			EnumIO type = getMode(TYPE_ITEM, face);
			if (type != EnumIO.OUTPUT && type != EnumIO.ALL) continue;

			TileEntity t = w.getTileEntity(pos1.setPos(p).move(face));
			if (t != null) {
				IItemHandler ih = t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite());
				if (ih != null) {
					for (int i = 0; i < ih.getSlots(); i++) {
						stack = ih.insertItem(i, stack, false);
						if (stack.isEmpty()) break;
					}
				}
			}
		}
		pos1.release();

		return stack;
	}

	public int inputItem(int maxInput) {
		World w = tile.getWorld();
		BlockPos p = tile.getPos();

		PooledMutableBlockPos pos1 = PooledMutableBlockPos.retain();
		for (EnumFacing face : EnumFacing.VALUES) {
			EnumIO type = getMode(TYPE_ITEM, face);
			if (type != EnumIO.INPUT && type != EnumIO.ALL) continue;

			TileEntity t = w.getTileEntity(pos1.setPos(p).move(face));
			if (t != null) {
				IItemHandler ih = t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite());
				IItemHandler iih = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
				if (ih != null && iih != null) {
					for (int i = 0; i < ih.getSlots(); i++) {
						ItemStack stack = ih.extractItem(i, maxInput, true);

						int count = stack.getCount();
						if (!stack.isEmpty()) {
							for (int j = 0; j < iih.getSlots(); j++) {
								stack = iih.insertItem(j, stack, false);
								if (stack.isEmpty()) break;
							}
						}
						count -= stack.getCount();
						ih.extractItem(i, count, false);

						maxInput -= count;
						if (maxInput == 0) break;
					}
				}
			}
		}
		pos1.release();

		return maxInput;
	}

	// endregion

	public void readFromNBT(NBTTagCompound tag) {
		int[] modes = tag.getIntArray("Mode");
		if (modes.length >= 3) {
			System.arraycopy(modes, 0, ioMode, 0, 3);
			checkViolation();
		} else {
			resetMode(-1);
		}
		setAutoIO(tag.getByte("Auto"));
	}

	public NBTTagCompound writeToNBT(NBTTagCompound tag) {
		for (int i = 0; i < 3; i++) {
			if (ioMode[i] != getDefault(i)) {
				tag.setIntArray("Mode", ioMode);
				break;
			}
		}

		if (autoIO != 0) tag.setByte("Auto", autoIO);
		return tag;
	}

	class ItemProxy extends SimpleInventory {
		ItemProxy() {
			super("IOManagerProxy");
		}

		boolean input;

		private int mapToSlot(int i) {
			return (input ? itemIn : itemOut).nthTrue(i);
		}

		@Override
		public void setStackInSlot(int i, @Nonnull ItemStack stack) {
			((IItemHandlerModifiable) ih).setStackInSlot(mapToSlot(i), stack);
		}

		@Override
		protected boolean canExtract(int id, ItemStack stack) {
			return !input;
		}

		@Override
		public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
			return input;
		}

		@Override
		public int getSlots() {
			return (input ? itemIn : itemOut).size();
		}

		@Nonnull
		@Override
		public ItemStack getStackInSlot(int i) {
			return ih.getStackInSlot(mapToSlot(i));
		}
	}

	class FluidProxy extends FluidHandler {
		FluidProxy() {
			if (fh instanceof FluidHandler) tanks = ((FluidHandler) fh).tanks;
		}

		@Override
		protected MyBitSet getInputTanks() {
			return input ? fluidIn : DirectAccessor.EMPTY_BITS;
		}

		@Override
		protected MyBitSet getOutputTanks() {
			return !input ? fluidOut : DirectAccessor.EMPTY_BITS;
		}

		boolean input;
	}

	class FHProxy extends FluidProxy {
		FHProxy() {}

		@Override
		public IFluidTankProperties[] getTankProperties() {
			return fh.getTankProperties();
		}

		@Override
		public int fill(FluidStack stack, boolean b) {
			if (!input) return 0;
			return fh.fill(stack, b);
		}

		@Nullable
		@Override
		public FluidStack drain(FluidStack stack, boolean b) {
			if (input) return null;
			return fh.drain(stack, b);
		}

		@Nullable
		@Override
		public FluidStack drain(int i, boolean b) {
			if (input) return null;
			return fh.drain(i, b);
		}
	}
}
