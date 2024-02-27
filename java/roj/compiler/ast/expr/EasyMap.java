package roj.compiler.ast.expr;

import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.Hasher;
import roj.collect.MyHashMap;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import java.util.Map;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/1/23 0023 8:31
 */
final class EasyMap extends ExprNode {
	MyHashMap<ExprNode, ExprNode> map = MyHashMap.withCustomHasher(Hasher.identity());
	private Generic type;

	@Override
	public String toString() { return "<EasyMap> "+map; }

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		if (type != null) return this;

		IType key = null, val = null;
		boolean allIsConstant = true;
		MyHashMap<ExprNode, ExprNode> map1 = MyHashMap.withCustomHasher(Hasher.identity());
		map1.ensureCapacity(map.size());

		for (Map.Entry<ExprNode, ExprNode> entry : map.entrySet()) {
			ExprNode kn = entry.getKey().resolve(ctx);
			ExprNode vn = entry.getValue().resolve(ctx);
			map1.put(kn, vn);

			if (!kn.isConstant() || !vn.isConstant()) allIsConstant = false;

			if (key == null) key = kn.type();
			else key = ctx.getCommonParent(key, kn.type());
			if (val == null) val = vn.type();
			else val = ctx.getCommonParent(val, vn.type());
		}

		map = map1;
		type = new Generic("java/util/Map", 0, Generic.EX_NONE);

		Type wrapper = TypeCast.getWrapper(key);
		type.addChild(wrapper == null ? key : wrapper);
		wrapper = TypeCast.getWrapper(key);
		type.addChild(wrapper == null ? val : wrapper);

		return this;
	}

	@Override
	public IType type() { return type; }

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		cw.clazz(NEW, "roj/collect/MyHashMap");
		cw.one(DUP);
		cw.ldc(map.size());
		cw.invoke(INVOKESPECIAL, "roj/collect/MyHashMap", "<init>", "(I)V");
		var lc = LocalContext.get();
		for (Map.Entry<ExprNode, ExprNode> entry : map.entrySet()) {
			cw.one(DUP);

			entry.getKey().writeDyn(cw, lc.castTo(entry.getKey().type(), LocalContext.OBJECT_TYPE, 0));
			entry.getValue().writeDyn(cw, lc.castTo(entry.getValue().type(), LocalContext.OBJECT_TYPE, 0));

			cw.invoke(INVOKEVIRTUAL, "roj/collect/MyHashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
			cw.one(POP);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof EasyMap easyMap)) return false;

		return map.equals(easyMap.map);
	}

	@Override
	public int hashCode() { return map.hashCode(); }
}