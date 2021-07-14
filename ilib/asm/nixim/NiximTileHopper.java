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
package ilib.asm.nixim;

import net.minecraft.tileentity.TileEntityHopper;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/1/29 16:57
 */
abstract class NiximTileHopper extends TileEntityHopper {
       /* private NonNullList<ItemStack> inventory;
        private int transferCooldown;
        private long tickedGameTime;

        private int staticCd;

        public void readFromNBT(NBTTagCompound compound) {
            super.readFromNBT(compound);
            if(compound.hasKey("ipt", 99)) {
                float ipt = compound.getFloat("ipt");
                if(ipt > 0 && ipt <= 1) {
                    staticCd = (int) (1 / ipt);
                }
            }
        }

        //@RemapTo()
        public NBTTagCompound writeToNBT(NBTTagCompound compound) {
            super.writeToNBT(compound);
            compound.setFloat("ipt", 1f / staticCd);

            return compound;
        }

        protected boolean updateHopper() {
            if (this.world != null && !this.world.isRemote) {
                if (!this.isOnTransferCooldown() && BlockHopper.isEnabled(this.getBlockMetadata())) {
                    boolean flag = false;
                    if (!this.isInventoryEmpty()) {
                        flag = this.transferItemsOut();
                    }

                    if (!this.isFull()) {
                        flag = pullItems(this) || flag;
                    }

                    if (flag) {
                        this.setTransferCooldown(8);
                        this.markDirty();
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isFull() {
            Iterator var1 = this.inventory.iterator();

            ItemStack itemstack;
            do {
                if (!var1.hasNext()) {
                    return true;
                }

                itemstack = (ItemStack)var1.next();
            } while(!itemstack.isEmpty() && itemstack.getCount() == itemstack.getMaxStackSize());

            return false;
        }

        private boolean transferItemsOut() {
            if (VanillaInventoryCodeHooks.insertHook(this)) {
                return true;
            } else {
                IInventory iinventory = this.getInventoryForHopperTransfer();
                if (iinventory == null) {
                    return false;
                } else {
                    EnumFacing enumfacing = BlockHopper.getFacing(this.getBlockMetadata()).getOpposite();
                    if (this.isInventoryFull(iinventory, enumfacing)) {
                        return false;
                    } else {
                        for(int i = 0; i < this.getSizeInventory(); ++i) {
                            if (!this.getStackInSlot(i).isEmpty()) {
                                ItemStack itemstack = this.getStackInSlot(i).copy();
                                ItemStack itemstack1 = putStackInInventoryAllSlots(this, iinventory, this.decrStackSize(i, 1), enumfacing);
                                if (itemstack1.isEmpty()) {
                                    iinventory.markDirty();
                                    return true;
                                }

                                this.setInventorySlotContents(i, itemstack);
                            }
                        }

                        return false;
                    }
                }
            }
        }

        private boolean isInventoryFull(IInventory inventoryIn, EnumFacing side) {
            if (inventoryIn instanceof ISidedInventory) {
                ISidedInventory isidedinventory = (ISidedInventory)inventoryIn;
                int[] aint = isidedinventory.getSlotsForFace(side);
                int[] var12 = aint;
                int var6 = aint.length;

                for(int var7 = 0; var7 < var6; ++var7) {
                    int k = var12[var7];
                    ItemStack itemstack1 = isidedinventory.getStackInSlot(k);
                    if (itemstack1.isEmpty() || itemstack1.getCount() != itemstack1.getMaxStackSize()) {
                        return false;
                    }
                }
            } else {
                int i = inventoryIn.getSizeInventory();

                for(int j = 0; j < i; ++j) {
                    ItemStack itemstack = inventoryIn.getStackInSlot(j);
                    if (itemstack.isEmpty() || itemstack.getCount() != itemstack.getMaxStackSize()) {
                        return false;
                    }
                }
            }

            return true;
        }

        private static boolean isInventoryEmpty(IInventory inventoryIn, EnumFacing side) {
            if (inventoryIn instanceof ISidedInventory) {
                ISidedInventory isidedinventory = (ISidedInventory)inventoryIn;
                int[] aint = isidedinventory.getSlotsForFace(side);
                int[] var4 = aint;
                int var5 = aint.length;

                for(int var6 = 0; var6 < var5; ++var6) {
                    int i = var4[var6];
                    if (!isidedinventory.getStackInSlot(i).isEmpty()) {
                        return false;
                    }
                }
            } else {
                int j = inventoryIn.getSizeInventory();

                for(int k = 0; k < j; ++k) {
                    if (!inventoryIn.getStackInSlot(k).isEmpty()) {
                        return false;
                    }
                }
            }

            return true;
        }

        public static boolean pullItems(IHopper hopper) {
            Boolean ret = VanillaInventoryCodeHooks.extractHook(hopper);
            if (ret != null) {
                return ret;
            } else {
                IInventory iinventory = getSourceInventory(hopper);
                if (iinventory != null) {
                    EnumFacing enumfacing = EnumFacing.DOWN;
                    if (isInventoryEmpty(iinventory, enumfacing)) {
                        return false;
                    }

                    if (iinventory instanceof ISidedInventory) {
                        ISidedInventory isidedinventory = (ISidedInventory)iinventory;
                        int[] aint = isidedinventory.getSlotsForFace(enumfacing);
                        int[] var6 = aint;
                        int var7 = aint.length;

                        for(int var8 = 0; var8 < var7; ++var8) {
                            int i = var6[var8];
                            if (pullItemFromSlot(hopper, iinventory, i, enumfacing)) {
                                return true;
                            }
                        }
                    } else {
                        int j = iinventory.getSizeInventory();

                        for(int k = 0; k < j; ++k) {
                            if (pullItemFromSlot(hopper, iinventory, k, enumfacing)) {
                                return true;
                            }
                        }
                    }
                } else {
                    Iterator var10 = getCaptureItems(hopper.getWorld(), hopper.getXPos(), hopper.getYPos(), hopper.getZPos()).iterator();

                    while(var10.hasNext()) {
                        EntityItem entityitem = (EntityItem)var10.next();
                        if (putDropInInventoryAllSlots((IInventory)null, hopper, entityitem)) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        private static boolean pullItemFromSlot(IHopper hopper, IInventory inventoryIn, int index, EnumFacing direction) {
            ItemStack itemstack = inventoryIn.getStackInSlot(index);
            if (!itemstack.isEmpty() && canExtractItemFromSlot(inventoryIn, itemstack, index, direction)) {
                ItemStack itemstack1 = itemstack.copy();
                ItemStack itemstack2 = putStackInInventoryAllSlots(inventoryIn, hopper, inventoryIn.decrStackSize(index, 1), (EnumFacing)null);
                if (itemstack2.isEmpty()) {
                    inventoryIn.markDirty();
                    return true;
                }

                inventoryIn.setInventorySlotContents(index, itemstack1);
            }

            return false;
        }

        public static boolean putDropInInventoryAllSlots(IInventory source, IInventory destination, EntityItem entity) {
            boolean flag = false;
            if (entity == null) {
                return false;
            } else {
                ItemStack itemstack = entity.getItem().copy();
                ItemStack itemstack1 = putStackInInventoryAllSlots(source, destination, itemstack, (EnumFacing)null);
                if (itemstack1.isEmpty()) {
                    flag = true;
                    entity.setDead();
                } else {
                    entity.setItem(itemstack1);
                }

                return flag;
            }
        }

        protected IItemHandler createUnSidedHandler() {
            return new VanillaHopperItemHandler(this);
        }

        public static ItemStack putStackInInventoryAllSlots(IInventory source, IInventory destination, ItemStack stack, @Nullable EnumFacing direction) {
            if (destination instanceof ISidedInventory && direction != null) {
                ISidedInventory isidedinventory = (ISidedInventory)destination;
                int[] aint = isidedinventory.getSlotsForFace(direction);

                for(int k = 0; k < aint.length && !stack.isEmpty(); ++k) {
                    stack = insertStack(source, destination, stack, aint[k], direction);
                }
            } else {
                int i = destination.getSizeInventory();

                for(int j = 0; j < i && !stack.isEmpty(); ++j) {
                    stack = insertStack(source, destination, stack, j, direction);
                }
            }

            return stack;
        }

        private static boolean canInsertItemInSlot(IInventory inventoryIn, ItemStack stack, int index, EnumFacing side) {
            if (!inventoryIn.isItemValidForSlot(index, stack)) {
                return false;
            } else {
                return !(inventoryIn instanceof ISidedInventory) || ((ISidedInventory)inventoryIn).canInsertItem(index, stack, side);
            }
        }

        private static boolean canExtractItemFromSlot(IInventory inventoryIn, ItemStack stack, int index, EnumFacing side) {
            return !(inventoryIn instanceof ISidedInventory) || ((ISidedInventory)inventoryIn).canExtractItem(index, stack, side);
        }

        private static ItemStack insertStack(IInventory source, IInventory destination, ItemStack stack, int index, EnumFacing direction) {
            ItemStack itemstack = destination.getStackInSlot(index);
            if (canInsertItemInSlot(destination, stack, index, direction)) {
                boolean flag = false;
                boolean flag1 = destination.isEmpty();
                if (itemstack.isEmpty()) {
                    destination.setInventorySlotContents(index, stack);
                    stack = ItemStack.EMPTY;
                    flag = true;
                } else if (canCombine(itemstack, stack)) {
                    int i = stack.getMaxStackSize() - itemstack.getCount();
                    int j = Math.min(stack.getCount(), i);
                    stack.shrink(j);
                    itemstack.grow(j);
                    flag = j > 0;
                }

                if (flag) {
                    if (flag1 && destination instanceof net.minecraft.tileentity.TileEntityHopper) {
                        net.minecraft.tileentity.TileEntityHopper tileentityhopper1 = (net.minecraft.tileentity.TileEntityHopper)destination;
                        if (!tileentityhopper1.mayTransfer()) {
                            int k = 0;
                            if (source != null && source instanceof net.minecraft.tileentity.TileEntityHopper) {
                                net.minecraft.tileentity.TileEntityHopper tileentityhopper = (net.minecraft.tileentity.TileEntityHopper)source;
                                if (tileentityhopper1.tickedGameTime >= tileentityhopper.tickedGameTime) {
                                    k = 1;
                                }
                            }

                            tileentityhopper1.setTransferCooldown(8 - k);
                        }
                    }

                    destination.markDirty();
                }
            }

            return stack;
        }

        private IInventory getInventoryForHopperTransfer() {
            EnumFacing enumfacing = BlockHopper.getFacing(this.getBlockMetadata());
            return getInventoryAtPosition(this.getWorld(), this.getXPos() + (double)enumfacing.getXOffset(), this.getYPos() + (double)enumfacing.getYOffset(), this.getZPos() + (double)enumfacing.getZOffset());
        }

        public static IInventory getSourceInventory(IHopper hopper) {
            return getInventoryAtPosition(hopper.getWorld(), hopper.getXPos(), hopper.getYPos() + 1.0D, hopper.getZPos());
        }

        public static List<EntityItem> getCaptureItems(World worldIn, double p_184292_1_, double p_184292_3_, double p_184292_5_) {
            return worldIn.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(p_184292_1_ - 0.5D, p_184292_3_, p_184292_5_ - 0.5D, p_184292_1_ + 0.5D, p_184292_3_ + 1.5D, p_184292_5_ + 0.5D), EntitySelectors.IS_ALIVE);
        }

        public static IInventory getInventoryAtPosition(World worldIn, double x, double y, double z) {
            IInventory iinventory = null;
            int i = MathHelper.floor(x);
            int j = MathHelper.floor(y);
            int k = MathHelper.floor(z);
            BlockPos blockpos = new BlockPos(i, j, k);
            IBlockState state = worldIn.getBlockState(blockpos);
            Block block = state.getBlock();
            if (block.hasTileEntity(state)) {
                TileEntity tileentity = worldIn.getTileEntity(blockpos);
                if (tileentity instanceof IInventory) {
                    iinventory = (IInventory)tileentity;
                    if (iinventory instanceof TileEntityChest && block instanceof BlockChest) {
                        iinventory = ((BlockChest)block).getContainer(worldIn, blockpos, true);
                    }
                }
            }

            if (iinventory == null) {
                List<Entity> list = worldIn.getEntitiesInAABBexcluding((Entity)null, new AxisAlignedBB(x - 0.5D, y - 0.5D, z - 0.5D, x + 0.5D, y + 0.5D, z + 0.5D), EntitySelectors.HAS_INVENTORY);
                if (!list.isEmpty()) {
                    iinventory = (IInventory)list.get(worldIn.rand.nextInt(list.size()));
                }
            }

            return (IInventory)iinventory;
        }

        private static boolean canCombine(ItemStack stack1, ItemStack stack2) {
            if (stack1.getItem() != stack2.getItem()) {
                return false;
            } else if (stack1.getMetadata() != stack2.getMetadata()) {
                return false;
            } else {
                return stack1.getCount() > stack1.getMaxStackSize() ? false : ItemStack.areItemStackTagsEqual(stack1, stack2);
            }
        }*/

}
