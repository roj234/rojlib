package roj.util;

public class NamespaceKey implements Comparable<NamespaceKey> {
	protected final String namespace, path;
	private String combined;

	public static NamespaceKey of(String loc) {
		return new NamespaceKey(loc);
	}

	public static NamespaceKey of(String ns, String path) {
		return new NamespaceKey(ns, path);
	}

	public NamespaceKey(String location) {
		int i = location.indexOf(':');
		combined = location;
		if (i >= 0) {
			namespace = location.substring(0, i);
			path = location.substring(i + 1);
		} else {
			namespace = "null";
			path = location;
		}
	}

	public NamespaceKey(String ns, String path) {
		if (ns == null || ns.equals("null")) throw new NullPointerException("ns");

		this.namespace = ns;
		this.path = path;
	}

	public String getNamespace() {
		return namespace;
	}

	public String getPath() {
		return path;
	}

	public String toString() {
		return combined == null ? combined = namespace + ':' + path : combined;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		NamespaceKey key = (NamespaceKey) o;
		return namespace.equals(key.namespace) && path.equals(key.path);
	}

	@Override
	public int hashCode() {
		return 31 * namespace.hashCode() * 31 * path.hashCode();
	}

	@Override
	public int compareTo(NamespaceKey o) {
		int i = namespace.compareTo(o.namespace);
		if (i == 0) return path.compareTo(o.path);
		return i;
	}
}
