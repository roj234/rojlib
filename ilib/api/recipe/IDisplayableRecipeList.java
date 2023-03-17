package ilib.api.recipe;

import java.util.Collection;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public interface IDisplayableRecipeList extends IRecipeList {
	Collection<IRecipe> getDisplayableRecipes();
}