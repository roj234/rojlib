/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ItemColor.java
 */
package ilib.api;

import net.minecraft.item.ItemStack;

public interface ItemColor {
	int getColor(ItemStack stack, int tintIndex);
}
