package ilib.api.registry;

/**
 * @author Roj234
 * @since 2020/8/21 14:15
 */
public interface Indexable {
	int getIndex();

	String getName();

	abstract class Impl implements Indexable {
		int index;
		public final String name;

		public final int getIndex() {
			return this.index;
		}

		public final String getName() {
			return this.name;
		}

		protected Impl(String name) {
			this.name = name;
		}

		protected Impl(String name, int index) {
			this.name = name;
			this.index = index;
		}
	}
}
