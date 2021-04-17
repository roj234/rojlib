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
import ilib.math.Arena;
import ilib.math.Section;
import ilib.misc.SelectionCache;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
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
public final class CommandSet extends AbstractSubCommand {
    @Override
    public String getName() {
        return "set";
    }


    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        IBlockState state;
        if (args.length < 1) {
            throw new WrongUsageException("commands.fill.usage");
        }

        // <block> <meta> keep <tile>
        // <block> <meta> destroy <tile>
        // <block> <meta> replace <to> <state> <tile>

        EntityPlayer player = getAsPlayer(sender);

        Block block = CommandBase.getBlockByText(sender, args[0]);
        BlockPos startPoint;
        BlockPos endPoint;

        sender.setCommandStat(CommandResultStats.Type.AFFECTED_BLOCKS, 0);

        Arena arena = SelectionCache.get(player.getUniqueID().getMostSignificantBits());
        if (arena == null) {
            throw new CommandException("command.ilib.sel.arena_not");
        }
        if (arena.isOK()) {
            Section sect = arena.toSection();
            startPoint = sect.minBlock();
            endPoint = sect.maxBlock();
        } else {
            throw new CommandException("command.ilib.sel.arena_not");
        }

        if (args.length >= 2) {
            state = CommandBase.convertArgToBlockState(block, args[1]);
        } else {
            state = block.getDefaultState();
        }

        int i = (endPoint.getX() - startPoint.getX() + 1) * (endPoint.getY() - startPoint.getY() + 1) * (endPoint.getZ() - startPoint.getZ() + 1);

        if (i > 16777215) {
            throw new CommandException("commands.fill.tooManyBlocks", i, 16777215);
        }
        if (startPoint.getY() < 0 || endPoint.getY() >= 256) {
            throw new CommandException("commands.fill.outOfWorld");
        }

        World world = sender.getEntityWorld();

        NBTTagCompound tag = null;

        FillCategory category = FillCategory.REPLACE;
        Predicate<IBlockState> replaceTo = null;
        Block replaceBlock = null;

        if (args.length >= 3) {
            switch (args[2]) {
                case "destroy":
                    category = FillCategory.DESTROY;
                    tag = getNbtTagCompound(args, state, block, 3);
                    break;
                case "keep":
                    category = FillCategory.KEEP;
                    tag = getNbtTagCompound(args, state, block, 3);
                    break;
                case "replace":
                    category = FillCategory.REPLACE;
                    if (args.length >= 4) {
                        replaceBlock = CommandBase.getBlockByText(sender, args[3]);
                        replaceTo = args.length > 4 ? ("*".equals(args[4]) ? Predicates.alwaysTrue() : CommandBase.convertArgToBlockStatePredicate(replaceBlock, args[4])) : Predicates.alwaysTrue();
                    }
                    tag = getNbtTagCompound(args, state, block, 5);
                    break;
            }
        }

        FillCategory finalCategory = category;
        Block finalReplaceBlock = replaceBlock;
        Predicate<IBlockState> finalReplaceTo = replaceTo;
        NBTTagCompound finalTag = tag;

        ModificationCache.beforeOperate(world, startPoint, endPoint, () -> {
            int j = 0;
            for (BlockPos pos : BlockPos.getAllInBoxMutable(startPoint, endPoint)) {
                switch (finalCategory) {
                    case DESTROY:
                        world.destroyBlock(pos, true);
                        break;
                    case KEEP:
                        if (!world.isAirBlock(pos)) {
                            continue;
                        }
                        break;
                    case REPLACE:
                        if (finalReplaceBlock != null) {
                            IBlockState ss = world.getBlockState(pos);
                            if (ss.getBlock() != finalReplaceBlock || !finalReplaceTo.test(ss)) {
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

                if (world.setBlockState(pos, state, 2)) {
                    j++;

                    if (finalTag != null) {
                        TileEntity tile1 = world.getTileEntity(pos);

                        if (tile1 != null) {
                            finalTag.setInteger("x", pos.getX());
                            finalTag.setInteger("y", pos.getY());
                            finalTag.setInteger("z", pos.getZ());
                            tile1.readFromNBT(finalTag);
                        }
                    }
                }
            }

            if (j <= 0) {
                notifyCommandListener(sender, "commands.fill.failed");
                return false;
            }

            sender.setCommandStat(CommandResultStats.Type.AFFECTED_BLOCKS, j);
            notifyCommandListener(sender, "commands.fill.success", j);
            return true;
        });
    }

    public static NBTTagCompound getNbtTagCompound(String[] args, IBlockState state, Block block, int index) throws CommandException {
        if (args.length > index && block.hasTileEntity(state)) {
            NBTTagCompound tag = new NBTTagCompound();

            String s = makePath(args, index);

            try {
                return JsonToNBT.getTagFromJson(s);
            } catch (NBTException e) {
                throw new CommandException("commands.fill.tagError", e.getMessage());
            }
        }
        return null;
    }

    @Override
    public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        switch (args.length) {
            case 1:
                return CommandBase.getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys());
            case 3:
                return CommandBase.getListOfStringsMatchingLastWord(args, "replace", "destroy", "keep");
            case 4:
                return "replace".equals(args[1]) ? CommandBase.getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys()) : empty_list;
        }
        return empty_list;
    }
}
