package roj.plugins.ci;

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
 * @author Roj234-N
 * @since 2025/5/7 13:11
 */
final class DependencyGraph {
	private final Map<String, Set<String>> graph = new HashMap<>();
	//private final ToLongMap<String> memberHash = new ToLongMap<>();

	/**
	 * 添加一个类
	 */
	public void add(ClassNode node) {
		String name = node.name();
		StringAttribute nestHost = node.getAttribute(node.cp, Attribute.NestHost);
		if (nestHost != null) name = nestHost.value;

		for (Constant cp : node.cp().data()) {
			if (cp instanceof CstClass ref) {
				// node类引用了ref类，所以在ref修改时需要重新编译node类
				String str = ref.value().str();
				if (str.equals(name)) continue;

				Set<String> set = graph.computeIfAbsent(str, Helpers.fnHashSet());
				set.add(name);
			}
		}
	}

	/**
	 * 获取引用类className的类
	 */
	public Set<String> get(String className) {
		return graph.getOrDefault(className, Collections.emptySet());
	}

	public void remove(Set<String> changed) {
		for (Set<String> value : graph.values()) {
			value.removeAll(changed);
		}
	}

	public void clear() {
		graph.clear();
	}

	public boolean isEmpty() {
		return graph.isEmpty();
	}
}
