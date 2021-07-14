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

package ilib.command;

import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import roj.collect.MyHashSet;

import javax.annotation.Nonnull;
import java.util.Set;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/22 14:44
 */
public final class CommandListenerIL extends CommandHandler implements ICommandListener {
    private final CommandHandler serverCmd;
    protected final MinecraftServer server;

    public final ICommandSender NULL_SENDER = new ICommandSender() {
        @Override
        public String getName() {
            return "MI_NULL_SENDER";
        }

        @Override
        public boolean canUseCommand(int permissionLvl, @Nonnull String cmd) {
            return true;
        }

        @Nonnull
        @Override
        public World getEntityWorld() {
            return DimensionManager.getWorld(0);
        }

        public MinecraftServer getServer() {
            return CommandListenerIL.this.getServer();
        }
    };

    private static final Set<String> forbiddenCommands = new MyHashSet<>(32);

    public CommandListenerIL() {
        server = DimensionManager.getWorld(0).getMinecraftServer();
        serverCmd = (CommandHandler) server.commandManager;
        CommandBase.setCommandListener(this);
        MasterCommand.setCommandListener(this);
    }

    public void runCmdWithNull(String cmd) {
        executeCommand(NULL_SENDER, cmd);
    }

    public void runCmd(ICommandSender sender, String cmd) {
        executeCommand(sender, cmd);
    }

    public static void addBannedCmd(String name) {
        forbiddenCommands.add(name);
    }

    @Override
    public int executeCommand(ICommandSender sender, String rawCommand) {
        if (forbiddenCommands.contains(rawCommand)) {
            TextComponentTranslation text = new TextComponentTranslation("commands.generic.permission");
            text.getStyle().setColor(TextFormatting.RED);
            sender.sendMessage(text);
            return 0;
        }
        return serverCmd.executeCommand(sender, rawCommand);
    }

    @Nonnull
    @Override
    protected MinecraftServer getServer() {
        return this.server;
    }

    @Override
    public void notifyListener(ICommandSender iCommandSender, ICommand iCommand, int i, String s, Object... objects) {
        ((ICommandListener)serverCmd).notifyListener(iCommandSender, iCommand, i, s, objects);
    }
}