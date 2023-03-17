package ilib.command.nextgen.filter;

import net.minecraft.entity.Entity;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:53
 */
public final class Name extends StringNegatable {
	public Name(String name) {
		super(name);
	}

	@Override
	boolean matches(Entity entity) {
		return entity.getName().equals(s);
	}
}
