package roj.ci;

import roj.asm.ClassNode;
import roj.asm.attr.Attribute;
import roj.asm.attr.StringAttribute;
import roj.asm.cp.Constant;
import roj.asm.cp.CstClass;
import roj.collect.HashMap;
import roj.util.Helpers;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 类依赖关系图，用于跟踪类之间的引用关系
 *
 * <p>该类维护一个从被引用类到引用该类的集合的映射，
 * 用于在类发生变化时确定需要重新编译的依赖类。
 * 注意：这里的类名都是直接文件名，内部类名会被擦除。
 * @author Roj234-N
 * @since 2025/5/7 13:11
 */
final class DependencyGraph {
	private final Map<String, Set<String>> graph = new HashMap<>();

	public boolean isEmpty() {return graph.isEmpty();}

	/**
	 * 获取所有直接引用指定类的类集合
	 * @param className 被引用的类名（直接文件名）
	 * @return 引用该类的类名集合（直接文件名），如果没有则返回空集合
	 */
	public Set<String> get(String className) {return graph.getOrDefault(className, Collections.emptySet());}

	/**
	 * 解析类节点并添加其依赖关系到图中
	 * @param node 要分析的类节点
	 */
	public void add(ClassNode node) {
		String name = node.name();
		StringAttribute nestHost = node.getAttribute(node.cp, Attribute.NestHost);
		if (nestHost != null) name = nestHost.value;

		for (Constant cp : node.cp().constants()) {
			if (cp instanceof CstClass ref) {
				// node类引用了ref类，所以在ref修改时需要重新编译node类
				String str = ref.value().str();
				if (str.equals(name) || str.startsWith("java/")) continue;

				Set<String> set = graph.computeIfAbsent(str, Helpers.fnHashSet());
				set.add(name);
			}
		}
	}

	/**
	 * 从依赖图中移除指定类的所有引用关系
	 * @param changed 要移除的类名集合（直接文件名）
	 */
	public void remove(Set<String> changed) {
		//graph.keySet().removeAll(changed);
		for (Set<String> value : graph.values()) {
			value.removeAll(changed);
		}
	}

	public void clear() {graph.clear();}
}
