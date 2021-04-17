package ilib.collect;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class MyImmutableMultimap implements Multimap<String, Object> {
	public MyImmutableMultimap(Multimap<String, Object> map) {
		this.map = map;
	}

	private final Multimap<String, Object> map;

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public boolean containsKey(@Nullable Object o) {
		return map.containsKey(o);
	}

	@Override
	public boolean containsValue(@Nullable Object o) {
		return map.containsValue(o);
	}

	@Override
	public boolean containsEntry(@Nullable Object o, @Nullable Object o1) {
		return map.containsEntry(o, o1);
	}

	@Override
	public boolean put(@Nullable String s, @Nullable Object o) {
		return false;
	}

	@Override
	public boolean remove(@Nullable Object o, @Nullable Object o1) {
		return false;
	}

	@Override
	public boolean putAll(@Nullable String s, @Nonnull Iterable<?> iterable) {
		return false;
	}

	@Override
	public boolean putAll(@Nonnull Multimap<? extends String, ?> multimap) {
		return false;
	}

	@Override
	public Collection<Object> replaceValues(@Nullable String s, @Nonnull Iterable<?> iterable) {
		return null;
	}

	@Override
	public Collection<Object> removeAll(@Nullable Object o) {
		return null;
	}

	@Override
	public void clear() {

	}

	@Override
	public Collection<Object> get(@Nullable String s) {
		return map.get(s);
	}

	@Override
	public Set<String> keySet() {
		return map.keySet();
	}

	@Override
	public Multiset<String> keys() {
		return map.keys();
	}

	@Override
	public Collection<Object> values() {
		return map.values();
	}

	@Override
	public Collection<Map.Entry<String, Object>> entries() {
		return map.entries();
	}

	@Override
	public Map<String, Collection<Object>> asMap() {
		return map.asMap();
	}
}
