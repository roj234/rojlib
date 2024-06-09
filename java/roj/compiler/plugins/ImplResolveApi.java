package roj.compiler.plugins;

import roj.asm.tree.ConstantData;
import roj.collect.Hasher;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.compiler.context.Library;
import roj.compiler.plugins.api.Resolver;

import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * @author Roj234
 * @since 2024/5/21 0021 2:26
 */
final class ImplResolveApi implements Library {
	private final PriorityQueue<Sortable<Resolver>> dr = new PriorityQueue<>();
	private final MyHashMap<String, ConstantData> generated = new MyHashMap<>();
	private final MyHashSet<Object> identity = new MyHashSet<>(Hasher.identity());

	private static final class Sortable<T> implements Comparable<Sortable<T>> {
		final int priority;
		final T resolver;

		Sortable(int priority, T resolver) {
			this.priority = priority;
			this.resolver = resolver;
		}

		public int compareTo(Sortable<T> resolver) {return Integer.compare(priority, resolver.priority);}
	}

	public void addTypeResolver(int priority, Resolver dtr) {dr.add(new Sortable<>(priority, dtr));}

	public ConstantData preFilter(CharSequence name) {
		for (Sortable<Resolver> sortable : dr) {
			ConstantData c = sortable.resolver.resolveClass(name);
			if (c != null) {
				generated.put(name.toString(), c);
				return c;
			}
		}
		return null;
	}

	public void postFilter(ConstantData info) {
		if (!identity.add(info)) return;

		for (Sortable<Resolver> sortable : dr) {
			sortable.resolver.classResolved(info);
		}
	}

	public void packageListed(String pkg, List<String> list) {
		if (!identity.add(list)) return;

		for (Sortable<Resolver> sortable : dr) {
			sortable.resolver.packageListed(pkg, list);
		}
	}

	@Override
	public Set<String> content() {return Collections.emptySet();}
	@Override
	public ConstantData get(CharSequence name) {return generated.get(name);}
}