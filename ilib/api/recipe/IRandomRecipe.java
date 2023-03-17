package ilib.api.recipe;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public interface IRandomRecipe<T> {
	T addNormal();

	T addRandom(int min, int max, double... factor);
}
