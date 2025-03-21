package roj.compiler.resolve;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.LocalVariable;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.ACC_STATIC;

/**
 * @author Roj234
 * @since 2024/6/11 0011 20:02
 */
public sealed abstract class NestContext {
	// 外部类名称
	final String host;
	// 构造时的参数列表
	final List<ExprNode> hostArguments;
	// 外部类此时可用的变量
	final Map<String, Variable> hostVariables;

	NestContext(String host, List<ExprNode> hostArguments, Map<String, Variable> hostVariables) {
		this.host = host;
		this.hostArguments = hostArguments;
		this.hostVariables = hostVariables;
	}

	public MethodWriter constructor() {return null;}

	public String nestHost() {return host;}
	public @Nullable FieldNode nestHostRef() {return null;}

	public abstract LocalContext.Import resolveField(LocalContext ctx, SimpleList<NestContext> nestHost, int i, String name);
	public LocalContext.Import resolveMethod(LocalContext ctx, SimpleList<NestContext> nestHost, int i, String name) {return null;}

	public static NestContext anonymous(LocalContext ctx, CompileUnit nest, MethodWriter nestConstructor, List<ExprNode> hostArgsLoad) {return new NestContext.InnerClass(ctx.file.name(), hostArgsLoad, ctx.variables, nest, nestConstructor);}
	public static NestContext innerClass(LocalContext ctx, String host, CompileUnit nest) {return new NestContext.InnerClass(host, nest);}
	public static NestContext lambda(LocalContext ctx, List<Type> nestArgs, List<Type> hostArgs, List<ExprNode> hostArgsLoad) {return new NestContext.Lambda(ctx.variables, nestArgs, hostArgs, hostArgsLoad);}

	public static final class InnerClass extends NestContext {
		public static final String FIELD_HOST_REF = "$host";

		// 内部类自身
		private final ClassNode nest;
		// 内部构造函数
		private final MethodWriter constructor;
		private int localSize;
		// 内部字段
		private final ToIntMap<Object> fieldIds = new ToIntMap<>();

		InnerClass(String host, List<ExprNode> hostArgs, Map<String, Variable> hostVars, CompileUnit nest, MethodWriter nestConstructor) {
			super(host, hostArgs, hostVars);
			this.nest = nest;
			this.constructor = nestConstructor;
			this.localSize = nestConstructor.getLocalSize();
		}

		InnerClass(String host, CompileUnit nest) {
			super(host, null, Collections.emptyMap());
			this.nest = nest;
			this.constructor = null;
			this.localSize = 0;

			fieldIds.putInt(this, nest.getField(FIELD_HOST_REF));
		}

		public MethodWriter constructor() {return constructor;}

		public String nestHost() {return host;}
		public FieldNode nestHostRef() {
			int fid = fieldIds.getOrDefault(this, -1);
			if (fid < 0) {
				Type type = Type.klass(host);
				fid = nest.newField(Opcodes.ACC_SYNTHETIC|Opcodes.ACC_FINAL, FIELD_HOST_REF, type);
				fieldIds.putInt(this, fid);

				copyIn(new ExprNode() {
					@Override public String toString() {return "this<"+type+">";}
					@Override public IType type() {return type;}
					@Override public void write(MethodWriter cw, boolean noRet) {
						var ctx = LocalContext.get();
						ctx.thisUsed = true;
						cw.vars(Opcodes.ALOAD, ctx.thisSlot);
					}
				}, fid);
			}
			return nest.fields.get(fid);
		}
		private int getVariable(Variable v) {
			int fid = fieldIds.getOrDefault(v, -1);
			if (fid < 0) {
				fid = nest.newField(Opcodes.ACC_SYNTHETIC|Opcodes.ACC_FINAL, "arg$"+v.name, v.type.rawType().toDesc());
				fieldIds.put(v, fid);

				v.refByNest = true;
				// to invoke constructor
				copyIn(new LocalVariable(v), fid);
			}
			return fid;
		}
		private void copyIn(ExprNode external, int fid) {
			var ctx = LocalContext.get();
			ctx.thisUsed = true;

			hostArguments.add(external);

			var type = external.type().rawType();
			constructor.mn.parameters().add(type);

			constructor.one(Opcodes.ALOAD_0);
			constructor.varLoad(type, localSize);
			constructor.field(Opcodes.PUTFIELD, nest, fid);
			int length = type.length();
			constructor.visitSizeMax(length+1, localSize += length);
		}

		public LocalContext.Import resolveField(LocalContext ctx, SimpleList<NestContext> nestHost, int i, String name) {
			int fid;
			var hostVar = hostVariables.get(name);
			ClassNode owner;
			if (hostVar == null) {
				owner = ctx.classes.getClassInfo(host);
				fid = owner.getField(name);
				if (fid < 0) return null;
				i--;
			} else {
				owner = nest;
				fid = getVariable(hostVar);
			}

			var chain = new FieldNode[nestHost.size()-i];

			int k = 0;
			for (int j = nestHost.size()-1; j > i; j--) {
				FieldNode ref = nestHost.get(j).nestHostRef();
				if (ref != null) chain[k++] = ref;
			}

			FieldNode fieldNode = owner.fields.get(fid);
			chain[k++] = fieldNode;

			if (k < chain.length) chain = Arrays.copyOf(chain, k);

			return makeChain(ctx, nestHost, null, null, owner == nest || (fieldNode.modifier&Opcodes.ACC_FINAL) != 0, chain);
		}
		public LocalContext.Import resolveMethod(LocalContext ctx, SimpleList<NestContext> nestHost, int i, String name) {
			var host = ctx.classes.getClassInfo(this.host);
			var v = host.getMethod(name);
			if (v >= 0) {
				i--;
				var chain = new FieldNode[nestHost.size()-i];

				int k = 0;
				for (int j = nestHost.size()-1; j > i; j--) {
					FieldNode ref = nestHost.get(j).nestHostRef();
					if (ref != null) chain[k++] = ref;
				}
				if (k < chain.length) chain = Arrays.copyOf(chain, k);

				return makeChain(ctx, nestHost, host, name, true, chain);
			}
			return null;
		}

		@NotNull
		private static LocalContext.Import makeChain(LocalContext ctx, SimpleList<NestContext> nestHost, ClassNode owner, String name, boolean isFinal, FieldNode... chain) {
			for (int i = nestHost.size() - 1; i >= 0; i--) {
				if (nestHost.get(i) instanceof InnerClass ic) {
					// must use This(), will reference topmost fieldIds
					return new LocalContext.Import(owner, name, ExprParser.fieldChain(ctx.ep.This().resolve(ctx), ic.nest, null, isFinal, chain));
				}
			}
			throw new AssertionError();
		}
	}
	public static final class Lambda extends NestContext {
		private final Map<Variable, LocalVariable> fieldIds;
		private final List<Type> nestArgs, hostArgs;
		private int localSize;

		public Lambda(Map<String, Variable> hostVars, List<Type> nestArgs, List<Type> hostArgs, List<ExprNode> hostArgsLoad) {
			super("OIIAOIIA", hostArgsLoad, hostVars);

			this.fieldIds = new LinkedMyHashMap<>();
			this.nestArgs = nestArgs; // Must be a SubList
			this.hostArgs = hostArgs;
			this.localSize = 0x10000;
		}

		@Override
		public LocalContext.Import resolveField(LocalContext ctx, SimpleList<NestContext> nestHost, int i, String name) {
			var hostVar = hostVariables.get(name);
			if (hostVar == null) return null;

			var nestVar = fieldIds.computeIfAbsent(hostVar, hostVariable -> {
				hostVariable.refByNest = true;

				Type rawType = hostVariable.type.rawType();

				nestArgs.add(rawType);
				hostArgs.add(rawType);
				hostArguments.add(new LocalVariable(hostVariable));

				Variable nestVariable = ctx.bp.newVar(name, hostVariable.type);
				nestVariable.pos = 0;
				nestVariable.hasValue = true;
				nestVariable.isFinal = true;
				nestVariable.slot = localSize;
				localSize += rawType.length();

				return new LocalVariable(nestVariable);
			});
			return LocalContext.Import.replace(nestVar);
		}

		public void processVariableIndex(LocalContext ctx, MyHashMap<String, Variable> variables) {
			if (!ctx.thisUsed) {
				ctx.method.modifier |= ACC_STATIC;
				for (var value : variables.values()) value.slot--;
			}

			for (Variable value : variables.values()) {
				if (value.slot >= 0x10000) value.slot -= 0x10000;
				else value.slot += localSize;
			}
		}
	}
}