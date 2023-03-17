package ilib.api.recipe;

import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/23 17:16
 */
public interface IModifiableRecipeList extends IRecipeList {
	void addRecipe(IRecipe recipe);

	default boolean remove(String name) {
		return false;
	}

	default boolean removeByInput(List<ItemStack[]> inputs) {
		return false;
	}

	default boolean removeByOutput(List<ItemStack> outputs) {
		return false;
	}
}