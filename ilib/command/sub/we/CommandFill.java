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
package ilib.command.sub.we;

import com.google.common.base.Predicates;
import ilib.command.sub.AbstractSubCommand;
import ilib.util.BlockHelper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.*;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class CommandFill extends AbstractSubCommand {
    @Override
    public String getName() {
        return "fill";
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        IBlockState state;
        if (args.length < 7) {
            throw new WrongUsageException("commands.fill.usage");
        }

        Block block = CommandBase.getBlockByText(sender, args[6]);
        BlockPos str;
        BlockPos end;

        sender.setCommandStat(CommandResultStats.Type.AFFECTED_BLOCKS, 0);

        {
            BlockPos pos0 = CommandBase.parseBlockPos(sender, args, 0, false);
            BlockPos pos1 = CommandBase.parseBlockPos(sender, args, 3, false);

            if (args.length >= 8) {
                state = CommandBase.convertArgToBlockState(block, args[7]);
            } else {
                state = block.getDefaultState();
            }

            str = new BlockPos(Math.min(pos0.getX(), pos1.getX()), Math.min(pos0.getY(), pos1.getY()), Math.min(pos0.getZ(), pos1.getZ()));
            end = new BlockPos(Math.max(pos0.getX(), pos1.getX()), Math.max(pos0.getY(), pos1.getY()), Math.max(pos0.getZ(), pos1.getZ()));
            int i = (end.getX() - str.getX() + 1) * (end.getY() - str.getY() + 1) * (end.getZ() - str.getZ() + 1);

            if (i > 16777215) {
                throw new CommandException("commands.fill.tooManyBlocks", i, 16777215);
            }
            if (str.getY() < 0 || end.getY() >= 256) {
                throw new CommandException("commands.fill.outOfWorld");
            }
        }

        World world = sender.getEntityWorld();

        NBTTagCompound tag = null;

        FillCategory cat = FillCategory.REPLACE;

        Predicate<IBlockState> replaceTo = null;
        Block replaceBlock = null;

        // x1 y1 z1 x2 y2 z2 block meta type block meta tile

        if (args.length >= 9) {
            switch (args[8]) {
                case "destroy":
                    cat = FillCategory.DESTROY;
                    tag = CommandSet.getNbtTagCompound(args, state, block, 9);
                    break;
                case "keep":
                    cat = FillCategory.KEEP;
                    tag = CommandSet.getNbtTagCompound(args, state, block, 9);
                    break;
                case "replace":
                    cat = FillCategory.REPLACE;
                    if (args.length > 9) {
                        replaceBlock = CommandBase.getBlockByText(sender, args[9]);
                        replaceTo = args.length > 10 ? ("*".equals(args[10]) ? Predicates.alwaysTrue() : CommandBase.convertArgToBlockStatePredicate(replaceBlock, args[10])) : Predicates.alwaysTrue();
                    }
                    tag = CommandSet.getNbtTagCompound(args, state, block, 11);
                    break;
            }
        }

        ModificationCache.ModificationInfo info = ModificationCache.beforeOperate(world, str, end);

        int i = 0;
        for (BlockPos pos : BlockPos.getAllInBoxMutable(str, end)) {
            switch (cat) {
                case DESTROY:
                    world.destroyBlock(pos, true);
                    break;
                case KEEP:
                    if (!world.isAirBlock(pos)) {
                        continue;
                    }
                    break;
                case REPLACE:
                    if (replaceBlock != null) {
                        IBlockState ss = world.getBlockState(pos);
                        if (ss.getBlock() != replaceBlock || !replaceTo.test(ss)) {
                            continue;
                        }
                    }
                    break;
            }

            TileEntity tile = world.getTileEntity(pos);

            if (tile instanceof IInventory) {
                ((IInventory) tile).clear();
            }
            world.removeTileEntity(pos);

            if (world.setBlockState(pos, state, BlockHelper.PLACEBLOCK_SENDCHANGE)) {
                i++;

                if (tag != null) {
                    TileEntity tile1 = world.getTileEntity(pos);

                    if (tile1 != null) {
                        tag.setInteger("x", pos.getX());
                        tag.setInteger("y", pos.getY());
                        tag.setInteger("z", pos.getZ());
                        tile1.readFromNBT(tag);
                    }
                }
            }
        }
        BlockHelper.notifyWall(world, str, end);

        if (i <= 0) {
            ModificationCache.cancel(info);
            throw new CommandException("commands.fill.failed");
        }

        sender.setCommandStat(CommandResultStats.Type.AFFECTED_BLOCKS, i);
        notifyCommandListener(sender, "commands.fill.success", i);
    }

    @Override
    public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        switch (args.length) {
            case 1:
            case 2:
            case 3:
                return CommandBase.getTabCompletionCoordinate(args, 0, pos);
            case 4:
            case 5:
            case 6:
                return CommandBase.getTabCompletionCoordinate(args, 3, pos);
            case 7:
                return CommandBase.getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys());
            case 9:
                return CommandBase.getListOfStringsMatchingLastWord(args, "replace", "destroy", "keep");
            case 10:
                return "replace".equals(args[8]) ? CommandBase.getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys()) : empty_list;
        }
        return empty_list;
    }
}
