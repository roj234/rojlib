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
import ilib.util.ItemNBT;
import ilib.util.TextHelper;
import ilib.util.energy.EnergyHelper;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;

import static ilib.item.ItemBase.color;
import static ilib.item.ItemBase.tips;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ItemBlockMI extends ItemBlock {
    public ItemBlockMI(Block block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public final void addInformation(ItemStack itemstack, World world, @Nonnull List<String> list, @Nonnull ITooltipFlag flag) {
        ItemBlockMI i = (ItemBlockMI) itemstack.getItem();
        ITooltip tooltip = null;
        if (i.block instanceof ITooltip) {
            tooltip = (ITooltip) i.block;
        } else if (i instanceof ITooltip) {
            tooltip = (ITooltip) i;
        }

        if (tooltip != null) {
            color.clear();
            tips.clear();
            tooltip.getTooltip(tips, color, itemstack);

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

                list.add(Colors.ORANGE + ItemBase.getOwnerTypeName(ownerType));
                list.add(Colors.BLUE + I18n.format("tooltip.ilib.owner") + owner);
            }
        }
    }

    public String getItemStackDisplayName(ItemStack stack) {
        return TextHelper.translate(this.getTranslationKey(stack)).trim();
    }

    @Override
    public boolean isInCreativeTab(CreativeTabs tab) {
        return tab == getCreativeTab() || tab == CreativeTabs.SEARCH;
    }

    @Override
    public final void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> list) {
        if (isInCreativeTab(tab) || canDisplayIn(tab)) {
            getSubItems(list);
        }
    }

    public boolean canDisplayIn(CreativeTabs tab) {
        return false;
    }

    protected void getSubItems(NonNullList<ItemStack> list) {
        list.add(new ItemStack(this));
    }
}