/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: FurnaceRecipe.java
 */
package ilib.api.recipe.impl;

import ilib.api.recipe.IRecipe;
import ilib.fluid.handler.IFluidProvider;

import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class FurnaceRecipe implements IRecipe {
	private final List<ItemStack> inputs;
	private final List<ItemStack> outputs;

	public FurnaceRecipe(ItemStack in, ItemStack out) {
		inputs = Collections.singletonList(in);
		outputs = Collections.singletonList(out);
	}

	public boolean matches(@Nonnull IFluidProvider fp, @Nonnull List<ItemStack> list) {
		return true;
	}

	@Nonnull
	@Override
	public List<ItemStack> operateInput(@Nonnull IFluidProvider fp, @Nonnull List<ItemStack> input) {
		if (IRecipe.decrStackSize(input, 0, 1).isEmpty()) {
			throw new RuntimeException("FurnaceRecipe: Invalid input");
		}
		return input;
	}

	@Override
	public boolean isShaped() {
		return true;
	}

	@Nonnull
	public String getName() {
		return "FurnaceRecipe";
	}

	public int getTimeCost() {
		return 100;
	}

	public int getPowerCost() {
		return 20;
	}

	public List<ItemStack> getInput() {
		return inputs;
	}

	public List<ItemStack> getOutput() {
		return outputs;
	}
}
