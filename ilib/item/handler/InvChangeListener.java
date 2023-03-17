/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: InvChangeListener.java
 */
package ilib.item.handler;

import net.minecraft.item.ItemStack;

import net.minecraftforge.items.IItemHandler;

public interface InvChangeListener {
	void onInventoryChanged(IItemHandler i, int slot, ItemStack old, ItemStack now);
}