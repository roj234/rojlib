package ilib.api.registry;

/**
 * @author Roj234
 * @since 2021/6/2 23:55
 */
public interface IRegistry<T extends Indexable> {
	T[] values();

	T byId(int meta);

	Class<T> getValueClass();
}