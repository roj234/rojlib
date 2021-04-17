package ilib.command.sub;

import com.mojang.authlib.GameProfile;
import ilib.util.PlayerUtil;
import roj.collect.MyBitSet;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.common.util.FakePlayer;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static net.minecraft.command.CommandBase.getCommandSenderAsPlayer;
import static net.minecraft.command.CommandBase.getListOfStringsMatchingLastWord;

/**
 * @author Roj234
 * @since 2023/1/31 0031 22:53
 */
public class CmdInvSee extends AbstractSubCommand {
	private static class ReadonlyContainer extends InventoryBasic {
		final boolean rw;
		final IInventory backend;
		public ReadonlyContainer(IInventory backend, boolean writable) {
			super(null, false, 0);
			this.backend = backend;
			rw = writable;
		}
		public int getSizeInventory() {
			return backend.getSizeInventory();
		}
		public boolean isEmpty() {
			return backend.isEmpty();
		}
		public ItemStack getStackInSlot1(int i) {
			return backend.getStackInSlot1(i);
		}
		public ItemStack decrStackSize(int slot, int count) {
			if (!rw) return null;
			return backend.decrStackSize(slot, count);
		}
		public ItemStack removeStackFromSlot(int index) {
			if (!rw) return null;
			return backend.removeStackFromSlot(index);
		}
		public void setInventorySlotContents(int index, ItemStack stack) {
			if (!rw) return;
			backend.setInventorySlotContents(index, stack);
		}
		public void markDirty() {
			if (rw) backend.markDirty();
		}
		public boolean isItemValidForSlot(int slot, ItemStack stack) {
			return rw&&backend.isItemValidForSlot(slot, stack);
		}
		public void clear() {
			if (rw) backend.clear();
		}
	}

	public String getName() {
		return "invsee";
	}

	@Override
	public String getHelp() {
		return "/invsee <player> [E|F]";
	}

	public boolean isUsernameIndex(String[] args, int userIndex) {
		return (userIndex == 0);
	}

	@Override
	public List<String> getTabCompletionOptions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
		// 该列表包括离线玩家
		if (args.length == 1) return getListOfStringsMatchingLastWord(args, server.getPlayerProfileCache().getUsernames());
		if (args.length == 2) return getListOfStringsMatchingLastWord(args, "enderchest");
		return null;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) throw new WrongUsageException(getHelp());
		EntityPlayerMP me = getCommandSenderAsPlayer(sender);

		EntityPlayerMP target = getExistPlayer(server, args[0]);
		if (target == null) throw new PlayerNotFoundException(args[0]);

		MyBitSet flag = args.length == 2 ? MyBitSet.from(args[1]) : new MyBitSet();

		IInventory inv = flag.contains('E') ? target.getInventoryEnderChest() : target.inventory;

		ReadonlyContainer inv1 = new ReadonlyContainer(inv, !(target instanceof FakePlayer) && flag.contains('F'));
		inv1.setCustomName(target.getName()+"的"+(flag.contains('E')?"末影箱":"物品栏")+(inv1.rw?"":"(只读)"));
		me.displayGUIChest(inv1);
	}

	private EntityPlayerMP getExistPlayer(MinecraftServer server, String name) {
		EntityPlayerMP player = PlayerUtil.getPlayer(name);
		if (player == null) {
			GameProfile profile = server.getPlayerProfileCache().getGameProfileForUsername(name);
			if (profile == null) return null;

			File playerData = new File(server.worlds[0].getSaveHandler().getWorldDirectory(), "playerdata");
			File entry = new File(playerData, profile.getId().toString() + ".dat");

			try {
				NBTTagCompound nbt = CompressedStreamTools.read(entry);
				if (nbt != null) {
					player = new FakePlayer(server.worlds[0], profile);
					player.readEntityFromNBT(nbt);
				}
			} catch (IOException ignored) {}
		}
		return player;
	}
}
