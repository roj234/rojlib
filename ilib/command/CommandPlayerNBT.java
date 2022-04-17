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

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class CommandPlayerNBT extends CommandBase {
    @Nonnull
    @Override
    public String getName() {
        return "playerdata";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "Usage: /playerdata <player> [dataTag]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP targetPlayer;
        NBTTagCompound playerTag = new NBTTagCompound();
        if (args.length == 0) {
            targetPlayer = getCommandSenderAsPlayer(sender);
            targetPlayer.writeEntityToNBT(playerTag);
            sender.sendMessage(new TextComponentString(playerTag.toString()));
            return;
        }
        targetPlayer = getPlayer(server, sender, args[0]);
        targetPlayer.writeEntityToNBT(playerTag);

        if (args.length == 1) {
            sender.sendMessage(new TextComponentString(playerTag.toString()));
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                sb.append(args[i]);
                sb.append(' ');
            }
            NBTTagCompound newOne;
            try {
                newOne = JsonToNBT.getTagFromJson(sb.toString().replace("&", "\u00a7"));
            } catch (NBTException e) {
                throw new CommandException("commands.blockdata.tagError", e.getMessage());
            }
            NBTTagCompound finalTag = playerTag.copy();
            finalTag.merge(newOne);
            if (playerTag.equals(finalTag)) {
                throw new CommandException("commands.blockdata.failed", finalTag.toString());
            }
            targetPlayer.readEntityFromNBT(finalTag);
            targetPlayer.setPlayerHealthUpdated();
            targetPlayer.sendPlayerAbilities();
            server.getPlayerList().serverUpdateMovingPlayer(targetPlayer);

            notifyCommandListener(sender, this, "commands.blockdata.success", finalTag.toString());
        }
    }

}
