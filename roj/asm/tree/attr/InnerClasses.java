package roj.asm.tree.attr;

import roj.asm.Opcodes;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstUTF;
import roj.asm.tree.IClass;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class InnerClasses extends Attribute {
	public static final String NAME = "InnerClasses";

	public InnerClasses() { classes = new SimpleList<>(); }

	public InnerClasses(DynByteBuf r, ConstantPool pool) {
		//** If a class file has a version number that is 51.0 or above, outer_class_info_index must be 0 if inner_name_index is 0.
		int count = r.readUnsignedShort();

		List<InnerClass> classes = this.classes = new SimpleList<>(count);

		while (count-- > 0) {
			String selfName = ((CstClass) pool.get(r)).name().str();
			CstClass outer = (CstClass) pool.get(r);
			// If C is not a member of a class or an interface (that is, if C is a top-level class or interface (JLS §7.6) or a local class (JLS §14.3) or an anonymous class (JLS §15.9.5)), the value of the outer_class_info_index item must be 0.
			String outerName = outer == null ? null : outer.name().str();

			CstUTF nameS = (CstUTF) pool.get(r);
			// If C is anonymous (JLS §15.9.5), the item must be null
			// Otherwise, the item must be a Utf8
			String name = nameS == null ? null : nameS.str();

			classes.add(new InnerClass(selfName, outerName, name, r.readChar()));
		}
	}

	public List<InnerClass> classes;

	@Override
	public boolean isEmpty() { return classes.isEmpty(); }

	@Override
	public String name() { return NAME; }

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		w.putShort(classes.size());
		for (int i = 0; i < classes.size(); i++) {
			InnerClass clazz = classes.get(i);
			w.putShort(pool.getClassId(clazz.self))
			 .putShort(clazz.parent == null ? 0 : pool.getClassId(clazz.parent))
			 .putShort(clazz.name == null ? 0 : pool.getUtfId(clazz.name))
			 .putShort(clazz.flags);
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("InnerClasses: \n");
		for (int i = 0; i < classes.size(); i++) {
			sb.append("         ").append(classes.get(i)).append('\n');
		}
		return sb.deleteCharAt(sb.length() - 1).toString();
	}

	public static class InnerClass {
		@Nonnull
		public String self;
		@Nullable
		public String parent, name;
		public char flags;

		public InnerClass(@Nonnull String self, @Nullable String parent, @Nullable String name, int flags) {
			this.self = self;
			this.parent = parent;
			this.name = name;
			this.flags = (char) flags;
		}

		public static InnerClass innerClass(String name, int flags) {
			int i = name.lastIndexOf('$');
			return new InnerClass(name, name.substring(i), name.substring(i+1), flags);
		}

		public static InnerClass anonymous(String name, int flags) {
			return new InnerClass(name, null, null, flags);
		}

		public static InnerClass reference(String from, IClass referent) {
			int i = referent.name().lastIndexOf('$');
			return new InnerClass(referent.name(), from, referent.name().substring(i+1), referent.modifier());
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			Opcodes.showModifiers(flags, Opcodes.ACC_SHOW_INNERCLASS, sb).append("class ");
			if (parent == null && name == null) {
				sb.append("<anonymous>");
			} else {
				sb.append(parent.substring(parent.lastIndexOf('/')+1)).append('.').append(name);
			}

			if (name == null || name.indexOf('$') >= 0) {
				sb.append(' ').append("(Path: ").append(self).append(')');
			}

			return sb.append(';').toString();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			InnerClass that = (InnerClass) o;

			if (!self.equals(that.self)) return false;
			if (!Objects.equals(parent, that.parent) || !Objects.equals(name, that.name)) {
				throw new AssertionError("State Violation");
			}
			return true;
		}

		@Override
		public int hashCode() {
			return self.hashCode();
		}
	}
}