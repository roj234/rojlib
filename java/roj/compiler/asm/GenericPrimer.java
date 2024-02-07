package roj.compiler.asm;

import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.Generic;
import roj.asm.type.GenericSub;
import roj.asm.type.IType;
import roj.asm.util.Attributes;
import roj.compiler.context.CompileUnit;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/9/17 0017 20:37
 */
public class GenericPrimer extends Generic {
	private byte myType;
	// lexer碰到了LSS
	private boolean checkSub;

	public GenericSub toGenericSub() {
		if (extendType != EX_NONE) return null;
		return new GenericSub(owner, children);
	}

	// true: not java type
	public boolean resolveS1(SignaturePrimer s) {
		if (s != null && s.hasTypeParam(owner)) {
			myType = TYPE_PARAMETER_TYPE;
			return true;
		}
		if ("*".equals(owner)) {
			myType = ANY_TYPE;
			return true;
		}
		myType = GENERIC_TYPE;
		return false;
	}

	public boolean isGenericArray() {
		if (array() == 0 || children.isEmpty()) return false;
		for (int i = 0; i < children.size(); i++) {
			IType x = children.get(i);
			if (x.genericType() != ANY_TYPE) return true;
		}
		return false;
	}

	public void resolveS2(CompileUnit file, String kind) {
		// MyHashMap<K,V>.Entry<Z>
		// MyHashMap.Entry<K,V>
		// MyHashMap<K,V>.Entry
		findSubclass:
		if (myType == GENERIC_TYPE && !checkSub) {
			checkSub = true;

			file._resolve(this, kind);

			IClass c = file.resolve(owner);
			if (c == null) break findSubclass;

			List<InnerClasses.InnerClass> list = Attributes.getInnerClasses(c.cp(), c);
			if (list == null) break findSubclass;

			for (InnerClasses.InnerClass ic : list) {
				if (ic.self.equals(owner)) {
					if ((ic.flags & Opcodes.ACC_STATIC) != 0) break findSubclass;
					// I am inner class
				}
			}
		}

		List<IType> types = children;
		for (int i = 0; i < types.size(); i++) {
			// todo
			//file._resolve1(types.get(i), kind);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Generic)) return false;

		Generic generic = (Generic) o;

		if (array() != generic.array()) return false;
		if (extendType != generic.extendType) return false;
		if (!owner.equals(generic.owner)) return false;
		if (sub != null ? !sub.equals(generic.sub) : generic.sub != null) return false;
		return childrenRaw().equals(generic.childrenRaw());
	}

	@Override
	public int hashCode() {
		int result = 0;
		result = 31 * result + owner.hashCode();
		result = 31 * result + (sub != null ? sub.hashCode() : 0);
		result = 31 * result + array();
		result = 31 * result + extendType;
		result = 31 * result + childrenRaw().hashCode();
		return result;
	}
}