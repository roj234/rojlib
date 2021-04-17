package ilib.asm.util;

import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.collect.MyHashMap;
import roj.collect.TrieTree;
import roj.util.Helpers;

import net.minecraft.client.util.SearchTree;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2020/10/1 1:17
 */
@Nixim("/")
public class FastSearchTree<T> extends SearchTree<T> {
	@Copy(unique = true)
	private final Map<String, TrieTree<T>> idMap = new MyHashMap<>(4, 1);
	@Copy(unique = true)
	private final TrieTree<T> tree = new TrieTree<>();
	@Copy(unique = true)
	static final Function<String, TrieTree<?>> fn = (y) -> new TrieTree<>();

	public FastSearchTree(Function<T, Iterable<String>> nameFunc, Function<T, Iterable<ResourceLocation>> idFunc) {
		super(nameFunc, idFunc);
		this.byId = null;
		this.byName = null;
		this.numericContents = Object2IntMaps.emptyMap();
	}

	@Inject("/")
	public void recalculate() {
		tree.clear();
		idMap.clear();
		List<T> ts = this.contents;
		for (int i = 0; i < ts.size(); i++) {
			index(ts.get(i));
		}
	}

	@Inject("/")
	public void add(T t) {
		this.contents.add(t);
		this.index(t);
	}

	@Inject("/")
	private void index(T t) {
		for (ResourceLocation pos : this.idFunc.apply(t)) {
			this.idMap.computeIfAbsent(pos.getNamespace().toLowerCase(), Helpers.cast(fn)).put(pos.getPath().toLowerCase(), t);
		}
		for (String x : this.nameFunc.apply(t)) {
			this.tree.put(x, t);
		}
	}

	@Inject("/")
	public List<T> search(String key) {
		int i;
		key = key.toLowerCase();
		if ((i = key.indexOf(':')) < 0) {return tree.valueMatches(key, 1000);} else {
			TrieTree<T> tree = idMap.get(key.substring(0, i));
			return tree == null ? Collections.emptyList() : tree.valueMatches(key.substring(i + 1), 1000);
		}
	}
}
