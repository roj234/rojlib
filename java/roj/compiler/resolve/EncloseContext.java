package roj.compiler.resolve;

import roj.asm.Opcodes;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.type.Type;
import roj.asm.visitor.CodeWriter;
import roj.collect.MyHashMap;
import roj.collect.ToIntMap;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.LocalVariable;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;

import java.util.List;
import java.util.Map;

/**
 * Only for enclosing class
 * lambda只要喂参数用aload就行
 * @author Roj234
 * @since 2024/6/11 0011 20:02
 */
public class EncloseContext {
	private CompileUnit nest;
	private Map<String, Variable> variables;

	private CodeWriter init;

	private List<ExprNode> inputArgs;

	private ToIntMap<Object> loaded = new ToIntMap<>();

	private MethodNode mn;
	private int localSize;
	private ExprNode _this;

	public static EncloseContext anonymousClass(LocalContext ctx, CodeWriter constructor, List<ExprNode> args) {
		return new EncloseContext(ctx.file, ctx.variables, constructor, args);
	}

	public EncloseContext(CompileUnit that, MyHashMap<String, Variable> vars, CodeWriter constructor, List<ExprNode> inputArgs) {
		nest = that;
		variables = new MyHashMap<>(vars);
		_this = that.lc().ep.This();
		this.init = constructor;
		mn = constructor.mn;
		this.inputArgs = inputArgs;
		localSize = constructor.getLocalSize();
	}

	public String thisType() {return nest.name;}

	public FieldNode thisRef() {
		LocalContext ctx = LocalContext.get();
		var anonySelf = ctx.file;

		int fid = loaded.getOrDefault(this, -1);
		if (fid < 0) {
			Type type = new Type(nest.name);
			fid = anonySelf.newField(Opcodes.ACC_SYNTHETIC|Opcodes.ACC_FINAL, "$this", type);

			loaded.putInt(this, fid);

			// to invoke constructor
			inputArgs.add(_this);
			addConstructorArg(type, anonySelf, fid);
		}
		return anonySelf.fields.get(fid);
	}

	public LocalContext.Import tryFieldRef(LocalContext ctx, String name) {
		Variable v = variables.get(name);
		if (v == null) return null;

		var anonySelf = ctx.file;
		int fid = loaded.getOrDefault(this, -1);
		if (fid < 0) {
			Type type = v.type.rawType();

			fid = anonySelf.newField(Opcodes.ACC_SYNTHETIC|Opcodes.ACC_FINAL, "$arg$"+name, type.toDesc());

			// to invoke constructor
			inputArgs.add(new LocalVariable(v));
			addConstructorArg(type, anonySelf, fid);
		}

		return new LocalContext.Import(anonySelf, "$arg$"+name, ctx.ep.This());
	}

	private void addConstructorArg(Type type, CompileUnit anonySelf, int fid) {
		// constructor args
		init.one(Opcodes.ALOAD_0);
		init.varLoad(type, localSize);
		init.field(Opcodes.PUTFIELD, anonySelf, fid);
		init.visitSizeMax(2, localSize += type.length());
		mn.parameters().add(type);
	}

	public LocalContext.Import tryMethodRef(LocalContext context, String name) {
		return null;
	}
}