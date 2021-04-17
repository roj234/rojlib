package ilib.api.registry;

/**
 * @author Roj234
 * @since 2021/6/2 23:42
 */
public interface Propertied<T extends Propertied<T>> extends Comparable<T>, Indexable {
	default int compareTo(T type) {
		return Integer.compare(getIndex(), type.getIndex());
	}
}