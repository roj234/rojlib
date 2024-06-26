package roj.asmx;

import roj.asm.Opcodes;
import roj.asm.tree.AccessData;
import roj.asm.tree.Attributed;
import roj.asm.tree.anno.Annotation;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.text.CharList;

import java.util.Map;
import java.util.Set;

/**
 * @author Roj234
 * @since 2023/12/26 0026 12:47
 */
public abstract sealed class AnnotatedElement permits AnnotatedElement.Type, AnnotatedElement.Node {
	final MyHashMap<String, Annotation> annotations = new MyHashMap<>();

	public abstract String owner();
	public String name() { return owner(); }
	public String desc() { return "L"+owner()+';'; }
	public Map<String, Annotation> annotations() { return annotations; }

	public boolean isLeaf() { return false; }
	public Type parent() { return null; }
	public Attributed node() { return null; }

	public static final class Type extends AnnotatedElement {
		final AccessData owner;
		final MyHashSet<Node> children = new MyHashSet<>();

		public Type(AccessData owner) { this.owner = owner; }

		public String owner() { return owner.name; }
		public Set<Node> children() { return children; }
		public Type parent() { return this; }
		public AccessData node() { return owner; }

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Type info)) return false;
			return owner == info.owner;
		}

		@Override
		public int hashCode() { return 2 + owner.name.hashCode(); }

		@Override
		public String toString() {
			CharList sb = IOUtil.getSharedCharBuf().append("[AnnotatedType\n");
			for (Annotation value : annotations.values()) sb.append(value).append('\n');
			return sb.append(Opcodes.showModifiers(owner.acc, Opcodes.ACC_SHOW_CLASS)).append(owner.name.replace('/', '.')).append(']').toString();
		}
	}

	public static final class Node extends AnnotatedElement {
		private final Type parent;
		private final AccessData.MOF node;

		public Node(Type parent, AccessData.MOF node) {
			this.parent = parent;
			this.node = node;
		}

		public String owner() { return parent.owner(); }
		public String name() { return node.name(); }
		public String desc() { return node.rawDesc(); }

		public boolean isLeaf() { return true; }
		public Type parent() { return parent; }
		public AccessData.MOF node() { return node; }

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Node info = (Node) o;
			return parent == info.parent && node == info.node;
		}

		@Override
		public int hashCode() {
			int result = 1;
			result = 31 * result + node.name().hashCode();
			result = 31 * result + node.rawDesc().hashCode();
			return result;
		}

		@Override
		public String toString() {
			CharList sb = IOUtil.getSharedCharBuf().append("[AnnotatedNode\n");
			for (Annotation value : annotations.values()) sb.append(value).append('\n');
			return sb.append(Opcodes.showModifiers(node.acc, Opcodes.ACC_SHOW_METHOD)).append(owner().replace('/', '.')).append('.').append(name()).append(' ').append(desc()).append(']').toString();
		}
	}
}