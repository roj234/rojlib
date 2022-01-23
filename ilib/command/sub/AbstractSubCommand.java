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

import com.google.common.collect.ImmutableList;
import ilib.command.MasterCommand;
import roj.util.EmptyArrays;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public abstract class AbstractSubCommand implements ISubCommand {
    private ICommand parent;
    public static final List<String> EMPTY = Collections.emptyList();

    public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        return ImmutableList.of();
    }

    public String getHelp() {
        return "该指令没有提供帮助";
    }

    public AbstractSubCommand setParent(ICommand parent) {
        this.parent = parent;
        return this;
    }

    public ICommand getParent() {
        return this.parent;
    }

    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    public static EntityPlayerMP getAsPlayer(ICommandSender sender) throws CommandException {
        return CommandBase.getCommandSenderAsPlayer(sender);
    }

    public void notifyCommandListener(ICommandSender sender, String text) {
        notifyCommandListener(sender, text, EmptyArrays.OBJECTS);
    }

    public void notifyCommandListener(ICommandSender sender, String text, Object... params) {
        notifyCommandListener(sender, getParent(), 0, text, params);
    }

    public static String makePath(String[] args, int startPos) {
        StringBuilder sb = new StringBuilder();
        for (int i = startPos; i < args.length; i++) {
            sb.append(args[i]);
            sb.append(' ');
        }
        if (args.length - startPos > 1)
            sb.delete(sb.length() - 1, sb.length());
        return sb.toString();
    }

    private static void notifyCommandListener(ICommandSender sender, ICommand command, int type, String text, Object... params) {
        MasterCommand.listener.notifyListener(sender, command, type, text, params);
    }
}
