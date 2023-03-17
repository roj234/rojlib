package ilib.asm.util;

import net.minecraft.entity.Entity;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/5/25 2:15
 */
public interface IEntityList {
	List<Entity> getValue();

	<T extends Entity> List<T> getValue(Class<T> clazz);
}
