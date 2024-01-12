package roj.compiler.ast.expr;

import roj.asm.type.IType;
import roj.collect.MyHashMap;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ResolveException;

import java.util.Map;

/**
 * @author Roj234
 * @since 2024/1/23 0023 8:31
 */
final class NamedParamList implements ExprNode {
	final MyHashMap<String, ExprNode> map = new MyHashMap<>();

	public boolean add(String name, ExprNode val) { return map.putIfAbsent(name, val) == null; }

	@Override
	public String toString() { return map.toString(); }

	@Override
	public ExprNode resolve(CompileContext ctx) throws ResolveException {
		for (Map.Entry<String, ExprNode> entry : map.entrySet()) {
			entry.setValue(entry.getValue().resolve(ctx));
		}
		return this;
	}

	// 这个类不会在resolve阶段后存在
	@Override
	public IType type() { return null; }
	@Override
	public void write(MethodWriter cw, boolean noRet) { throw new UnsupportedOperationException(); }

	@Override
	public boolean equalTo(Object o) { return false; }

	public Map<String, IType> getExtraParams() {
		MyHashMap<String, IType> params = new MyHashMap<>(map.size());
		for (Map.Entry<String, ExprNode> entry : map.entrySet()) {
			params.put(entry.getKey(), entry.getValue().type());
		}
		return params;
	}
}