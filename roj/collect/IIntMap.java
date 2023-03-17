package roj.collect;

/**
 * @author Roj233
 * @since 2021/10/14 18:41
 */
public interface IIntMap<V> {
	int size();

	default V putInt(int key, V e) {
		putInt(e, key);
		return null;
	}

	default V get(int key) {throw new UnsupportedOperationException();}

	default Integer putInt(V key, int e) {
		putInt(e, key);
		return null;
	}

	default int getInt(V key) {throw new UnsupportedOperationException();}
}
