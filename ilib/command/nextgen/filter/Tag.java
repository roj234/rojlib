package ilib.command.nextgen.filter;

import net.minecraft.entity.Entity;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:53
 */
public final class Tag extends StringNegatable {
	public Tag(String tag) {
		super(tag);
	}

	@Override
	boolean matches(Entity entity) {
		return s.isEmpty() ? entity.getTags().isEmpty() : entity.getTags().contains(s);
	}
}
