package roj.reflect.resolver;

import roj.asm.ClassNode;
import roj.collect.ToIntMap;

/**
 * @author Roj234
 * @since 2025/09/19 19:45
 */
public interface IResolver {
	ClassNode resolve(CharSequence name);
	ToIntMap<String> getHierarchyList(ClassNode info);
}
