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
		return 3;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "Usage: /itemdata [dataTag]";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		int _start = 0;
		EntityPlayerMP player = (sender instanceof EntityPlayerMP) ? (EntityPlayerMP) sender : (args.length == 0 ? null : getPlayer(server, sender, args[0]));
		if (sender != player) _start++;
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
