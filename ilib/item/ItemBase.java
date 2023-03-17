package ilib.item;

import ilib.api.item.ILostThing;
import ilib.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ItemBase extends Item {
	protected final String modid() {
		ResourceLocation name = getRegistryName();
		if (name != null) return name.getNamespace();
		return ForgeUtil.getCurrentModId();
	}

	protected void addTooltip(ItemStack stack, List<String> list) {}

	@Override
	@SideOnly(Side.CLIENT)
	public final void addInformation(ItemStack stack, World world, List<String> list, ITooltipFlag flag) {
		addTooltip(stack, list);

		Item i = stack.getItem();
		if (i instanceof ILostThing) {
			ILostThing t = (ILostThing) i;
			EntityPlayer clientPlayer = Minecraft.getMinecraft().player;
			if (clientPlayer != null) {
				if (t.isOwned(stack)) {
					if (t.isOwner(stack, clientPlayer)) {list.add(ChatColor.ORANGE + I18n.format("tooltip.ilib.lstd.bound") + clientPlayer.getName());} else {
						list.add(ChatColor.DARK_RED + I18n.format("tooltip.ilib.lstd.notu"));
					}
				} else {list.add(ChatColor.ORANGE + I18n.format("tooltip.ilib.lstd.nobound"));}
			}
		}

		NBTTagCompound tag = ItemNBT.getRootTagNullable(stack);
		if (tag != null) {
			if (tag.getInteger("MaxME") != 0) EnergyHelper.addEnergyInformation(stack, list);
			if (tag.hasKey("Owner", NBTType.COMPOUND)) {
				NBTTagCompound owner = tag.getCompoundTag("Owner");
				int ownerType = owner.getInteger("OwnType");
				String ownerId = owner.getString("Own");

				list.add(ChatColor.ORANGE + ItemBase.getOwnerTypeName(ownerType));
				list.add(ChatColor.BLUE + I18n.format("tooltip.ilib.owner") + ownerId);
			}
		}
	}

	public String getItemStackDisplayName(ItemStack stack) {
		return MCTexts.format(this.getTranslationKey(stack)).trim();
	}

	@Override
	public final void getSubItems(CreativeTabs tab, NonNullList<ItemStack> list) {
		if (isInCreativeTab(tab)) getSubItems(list);
	}

	@Override
	public boolean isInCreativeTab(CreativeTabs tab) {
		return tab == getCreativeTab() || tab == CreativeTabs.SEARCH;
	}

	protected void getSubItems(NonNullList<ItemStack> list) {
		list.add(new ItemStack(this));
	}

	public static String getOwnerTypeName(int type) {
		switch (type) {
			case 0:
			default:
				return MCTexts.format("ilib.perm.public");
			case 1:
				return MCTexts.format("ilib.perm.private");
			case 2:
				return MCTexts.format("ilib.perm.protected");
		}
	}
}