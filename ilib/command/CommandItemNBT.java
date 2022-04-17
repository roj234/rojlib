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
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class CommandItemNBT extends CommandBase {
    @Override
    public String getName() {
        return "itemdata";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "Usage: /itemdata [dataTag]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        int _start = 0;
        EntityPlayerMP player = (sender instanceof EntityPlayerMP) ? (EntityPlayerMP) sender : (args.length == 0 ? null : getPlayer(server, sender, args[0]));
        if (sender != player)
            _start++;
        if (player == null) {
            throw new PlayerNotFoundException("command.il.itemnbt.no");
        }
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) {
            throw new WrongUsageException("command.il.itemnbt.notitem");
        }
        NBTTagCompound tag = held.getTagCompound();
        if (tag == null && args.length == 0) {
            throw new CommandException("command.il.itemnbt.nonbt");
        }
        if (args.length == 0) {
            player.sendMessage(new TextComponentString(tag.toString()));
        } else {
            String str = buildString(args, _start);
            NBTTagCompound newOne;
            try {
                newOne = JsonToNBT.getTagFromJson(str.replace("&", "\u00a7"));
            } catch (NBTException e) {
                throw new CommandException("commands.blockdata.tagError", e.getMessage());
            }
            NBTTagCompound finalTag = tag != null ? tag.copy() : new NBTTagCompound();
            finalTag.merge(newOne);
            if (finalTag.equals(tag)) {
                throw new CommandException("commands.blockdata.failed", finalTag.toString());
            }
            held.setTagCompound(finalTag.isEmpty() ? null : finalTag);
            player.setHeldItem(EnumHand.MAIN_HAND, held);

            notifyCommandListener(sender, this, "commands.blockdata.success", finalTag.toString());
        }
    }

}
