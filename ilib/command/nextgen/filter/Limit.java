package ilib.command.nextgen.filter;

import roj.concurrent.OperationDone;

import net.minecraft.entity.Entity;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:53
 */
public final class Limit extends Filter {
	private final int limit;

	public Limit(int limit) {this.limit = limit;}

	@Override
	public void accept(Entity entity) {
		if (++successCount >= limit) throw OperationDone.INSTANCE;

		next.accept(entity);
	}

	@Override
	public int relativeDifficulty() {
		return Integer.MIN_VALUE;
	}
}
