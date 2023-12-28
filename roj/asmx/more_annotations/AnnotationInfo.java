package roj.asmx.more_annotations;

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
public abstract class AnnotationInfo {
	final String owner;
	final MyHashMap<String, Annotation> annotations = new MyHashMap<>();

	AnnotationInfo(String owner) { this.owner = owner; }

	public String owner() { return owner; }
	public String name() { return owner; }
	public String desc() { return "L"+owner+';'; }
	public Map<String, Annotation> annotations() { return annotations; }
	public Set<NodeInfo> children() { return Collections.emptySet(); }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof AnnotationInfo)) return false;

		AnnotationInfo info = (AnnotationInfo) o;

		if (!owner.equals(info.owner)) return false;
		return annotations.equals(info.annotations);
	}

	@Override
	public int hashCode() {
		int result = owner.hashCode();
		result = 31 * result + annotations.hashCode();
		return result;
	}

	public static final class ClassInfo extends AnnotationInfo {
		final MyHashSet<NodeInfo> children = new MyHashSet<>();

		public ClassInfo(String owner) { super(owner); }

		@Override
		public Set<NodeInfo> children() { return children; }
	}

	public static final class NodeInfo extends AnnotationInfo {
		final String name, desc;

		public NodeInfo(String owner, String name, String desc) {
			super(owner);
			this.name = name;
			this.desc = desc;
		}

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