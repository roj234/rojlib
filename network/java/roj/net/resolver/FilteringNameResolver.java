package roj.net.resolver;

import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/9/1 4:04
 */
public abstract class FilteringNameResolver implements NameResolver {
	protected NameResolver parent;

	public FilteringNameResolver(NameResolver parent) {this.parent = parent;}

	@Override
	public List<InetAddress> lookup(String hostname) {
		var addresses = find(hostname);
		if (addresses != null) return addresses;

		if (parent != null) return parent.lookup(hostname);
		return null;
	}

	@Nullable protected List<InetAddress> find(String hostname) {return null;}
}
