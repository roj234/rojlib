package ilib.command.nextgen.filter;

import net.minecraft.entity.Entity;

/**
 * @author Roj234
 * @since 2023/3/1 0001 0:55
 */
abstract class StringNegatable extends Filter {
	private boolean negate;
	final String s;

	StringNegatable(String s) {
		this.s = s;
	}

	@Override
	public void accept(Entity entity) {
		if (matches(entity) != negate) {
			successCount++;
			next.accept(entity);
		}
	}

	abstract boolean matches(Entity entity);

	@Override
	public int relativeDifficulty() {
		return 800;
	}

	@Override
	public void negate() {
		negate = true;
	}
}
