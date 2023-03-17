package ilib.api.recipe;

import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * 多输入的合成
 */
public interface MultiInputRecipe extends IRecipe {
	default List<ItemStack> getInput() {
		throw new IllegalStateException();
	}

	List<ItemStack[]> getMultiInputs();
}