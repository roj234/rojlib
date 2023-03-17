package ilib.util;

import ilib.api.energy.MEItem;
import ilib.api.energy.MEItem.EnergyType;
import ilib.api.energy.METile;
import ilib.capabilities.Capabilities;
import roj.collect.SimpleList;
import roj.text.TextUtil;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class EnergyHelper {
	private EnergyHelper() {
	}

	public static final MEItem EMPTYCAP = new MEItem() {
		@Override
		public NBTTagCompound serializeNBT() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void deserializeNBT(NBTTagCompound nbtTagCompound) {
			throw new UnsupportedOperationException();
		}

		@Override
		public EnergyType getEnergyType() {
			return EnergyType.TUBE;
		}

		public int currentME() {
			return 0;
		}

		public int maxME() {
			return 0;
		}

		public boolean canExtract() {
			return false;
		}

		public boolean canReceive() {
			return false;
		}

		public MEItem setME(int count) {
			return this;
		}

		public int extractME(int count, boolean simulate) {
			return 0;
		}

		public int receiveME(int count, boolean simulate) {
			return 0;
		}

		public int voltage() {
			return -1;
		}

		public int receiveSpeed() {
			return 0;
		}

		public int extractSpeed() {
			return 0;
		}
	};

	/* NBT TAG HELPER */
	public static void addEnergyInformation(@Nonnull ItemStack stack, @Nonnull List<String> list) {
		MEItem cap = stack.getCapability(Capabilities.MENERGY, null);
		if (cap != null) {
			list.add(I18n.format("tooltip.mi.energy") + TextUtil.scaledNumber(getEnergyStored(stack)) + " / " + TextUtil.scaledNumber(cap.maxME()) + " ME");
		} else if (ItemNBT.getRootTag(stack).hasKey("Power")) {
			list.add(I18n.format("tooltip.mi.energy") + TextUtil.scaledNumber(ItemNBT.getInt(stack, "Power")) + " / " + TextUtil.scaledNumber(ItemNBT.getInt(stack, "MaxME")) + " ME");
		} else {list.add("NaN/NaN ME");}
	}

	@Nonnull
	public static String addEnergyInformation(@Nonnull TileEntity te) {
		METile cap = te.getCapability(Capabilities.MENERGY_TILE, null);
		if (cap != null) {
			return I18n.format("tooltip.mi.energy") + TextUtil.scaledNumber(cap.currentME()) + " / " + TextUtil.scaledNumber(cap.maxME()) + " ME";
		}
		return "NaN/NaN ME";
	}

	/* IMEnergyItemContainer Interaction */
	public static int extractMEFromHeldStack(EntityPlayer player, int maxExtract, boolean simulate) {
		ItemStack stack = player.getHeldItemMainhand();

		return getCapabilityDefault(stack).extractME(maxExtract, simulate);
	}

	public static int insertMEIntoHeldStack(EntityPlayer player, int maxReceive, boolean simulate) {
		ItemStack stack = player.getHeldItemMainhand();
		return getCapabilityDefault(stack).receiveME(maxReceive, simulate);
	}

	public static MEItem getFirstUsableBattery(EntityPlayer player) {
		final int LAST = InventoryPlayer.getHotbarSize();

		MEItem cap;
		for (int i = 0; i < LAST; i++) {
			ItemStack stack = player.inventory.getStackInSlot1(i);
			if ((cap = stack.getCapability(Capabilities.MENERGY, null)) != null) {
				if (cap.canExtract() && cap.currentME() > 0) return cap;
			}
		}
		return null;
	}

	private static final List<MEItem> tempList = new SimpleList<>(15);

	public static void batteryTick(EntityPlayer player, int searchBatterySlot, int searchItemSlot) {
		final int LAST = Math.max(searchBatterySlot, searchItemSlot);
		tempList.clear();

		MEItem capr;
		MEItem battery = null;

		InventoryPlayer inv = player.inventory;
		for (int i = 0; i < LAST; i++) {
			ItemStack stack = inv.getStackInSlot1(i);
			if ((capr = stack.getCapability(Capabilities.MENERGY, null)) != null) {
				if (i < searchBatterySlot && battery == null && capr.canExtract()) {battery = capr;} else if (i < searchItemSlot) {
					if (!isBattery(capr) && capr.canReceive()) tempList.add(capr);
				}
			}
		}
		int len = tempList.size();
		if (len == 0 || battery == null) return;

		int maxExtract = Math.min(battery.currentME(), battery.extractSpeed());
		int forEach = maxExtract / len;
		for (MEItem cap : tempList) {
			if (!battery.canExtract()) return;
			EnergyHelper.safeTransfer(battery, cap, forEach);
		}
	}

	public static boolean isBattery(MEItem cap) {
		return cap.getEnergyType() == EnergyType.STORAGE;
	}

	public static int safeTransfer(MEItem from, MEItem to, int count) {
		return from.extractME(to.receiveME(from.extractME(count, true), false), false);
	}

	public static boolean isHoldingEnergyItem(EntityPlayer player) {
		return getCapabilityDefault(player.getHeldItemMainhand()) != EMPTYCAP;
	}

	public static int getEnergyStored(ItemStack stack) {
		MEItem cap = getCapabilityDefault(stack);
		NBTTagCompound tag = stack.getTagCompound();
		if (cap.currentME() != 0) {
			return cap.currentME();
		} else if (tag != null && tag.hasKey("clientPower", 99)) {
			return tag.getInteger("clientPower");
		} else {
			return 0;
		}
	}

	public static MEItem getCapabilityDefault(ItemStack stack) {
		MEItem cap = stack.getCapability(Capabilities.MENERGY, null);
		return stack.isEmpty() ? EMPTYCAP : (cap == null ? EMPTYCAP : cap);
	}
}