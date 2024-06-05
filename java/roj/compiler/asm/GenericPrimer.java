package roj.compiler.asm;

import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.*;
import roj.asm.util.Attributes;
import roj.compiler.context.LocalContext;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/9/17 0017 20:37
 */
public class GenericPrimer extends Generic {
	public IType resolve(SignaturePrimer s) {
		if (s != null && s.hasTypeParam(owner)) return new TypeParam(owner);
		if ("*".equals(owner)) return Signature.any();
		return isRealGeneric() ? this : rawType();
	}

	public GenericSub toGenericSub() {
		if (extendType != EX_NONE) return null;
		return new GenericSub(owner, children);
	}

	public boolean isGenericArray() {
		if (array() == 0 || children.isEmpty()) return false;
		for (int i = 0; i < children.size(); i++) {
			IType x = children.get(i);
			if (x.genericType() != ANY_TYPE) return true;
		}
		return false;
	}

	public void initS2(LocalContext ctx) {
		// MyHashMap<K,V>.Entry<Z>
		// MyHashMap.Entry<K,V>
		// MyHashMap<K,V>.Entry.SomeClass<Z>
		// class G1<T> { class G2 { class G3<T2> {} } }
		findSubclass:
		{
			IClass c = ctx.resolveType(owner);
			if (c == null) break findSubclass;

			List<InnerClasses.InnerClass> list = Attributes.getInnerClasses(c.cp(), c);
			if (list == null) break findSubclass;

			for (InnerClasses.InnerClass ic : list) {
				if (ic.self.equals(owner)) {
					if ((ic.flags & Opcodes.ACC_STATIC) != 0) break findSubclass;

					// I am inner class
					// todo 结构是什么样的来着？
					// G1<T>.G2.G3<T2> ?
				}
			}
		}

		List<IType> types = children;
		for (int i = 0; i < types.size(); i++) {
			if (types.get(i) instanceof GenericPrimer gp) gp.initS2(ctx);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Generic generic)) return false;

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