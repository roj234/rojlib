package roj.plugins.minecraft.server.data.world;

/**
 * @author Roj234
 * @since 2024/3/20 0020 5:36
 */
final class Ref<T> {
	Ref(T block) { this.block = block; }

	T block;
	short id;
	short count;

	Ref<T> next;

	@Override
	public String toString() {
		return String.valueOf(block);
	}
}