package ilib.tile;

import ilib.fluid.handler.FluidHandler;
import ilib.item.handler.SimpleInventory;
import ilib.util.EnumIO;
import roj.collect.IntList;
import roj.util.EmptyArrays;

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
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/4/15 15:05
 */
public final class IOManager {
    public static final int ITEM_IN = 1, ITEM_OUT = 2, FLUID_IN = 4, FLUID_OUT = 8;

    private final TileEntity tile;
    private final IItemHandler ih;
    private final IFluidHandler fh;

    private final int[] ioMask, ioMode;

    public byte autoIO;

    private IntList itemSlots;
    private ItemProxy itemProxy;
    private int inSlotCount;

    private IntList fluidIn, fluidOut;
    private FluidProxy fluidProxy;

    public IOManager(TileEntity tile) {
        this.tile = tile;
        this.ioMode = new int[3];
        this.ioMask = new int[3];

        this.ih = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        this.fh = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
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

    // endregion

    public EnumIO getMode(int type, EnumFacing face) {
        return EnumIO.VALUES[(ioMode[type] >>> (face.ordinal() << 3)) & 15];
    }

    public void setMode(int type, EnumFacing face, EnumIO mode) {
        int id = mode.ordinal();
        if (((1 << id) & ioMask[type]) == 0) return;

        int field = ioMode[type] & ~(15 << (face.ordinal() << 3));
        ioMode[type] = field | (id << (face.ordinal() << 3));
    }

    public void resetMode(int type) {
        if (type == -1) {
            for (int i = 0; i < 3; i++) {
                resetMode(type);
            }
            return;
        }

        int mode = 0;
        int idx = getIdx(ioMask[type] >>> 16);
        for (int j = 0; j < 18; j += 3) {
            mode |= idx << j;
        }
        ioMode[type] = mode;
    }

    public void nextMode(int type, EnumFacing face) {
        int mode = ioMode[type];
        int mask = ioMask[type];

        int j = face.ordinal();
        int ord = (mode >>> j) & 15;
        if (((1 << ++ord) & mask) == 0) {
            ord = 0;
            while (((1 << ord) & mask) == 0) {
                ord++;
            }
        }

        ioMode[type] = (mode & ~(15 << j)) | (ord << j);
    }

    public void checkViolation() {
        for (int i = 0; i < 3; i++) {
            int mode = ioMode[i];
            int mask = ioMask[i];
            int idx = getIdx(mask >>> 16);

            for (int j = 0; j < 18; j += 3) {
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
            if ((l & 1) != 0)
                return l == 1 ? i : -1;
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
        if ((autoIO & ITEM_OUT) != 0) {
            // 物品自动输出
            for (int i = 0; i < itemSlots.size(); i++) {
                int val = itemSlots.get(i);
                if (val < 0) outputItem(-val - 1);
            }
        }

        if ((autoIO & ITEM_IN) != 0) {
            // 物品自动输入
            inputItem(64);
        }

        if ((autoIO & FLUID_OUT) != 0) {
            FluidTank[] tanks = ((FluidHandler) fh).tanks;

            // 流体自动输出
            for (int i = 0; i < fluidOut.size(); i++) {
                FluidStack stack = tanks[fluidOut.get(i)].getFluid();
                if (stack != null) drainFluid(stack);
            }
        }

        if ((autoIO & FLUID_IN) != 0) {
            FluidTank[] tanks = ((FluidHandler) fh).tanks;

            // 流体自动输入
            for (int i = 0; i < fluidIn.size(); i++) {
                FluidTank tank = tanks[fluidIn.get(i)];
                int remain = tank.getCapacity() - tank.getFluidAmount();
                if (remain > 0) fillFluid(remain, tank);
            }
        }

    }

    // region Sided Handler

    public void clearHandlers() {
        fluidProxy = null;
        itemProxy = null;
    }

    public IItemHandler getItemHandler(EnumFacing face) {
        EnumIO mode = getMode(EnumIO.TYPE_ITEM, face);
        if (mode.canInput() == mode.canOutput()) return mode.canInput() ? ih : null;

        if (itemProxy == null) itemProxy = new ItemProxy();
        itemProxy.input = mode.canInput();

        return itemProxy;
    }

    public IFluidHandler getFluidHandler(EnumFacing face) {
        EnumIO mode = getMode(EnumIO.TYPE_FLUID, face);
        if (mode.canInput() == mode.canOutput()) return mode.canInput() ? fh : null;

        if (fluidProxy == null) fluidProxy = new FluidProxy();
        fluidProxy.input = mode.canInput();

        return fluidProxy;
    }

    // endregion
    // region 自动输入/输出

    public int drainFluid(FluidStack stack) {
        if(stack.amount == 0) return 0;

        World w = tile.getWorld();
        BlockPos p = tile.getPos();

        PooledMutableBlockPos pos1 = PooledMutableBlockPos.retain();
        for(EnumFacing face : EnumFacing.VALUES) {
            EnumIO type = getMode(EnumIO.TYPE_FLUID, face);
            if(type != EnumIO.OUTPUT && type != EnumIO.ALL) continue;

            TileEntity t = w.getTileEntity(pos1.setPos(p).move(face));
            if(t != null) {
                IFluidHandler fh = t.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite());
                if(fh != null) {
                    stack.amount -= fh.fill(stack, true);
                    if(stack.amount == 0) break;
                }
            }
        }
        pos1.release();
        return stack.amount;
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
        for(EnumFacing face : EnumFacing.VALUES) {
            EnumIO type = getMode(EnumIO.TYPE_FLUID, face);
            if(type != EnumIO.INPUT && type != EnumIO.ALL) continue;

            TileEntity t = w.getTileEntity(pos1.setPos(p).move(face));
            if(t != null) {
                IFluidHandler src = t.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite());
                if(src != null) {
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

    public void fillFluid(int maxFill, IFluidHandler fh) {
        World w = tile.getWorld();
        BlockPos p = tile.getPos();

        PooledMutableBlockPos pos1 = PooledMutableBlockPos.retain();
        for(EnumFacing face : EnumFacing.VALUES) {
            EnumIO type = getMode(EnumIO.TYPE_FLUID, face);
            if(type != EnumIO.INPUT && type != EnumIO.ALL) continue;

            TileEntity t = w.getTileEntity(pos1.setPos(p).move(face));
            if(t != null) {
                IFluidHandler src = t.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite());
                if(src != null) {
                    FluidStack fs = src.drain(maxFill, false);
                    int filled = fh.fill(fs, true);
                    src.drain(filled, true);

                    maxFill -= filled;
                    if (maxFill == 0) break;
                }
            }
        }
        pos1.release();
    }


    public ItemStack outputItem(int slot) {
        ItemStack stack = ih.getStackInSlot(slot);
        return outputItem(ih.extractItem(slot, stack.getCount(), false));
    }

    public ItemStack outputItem(ItemStack stack) {
        if(stack.isEmpty()) return ItemStack.EMPTY;

        World w = tile.getWorld();
        BlockPos p = tile.getPos();

        PooledMutableBlockPos pos1 = PooledMutableBlockPos.retain();
        for(EnumFacing face : EnumFacing.VALUES) {
            EnumIO type = getMode(EnumIO.TYPE_ITEM, face);
            if(type != EnumIO.OUTPUT && type != EnumIO.ALL) continue;

            TileEntity t = w.getTileEntity(pos1.setPos(p).move(face));
            if(t != null) {
                IItemHandler ih = t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite());
                if(ih != null) {
                    for(int i = 0; i < ih.getSlots(); i++) {
                        stack = ih.insertItem(i, stack, false);
                        if(stack.isEmpty()) break;
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
        for(EnumFacing face : EnumFacing.VALUES) {
            EnumIO type = getMode(EnumIO.TYPE_ITEM, face);
            if(type != EnumIO.INPUT && type != EnumIO.ALL) continue;

            TileEntity t = w.getTileEntity(pos1.setPos(p).move(face));
            if(t != null) {
                IItemHandler ih = t.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite());
                IItemHandler iih = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
                if(ih != null && iih != null) {
                    for(int i = 0; i < ih.getSlots(); i++) {
                        ItemStack stack = ih.extractItem(i, maxInput, true);

                        int count = stack.getCount();
                        if(!stack.isEmpty()) {
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
        }
        autoIO = tag.getByte("Auto");
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setIntArray("Mode", ioMode);
        tag.setByte("Auto", autoIO);
        return tag;
    }

    class ItemProxy extends SimpleInventory {
        public ItemProxy() {
            super("IOManagerProxy");
            inSlotCount = 0;
            for (int j = 0; j < itemSlots.size(); j++) {
                int val = itemSlots.get(j);
                if (val >= 0) inSlotCount++;
            }
        }

        boolean input;

        private int mapToSlot(int i) {
            for (int j = 0; j < itemSlots.size(); j++) {
                int val = itemSlots.get(j);
                if (input) {
                    if (val >= 0) {
                        if (i-- == 0) return val;
                    }
                } else {
                    if (val < 0) {
                        if (i-- == 0) return -val - 1;
                    }
                }
            }
            throw new IndexOutOfBoundsException();
        }

        @Override
        public void setStackInSlot(int i, @Nonnull ItemStack stack) {
            ((IItemHandlerModifiable) ih).setStackInSlot(mapToSlot(i), stack);
        }

        @Override
        public int getSlots() {
            return input ? inSlotCount : itemSlots.size() - inSlotCount;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int i) {
            return ih.getStackInSlot(mapToSlot(i));
        }
    }

    class FluidProxy extends FluidHandler {
        @Override
        protected void setupTanks() {
            tanks = ((FluidHandler) fh).tanks;
            fluidIn.trimToSize();
            fluidOut.trimToSize();
        }

        @Override
        protected int[] getInputTanks() {
            return input ? fluidIn.getRawArray() : EmptyArrays.INTS;
        }

        @Override
        protected int[] getOutputTanks() {
            return !input ? fluidOut.getRawArray() : EmptyArrays.INTS;
        }

        boolean input;
    }
}
