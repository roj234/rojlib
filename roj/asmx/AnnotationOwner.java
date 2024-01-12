package roj.asmx;

import roj.asm.tree.anno.Annotation;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Roj234
 * @since 2023/12/26 0026 12:47
 */
public abstract sealed class AnnotationOwner permits AnnotationOwner.ClassInfo, AnnotationOwner.NodeInfo {
	private final String owner;
	final MyHashMap<String, Annotation> annotations = new MyHashMap<>();

	AnnotationOwner(String owner) { this.owner = owner; }

	public String owner() { return owner; }
	public String name() { return owner; }
	public String desc() { return "L"+owner+';'; }
	public Map<String, Annotation> annotations() { return annotations; }
	public Set<NodeInfo> children() { return Collections.emptySet(); }
	public boolean isLeaf() { return false; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof AnnotationOwner info)) return false;

		if (!owner.equals(info.owner)) return false;
		return annotations.equals(info.annotations);
	}

	@Override
	public int hashCode() {
		int result = owner.hashCode();
		result = 31 * result + annotations.hashCode();
		return result;
	}

	public static final class ClassInfo extends AnnotationOwner {
		final MyHashSet<NodeInfo> children = new MyHashSet<>();

		public ClassInfo(String owner) { super(owner); }

		@Override
		public Set<NodeInfo> children() { return children; }
	}

	public static final class NodeInfo extends AnnotationOwner {
		private final String name, desc;

		public NodeInfo(String owner, String name, String desc) {
			super(owner);
			this.name = name;
			this.desc = desc;
		}

		@Override
		public String name() { return name; }
		@Override
		public String desc() { return desc; }
		@Override
		public boolean isLeaf() { return true; }

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			if (!super.equals(o)) return false;

			NodeInfo info = (NodeInfo) o;

			if (!name.equals(info.name)) return false;
			return desc.equals(info.desc);
		}

		@Override
		public int hashCode() {
			int result = super.hashCode();
			result = 31 * result + name.hashCode();
			result = 31 * result + desc.hashCode();
			return result;
		}
	}
}