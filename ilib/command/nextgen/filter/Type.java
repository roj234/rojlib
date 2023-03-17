package ilib.command.nextgen.filter;

import net.minecraft.entity.Entity;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:53
 */
public final class Type extends Filter {
	private final Class<? extends Entity> type;

	public Type(Class<? extends Entity> type) {
		this.type = type;
	}

	@Override
	public void accept(Entity entity) {
		if (entity.getClass().isInstance(type)) {
			successCount++;
			next.accept(entity);
		}
	}

	@Override
	public int relativeDifficulty() {
		return 600;
	}
}
