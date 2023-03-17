package ilib.api.recipe;

import ilib.fluid.handler.IFluidProvider;

import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface IRecipeList {
	IRecipe contains(List<ItemStack> list, IFluidProvider machine);
}