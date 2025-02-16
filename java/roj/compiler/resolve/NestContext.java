package roj.compiler.resolve;

import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.asm.insn.CodeWriter;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.LocalVariable;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/6/11 0011 20:02
 */
public final class NestContext {
	private final ToIntMap<Object> fieldIds = new ToIntMap<>();

	private final String nestType;
	private final CompileUnit self;
	private final Map<String, Variable> variables;

	private final CodeWriter init;
	private final List<ExprNode> initArgs;
	private int initSize;


	public static NestContext anonymousClass(LocalContext ctx, CompileUnit self, CodeWriter constructor, List<ExprNode> args) {
		return new NestContext(ctx.file.name(), self, ctx.variables, constructor, args);
	}
	public static NestContext notFinalClass(LocalContext ctx, CompileUnit self, CodeWriter constructor, List<ExprNode> args) {
		return new NestContext(ctx.file.name(), self, Collections.emptyMap(), constructor, args);
	}
	public static NestContext lambda(LocalContext ctx, CompileUnit self, CodeWriter constructor, List<ExprNode> args) {
		return new NestContext(ctx.file.name(), self, Collections.emptyMap(), constructor, args);
	}

	public NestContext(String nestType, CompileUnit that, Map<String, Variable> vars, CodeWriter constructor, List<ExprNode> initArgs) {
		this.nestType = nestType;
		self = that;
		variables = vars;

		this.init = constructor;
		this.initSize = constructor.getLocalSize();
		this.initArgs = initArgs;
	}

	public String nestType() {return nestType;}
	public FieldNode nestRef() {
		int fid = fieldIds.getOrDefault(this, -1);
		if (fid < 0) {
			Type type = Type.klass(nestType);
			fid = self.newField(Opcodes.ACC_SYNTHETIC|Opcodes.ACC_FINAL, "$this", type);
			fieldIds.putInt(this, fid);

			addConstr(new ExprNode() {
				@Override
				public String toString() {return "Nest<this>";}
				@Override
				public IType type() {return type;}
				@Override
				public void write(MethodWriter cw, boolean noRet) {
					var ctx = LocalContext.get();
					ctx.thisUsed = true;
					cw.vars(Opcodes.ALOAD, ctx.thisSlot);
				}
			}, fid);
		}
		return self.fields.get(fid);
	}

	public static LocalContext.Import tryFieldRef(SimpleList<NestContext> list, LocalContext ctx, String name) {
		for (int i = list.size()-1; i >= 0; i--) {
			var ec = list.get(i);
			var v = ec.variables.get(name);
			if (v != null) {
				var chain = new FieldNode[list.size()-i];

				int k = 0;
				for (int j = list.size()-1; j > i; j--)
					chain[k++] = list.get(j).nestRef();
				chain[k] = ec.self.fields.get(ec.getVariable(v));

				// must use This(), will reference topmost fieldIds
				return new LocalContext.Import(ExprParser.fieldChain(ctx.ep.This(), list.getLast().self, null, true, chain));
			}
		}

		return null;
	}
	private int getVariable(Variable v) {
		int fid = fieldIds.getOrDefault(v, -1);
		if (fid < 0) {
			fid = self.newField(Opcodes.ACC_SYNTHETIC|Opcodes.ACC_FINAL, "$arg$"+v.name, v.type.rawType().toDesc());
			fieldIds.put(v, fid);

			v.refByNest = true;
			// to invoke constructor
			addConstr(new LocalVariable(v), fid);
		}
		return fid;
	}

	public static LocalContext.Import tryMethodRef(SimpleList<NestContext> list, LocalContext ctx, String name) {
		for (int i = list.size()-1; i >= 0; i--) {
			var ec = list.get(i);
			var v = ec.self.getMethod(name);
			if (v >= 0) {
				var chain = new FieldNode[list.size()-i-1];

				int k = 0;
				for (int j = list.size()-1; j > i; j--)
					chain[k++] = list.get(j).nestRef();

				// must use This(), will reference topmost fieldIds
				return new LocalContext.Import(ec.self, name, ExprParser.fieldChain(ctx.ep.This(), list.getLast().self, null, true, chain));
			}
		}

		return null;
	}

	private void addConstr(ExprNode invoke, int fid) {
		var ctx = LocalContext.get();
		ctx.thisUsed = true;

		var type = invoke.type().rawType();
		initArgs.add(invoke);
		init.mn.parameters().add(type);

		init.one(Opcodes.ALOAD_0);
		init.varLoad(type, initSize);
		init.field(Opcodes.PUTFIELD, self, fid);
		int length = type.length();
		init.visitSizeMax(length+1, initSize += length);
	}
}