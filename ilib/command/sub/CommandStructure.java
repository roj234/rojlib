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

package ilib.command.sub;

import ilib.ImpLib;
import ilib.command.sub.we.ModificationCache;
import ilib.math.Arena;
import ilib.math.Section;
import ilib.misc.SelectionCache;
import ilib.world.structure.ReplacementType;
import ilib.world.structure.StructureAdvanced;
import ilib.world.structure.schematic.Schematic;
import ilib.world.structure.schematic.SchematicLoader;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class CommandStructure extends AbstractSubCommand {

    @Override
    public String getName() {
        return "structure";
    }

    @Override
    public String getHelp() {
        return "command.il.help.structure";
    }

    public static final StructureAdvanced GENERATOR = new StructureAdvanced(null);

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        World w = sender.getEntityWorld();

        switch (args.length) {
            case 2:
            case 3: {
                EntityPlayer player = getAsPlayer(sender);
                boolean flag = args[0].equals("save");
                Arena arena = SelectionCache.get(player.getUniqueID().getMostSignificantBits());
                if (arena == null) {
                    throw new CommandException("command.ilib.sel.arena_not");
                }
                if (arena.isOK()) {
                    if (!flag) {
                        throw new CommandException("command.ilib.sel.unknown_pos");
                    }
                    Section sect = arena.toSection();
                    saveStructure(w, args[1], sect.minBlock(), sect.maxBlock());
                } else {
                    if (flag) {
                        throw new CommandException("command.ilib.sel.arena_not");
                    }
                    BlockPos pos0 = arena.getP1() == null ? arena.getP2() : arena.getP1();
                    File file = new File("./" + args[1].replace("/", "_").replace("\\", "_"));
                    if (!file.exists()) {
                        throw new CommandException("command.ilib.404");
                    }
                    GENERATOR.setSchematic(SchematicLoader.INSTANCE.loadSchematic(file));

                    ReplacementType type = args.length < 3 ? ReplacementType.replace : ReplacementType.valueOf(args[2]);

                    Schematic s = GENERATOR.getSchematic();

                    ModificationCache.beforeOperate(w, pos0, pos0.add(s.width(), s.height(), s.length()), () -> {
                        GENERATOR.generate(w, pos0, type, true);
                        return true;
                    });
                }
            }
            break;
            case 4:
            case 5: {
                BlockPos pos0 = CommandBase.parseBlockPos(sender, args, 1, false);
                File file = new File("./" + args[0].replace("/", "_").replace("\\", "_"));
                if (!file.exists()) {
                    throw new CommandException("command.ilib.404");
                }
                GENERATOR.setSchematic(SchematicLoader.INSTANCE.loadSchematic(file));

                ReplacementType type = args.length < 5 ? ReplacementType.replace : ReplacementType.valueOf(args[4]);

                Schematic s = GENERATOR.getSchematic();

                ModificationCache.beforeOperate(w, pos0, pos0.add(s.width(), s.height(), s.length()), () -> {
                    GENERATOR.generate(w, pos0, type, true);
                    return true;
                });
            }
            break;
            case 7: {
                BlockPos pos0 = CommandBase.parseBlockPos(sender, args, 1, false);
                BlockPos pos1 = CommandBase.parseBlockPos(sender, args, 4, false);
                saveStructure(w, args[0], pos0, pos1);
            }
            break;
            default:
                throw new WrongUsageException("/il structure <name> <sx sy sz> [ex ey ez] [mode] or /il structure <save/load> <name> [mode]");
        }

        notifyCommandListener(sender, "command.ilib.ok");
    }

    private static void saveStructure(World w, String fileName, BlockPos pos0, BlockPos pos1) throws CommandException {
        BlockPos startPoint = new BlockPos(Math.min(pos0.getX(), pos1.getX()), Math.min(pos0.getY(), pos1.getY()), Math.min(pos0.getZ(), pos1.getZ()));
        BlockPos length = new BlockPos(Math.abs(pos1.getX() - pos0.getX()) + 1, Math.abs(pos1.getY() - pos0.getY()) + 1, Math.abs(pos1.getZ() - pos0.getZ()) + 1);
        try {
            CompressedStreamTools.writeCompressed(SchematicLoader.INSTANCE.writeSchematic(w, startPoint, length), new FileOutputStream(new File("./" + fileName.replace("/", "_").replace("\\", "_"))));
        } catch (IOException e) {
            ImpLib.logger().warn("Fatal error occurred during structure saving");
            ImpLib.logger().catching(e);
            throw new CommandException(e.toString());
        }
    }

    @Override
    public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        switch (args.length) {
            case 2:
            case 3:
            case 4:
                return CommandBase.getTabCompletionCoordinate(args, 1, pos);
            case 5:
            case 6:
            case 7:
                return CommandBase.getTabCompletionCoordinate(args, 3, pos);
        }
        return empty_list;
    }
}
