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

package ilib.command.parser;

import ilib.command.sub.ISubCommand;
import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import roj.collect.EmptyList;
import roj.collect.MyHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class CommandNeXt extends CommandBase {
    public final String name;
    public final int permission;

    private final Map<String, ISubCommand> subCommands = new MyHashMap<>();

    private List<String> aliases;
    private TextComponentString helpComponent;
    private String canUseCommands;

    static List<String> empty_list = EmptyList.getInstance();

    public static ICommandListener listener;

    static Map<String, User> data;
    static Map<String, ArgumentManager<?>> decoders;

    static class User {
        ASMInvoker targetInvoker;
    }

    private interface ASMInvoker {
        void invoke(Object instance, Object[] data);
    }

    public static void initStore() {
        // todo
    }

    public CommandNeXt(String name, int level, Class<? extends CommandHandler> target) {
        this.name = name;
        this.permission = level;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "command.mi." + name + ".usage";
    }

    @Nonnull
    public List<String> getAliases() {
        return this.aliases == null ? empty_list : this.aliases;
    }

    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) throws CommandException {
        if (helpComponent == null)
            helpComponent = new TextComponentString("/" + getName() + " help <name>");
        if (args.length >= 1) {
            String s = args[0];
            ISubCommand command = subCommands.get(s);
            if (command != null) {
                command.execute(server, sender, removeFirst(args));
                return;
            } else if ("help".equals(s)) {
                if (args.length == 1) {
                    sender.sendMessage(new TextComponentString(getName() + " help <name>"));
                } else {
                    command = subCommands.get(args[1]);
                    if (command != null)
                        sender.sendMessage(new TextComponentTranslation(command.getHelp()));
                }
                return;
            }
        }
        if (canUseCommands == null) {
            if (subCommands.size() == 0)
                throw new CommandException("没有子命令! 这是一个BUG!");
            StringBuilder sb = new StringBuilder("/" + getName() + " <");
            for (String str : subCommands.keySet()) {
                sb.append(str);
                sb.append('/');
            }
            sb.append("help");
            sb.append('>');
            canUseCommands = sb.toString();
        }
        throw new WrongUsageException(canUseCommands);
    }

    public static void setCommandListener(ICommandListener _listener) {
        listener = _listener;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return this.permission;
    }

    @Nonnull
    public List<String> getTabCompletions(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, subCommands.keySet());
        } else if (args.length > 1) {
            String s = args[0];
            ISubCommand command = subCommands.get(s);
            return command == null ? ("help".equals(s) ? getListOfStringsMatchingLastWord(args, subCommands.keySet()) : empty_list) : command.getTabCompletionOptions(server, sender, removeFirst(args), pos);
        } else {
            return empty_list;
        }
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        if (args.length < 1) {
            return false;
        } else {
            String s = args[0];
            ISubCommand command = subCommands.get(s);
            return command != null && command.isUsernameIndex(removeFirst(args), index - 1);
        }
    }

    public CommandNeXt addAliases(String alias) {
        if (this.aliases == null) {
            this.aliases = new ArrayList<>();
            this.aliases.add(getName());
        }
        this.aliases.add(alias);
        return this;
    }

    public static String[] removeFirst(String[] args) {
        if (args.length < 2) {
            return new String[0];
        } else {
            String[] nArgs = new String[args.length - 1];
            System.arraycopy(args, 1, nArgs, 0, nArgs.length);
            return nArgs;
        }
    }
}
