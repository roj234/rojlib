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

package ilib.item;

import ilib.ImpLib;
import ilib.api.item.IShiftTooltip;
import ilib.api.tile.ToolTarget;
import ilib.api.tile.ToolTarget.Type;
import ilib.util.Colors;
import ilib.util.MCTexts;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Deprecated
public class ItemTool extends ItemRightClickBlock implements IShiftTooltip {
    private final Type type;

    public static final ItemTool WRENCH = new ItemTool(Type.WRENCH),
            CUTTER = new ItemTool(Type.CUTTER);

    ItemTool(Type type) {
        super();
        setNoRepair();
        this.type = type;
        setMaxDamage(type.MAX_DAMAGE);
    }

    @Override
    protected ItemStack onRightClick(World world, BlockPos targetPos, BlockPos clickPos, EntityPlayer player, ItemStack stack, EnumFacing sideHit) {
        if (world.isRemote) return stack;
        TileEntity te = world.getTileEntity(clickPos);

        stack = player.capabilities.isCreativeMode ? stack : duraShrink1(stack);

        if (te instanceof ToolTarget) {
            ToolTarget tile = (ToolTarget) te;

            int bin = tile.canUse(type.getIndex(), player.isSneaking());
            if (bin < 0) {
                return null;
            }

            switch (bin) {
                case ToolTarget.TOGGLE:
                    tile.toggleByTool(type.getIndex(), sideHit);
                    break;
                case ToolTarget.TURN:
                    boolean flag = world.getBlockState(clickPos).getBlock().rotateBlock(world, clickPos, sideHit);
                    return flag ? stack : null;
                case ToolTarget.DESTROY:
                    tile.destroyByTool(type.getIndex());
                    break;
                case ToolTarget.SPECIAL:
                    break;
                default:
                    ImpLib.logger().warn("ToolTarget: undefined type: " + bin);
            }
            return stack;
        } else if (!player.isSneaking() && type.getIndex() == 0) {
            boolean flag = world.getBlockState(clickPos).getBlock().rotateBlock(world, clickPos, sideHit);

            return flag ? stack : null;
        }
        return null;
    }

    private static ItemStack duraShrink1(ItemStack stack) {
        int dura = stack.getItemDamage();
        if (dura < stack.getMaxDamage()) {
            stack.setItemDamage(++dura);
            return stack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void addTooltip(ItemStack stack, List<String> tooltip) {
        tooltip.add(Colors.DARK_BLUE.toString() + MCTexts.format("tooltip.mi.tool." + type.getName()));
    }
}