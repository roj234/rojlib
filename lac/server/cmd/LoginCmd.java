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

package lac.server.cmd;

import ilib.command.sub.AbstractSubCommand;
import ilib.command.sub.MySubs;
import lac.server.LoginMgr;
import lac.server.LoginMgr.Account;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Login Commands
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/23 14:15
 */
public class LoginCmd extends AbstractSubCommand {
    public static final MySubs CHANGE_PASS = new MySubs("change") {
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 2) throw new WrongUsageException("/login change <old> <new>");
            EntityPlayerMP player = AbstractSubCommand.getAsPlayer(sender);
            Account account = LoginMgr.get(player);

            notifyCommandListener(sender, account != null && account.changePassword(args[0], args[1]) ? "lac.success" : "lac.fail");
        }
    };

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1) throw new WrongUsageException("/login remove <account>");

        boolean flag = LoginMgr.removeAccount(args[0]);

        notifyCommandListener(sender, flag ? "lac.success" : "lac.fail");
    }

    public static final MySubs SAVE_LOAD = new MySubs("sl", "lac.login.sl") {
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (args.length != 1) throw new WrongUsageException("/login sl <s/l>");

            switch (args[0]) {
                case "s":
                case "save":
                    LoginMgr.save();
                    notifyCommandListener(sender, "Saved");
                    break;
                case "l":
                case "load":
                    LoginMgr.load();
                    notifyCommandListener(sender, "Loaded");
                    break;
            }
        }
    };
}
