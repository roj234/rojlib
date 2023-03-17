package ilib.command;

import com.mojang.authlib.GameProfile;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListOps;
import net.minecraft.server.management.UserListOpsEntry;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.SaveHandler;

import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * @author Roj234
 * @since 2022/12/11 0011 1:45
 */
public class OpCuff extends CommandBase {
	@Override
	public String getName() {
		return "opcuff";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/opcuff <add/remove> <playse>";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (!(sender instanceof MinecraftServer)) {
			sender.sendMessage(new TextComponentString("该指令只能服务器后台使用"));
		} else {
			if (args.length < 2) {
				throw new WrongUsageException(getUsage(sender));
			}
			String playerName = buildString(args, 1);
			GameProfile profile = server.getPlayerProfileCache().getGameProfileForUsername(playerName);
			if (profile == null) {
				throw new CommandException("没有这个人["+playerName+"]");
			}

			server.getPlayerList().getOppedPlayers().removeEntry(profile);
			boolean add = args[0].equalsIgnoreCase("add");
			if (add) {
				server.getPlayerList().getOppedPlayers().addEntry(new UserListOpsEntry(profile, 0, false));
			}

			EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(profile.getId());
			if (online != null) {
				if (add) {
					online.getEntityData().setBoolean("_Cuff_", true);
				} else {
					NBTTagCompound op = getOptional(online);
					if (op != null) {
						op.removeTag("_Cuff_");
					}
				}
			} else {
				File dir;
				try {
					IPlayerFileData offline = server.getEntityWorld().getSaveHandler().getPlayerNBTManager();
					Field fuckMojang = SaveHandler.class.getDeclaredField("field_75771_c");
					fuckMojang.setAccessible(true);
				    dir = (File) fuckMojang.get(offline);
				} catch (ReflectiveOperationException e) {
					throw new CommandException(e.toString());
				}
				// 记得调试一下文件名
				File playerNbt = new File(dir, profile.getId().toString() + ".dat");
				System.out.println(playerNbt.getAbsolutePath());
				if (!playerNbt.isFile()) return;
				try {
					NBTTagCompound playerData = CompressedStreamTools.read(playerNbt);
					if (playerData == null) {
						throw new CommandException("NBT Deser fail");
					}
					if (!playerData.hasKey("ForgeData")) {
						if (add) playerData.setTag("ForgeData", new NBTTagCompound());
					}
					NBTTagCompound map = playerData.getCompoundTag("ForgeData");
					if (add) {
						map.setBoolean("_Cuff_", true);
					} else {
						map.removeTag("_Cuff_");
					}
					CompressedStreamTools.write(playerData, playerNbt);
				} catch (IOException e) {
					throw new CommandException(e.toString());
				}
			}
		}
	}

	static Field entityDataField;
	static {
		try {
			Field field = Entity.class.getDeclaredField("customEntityData");
			field.setAccessible(true);
			entityDataField = field;
		} catch (Exception ignored) {}
	}
	private static NBTTagCompound getOptional(EntityPlayerMP online) {
		try {
			if (entityDataField != null) {
				return (NBTTagCompound) entityDataField.get(online);
			}
		} catch (Exception ignored) {}
		return online.getEntityData();
	}

	@SubscribeEvent
	public static void onCommandProcess(CommandEvent event) {
		ICommandSender p = event.getSender();
		if (p instanceof EntityPlayerMP) {
			EntityPlayerMP p1 = (EntityPlayerMP) p;
			NBTTagCompound tag = getOptional(p1);
			if (tag != null && tag.hasKey("_Cuff_")) {
				if (p.canUseCommand(1, "deop")) {
					UserListOps ops = p.getServer().getPlayerList().getOppedPlayers();
					ops.removeEntry(p1.getGameProfile());
					ops.addEntry(new UserListOpsEntry(p1.getGameProfile(), 0, false));
				}
				if (event.getCommand() instanceof CommandBase) {
					if (((CommandBase) event.getCommand()).getRequiredPermissionLevel() > 0) {
						event.setCanceled(true);
						p.sendMessage(new TextComponentString("SB"));
					}
				} else {
					// 自己解决不是CommandBase
				}
			}
		}
	}
}
