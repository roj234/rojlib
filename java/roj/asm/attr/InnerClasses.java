package roj.asm.attr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.ClassDefinition;
import roj.asm.Opcodes;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstUTF;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

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

		List<Item> classes = this.classes = new SimpleList<>(count);

		while (count-- > 0) {
			String selfName = ((CstClass) pool.get(r)).name().str();
			CstClass outer = (CstClass) pool.getNullable(r);
			// If C is not a member of a class or an interface (that is, if C is a top-level class or interface (JLS §7.6) or a local class (JLS §14.3) or an anonymous class (JLS §15.9.5)), the value of the outer_class_info_index item must be 0.
			String outerName = outer == null ? null : outer.name().str();

			CstUTF nameS = (CstUTF) pool.getNullable(r);
			// If C is anonymous (JLS §15.9.5), the item must be null
			// Otherwise, the item must be a Utf8
			String name = nameS == null ? null : nameS.str();

			classes.add(new Item(selfName, outerName, name, r.readChar()));
		}
	}

	public List<Item> classes;

	@Override
	public boolean writeIgnore() { return classes.isEmpty(); }

	@Override
	public String name() { return NAME; }

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		w.putShort(classes.size());
		for (int i = 0; i < classes.size(); i++) {
			Item clazz = classes.get(i);
			w.putShort(pool.getClassId(clazz.self))
			 .putShort(clazz.parent == null ? 0 : pool.getClassId(clazz.parent))
			 .putShort(clazz.name == null ? 0 : pool.getUtfId(clazz.name))
			 .putShort(clazz.modifier);
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("InnerClasses: \n");
		for (int i = 0; i < classes.size(); i++) {
			sb.append("         ").append(classes.get(i)).append('\n');
		}
		return sb.deleteCharAt(sb.length() - 1).toString();
	}

	public static class Item {
		@NotNull
		public String self;
		@Nullable
		public String parent, name;
		public char modifier;

		public Item(@NotNull String self, @Nullable String parent, @Nullable String name, int modifier) {
			this.self = self;
			this.parent = parent;
			this.name = name;
			this.modifier = (char) modifier;
		}

		public static Item innerClass(String name, int flags) {
			int i = name.lastIndexOf('$');
			return new Item(name, name.substring(0, i), name.substring(i+1), flags);
		}

		public static Item anonymous(String name, int flags) {
			return new Item(name, null, null, flags);
		}

		public static Item reference(String from, ClassDefinition referent) {
			int i = referent.name().lastIndexOf('$');
			return new Item(referent.name(), from, referent.name().substring(i+1), referent.modifier());
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			Opcodes.showModifiers(modifier, Opcodes.ACC_SHOW_INNERCLASS, sb).append("class ");
			if (parent == null && name == null) {
				sb.append("<anonymous>");
			} else {
				sb.append(parent == null ? "null" : parent.substring(parent.lastIndexOf('/')+1)).append('.').append(name);
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

			Item that = (Item) o;

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