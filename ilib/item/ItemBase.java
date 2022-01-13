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

package ilib.item;

import ilib.api.Ownable;
import ilib.api.item.ILostThing;
import ilib.api.item.IShiftTooltip;
import ilib.api.item.ITooltip;
import ilib.util.Colors;
import ilib.util.ForgeUtil;
import ilib.util.ItemNBT;
import ilib.util.TextHelper;
import ilib.util.energy.EnergyHelper;

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

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class ItemBase extends Item {
    protected static final List<String> color = new ArrayList<>();
    protected static final List<String> tips = new ArrayList<>();

    public String modid() {
        ResourceLocation resloc = getRegistryName();
        if (resloc != null)
            return resloc.getNamespace();
        return ForgeUtil.getCurrentModId();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public final void addInformation(ItemStack itemstack, World world, @Nonnull List<String> list, @Nonnull ITooltipFlag flag) {
        Item i = itemstack.getItem();
        if (i instanceof ITooltip) {
            ITooltip t = (ITooltip) i;

            color.clear();
            tips.clear();
            t.getTooltip(tips, color, itemstack);

            if (i instanceof IShiftTooltip) {
                String z = I18n.format(tips.get(0));
                TextHelper.shiftLore(list, color.size() > 0 ? I18n.format(color.get(0)) + z : z);
            } else {
                String s, s2;
                for (int j = 0; j < tips.size(); j++) {
                    s = tips.get(j);
                    s = s == null ? "" : I18n.format(s);
                    if (color.size() > j && (s2 = color.get(j)) != null) {
                        list.add(I18n.format(s2) + s);
                    } else {
                        list.add(s);
                    }
                }
            }
        }

        if (i instanceof ILostThing) {
            ILostThing t = (ILostThing) i;
            EntityPlayer clientPlayer = Minecraft.getMinecraft().player;
            if (clientPlayer != null) {
                if (t.isOwned(itemstack))
                    if (t.isOwner(itemstack, clientPlayer))
                        list.add(Colors.ORANGE + I18n.format("tooltip.ilib.lstd.bound") + clientPlayer.getName());
                    else
                        list.add(Colors.DARK_RED + I18n.format("tooltip.ilib.lstd.notu"));
                else
                    list.add(Colors.ORANGE + I18n.format("tooltip.ilib.lstd.nobound"));
            }
        }

        NBTTagCompound tag = ItemNBT.getRootTagNullable(itemstack);
        if (tag != null) {
            if (tag.getInteger("MaxME") != 0)
                EnergyHelper.addEnergyInformation(itemstack, list);
            if (tag.hasKey("Owner")) {
                if (!(tag.getTag("Owner") instanceof NBTTagCompound)) {
                    tag.removeTag("Owner");
                    return;
                }
                NBTTagCompound tag2 = (NBTTagCompound) tag.getTag("Owner");
                int ownerType = tag2.getInteger("Type");
                String owner = tag2.getString("Name");

                if (owner.equals(Ownable.UNKNOWN)) {
                    tag.removeTag("Owner");
                    return;
                }

                list.add(Colors.ORANGE + getOwnerTypeName(ownerType));
                list.add(Colors.BLUE + I18n.format("tooltip.ilib.owner") + owner);
            }
        }
    }

    public String getItemStackDisplayName(ItemStack stack) {
        return TextHelper.translate(this.getTranslationKey(stack)).trim();
    }

    @Override
    public final void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> list) {
        if (isInCreativeTab(tab)) {
            getSubItems(list);
        }
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
                return TextHelper.translate("gui.mi.tabs.perm.public");
            case 1:
                return TextHelper.translate("gui.mi.tabs.perm.private");
            case 2:
                return TextHelper.translate("gui.mi.tabs.perm.protected");
            default:
                return TextHelper.translate("gui.mi.tabs.redstone.error");
        }
    }
}