/*
 * This file is a part of MoreItems
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

import lac.server.packets.PacketScreenshot;
import lac.server.packets.SPacketCrash;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * @author Roj233
 * @since 2021/10/15 20:10
 */
public class CmdLAC extends CommandBase {
    @Override
    public String getName() {
        return "lac";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/lac <shot/kill/upload> <name>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/lac <shot/kill> <name>");
        }
        if (sender instanceof MinecraftServer) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(args[1]);
            if (player == null)
                throw new CommandException("Not found player!");
            switch (args[0].toLowerCase()) {
                case "shot":
                    player.connection.netManager.sendPacket(new PacketScreenshot());
                    break;
                case "kill":
                    player.connection.netManager.sendPacket(new SPacketCrash());
                    break;
            }
        }
    }
}
