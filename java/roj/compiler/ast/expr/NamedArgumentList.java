package roj.compiler.ast.expr;

import roj.asm.type.IType;
import roj.collect.MyHashMap;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.resolve.ResolveException;

import java.util.Map;

/**
 * @author Roj234
 * @since 2024/1/23 8:31
 */
final class NamedArgumentList extends Expr {
	final MyHashMap<String, Expr> map = new MyHashMap<>();

	public boolean add(String name, Expr val) { return map.putIfAbsent(name, val) == null; }

	@Override
	public String toString() { return map.toString(); }

	@Override
	public Expr resolve(LocalContext ctx) throws ResolveException {
		for (Map.Entry<String, Expr> entry : map.entrySet()) {
			entry.setValue(entry.getValue().resolve(ctx));
		}
		return this;
	}

	// 这个类不会在resolve阶段后存在
	@Override public IType type() { throw new UnsupportedOperationException(); }
	@Override public void write(MethodWriter cw, boolean noRet) { throw new UnsupportedOperationException(); }

	Map<String, IType> resolve() {
		MyHashMap<String, IType> params = new MyHashMap<>(map.size());
		for (Map.Entry<String, Expr> entry : map.entrySet()) {
			params.put(entry.getKey(), entry.getValue().type());
		}
		return params;
	}
}