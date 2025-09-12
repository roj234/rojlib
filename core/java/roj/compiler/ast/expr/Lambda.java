package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassDefinition;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.cp.CstClass;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.JavaTokenizer;
import roj.compiler.api.Compiler;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.LambdaCall;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.ParseTask;
import roj.compiler.ast.VariableDeclare;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.NestContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.ParseException;
import roj.text.TextUtil;
import roj.util.Helpers;
import roj.util.function.Flow;

import java.util.Collections;
import java.util.List;

/**
 * Lambda：方法参数或Assign的目标
 * @author Roj234
 * @since 2024/1/23 11:32
 */
public final class Lambda extends Expr {
	public static final int ARGC_UNKNOWN = 0x10000;
	public static final IType ARGC_UNKNOWN_TYPE = untypedLambda(ARGC_UNKNOWN);

	private static final Asterisk[] untypedLambda = new Asterisk[10];
	static {
		for (int i = 0; i < 10; i++) untypedLambda[i] = untypedLambda(i);
	}
	public static int getLambdaArgc(IType lambdaType) {
		String name;
		if (lambdaType.genericType() != IType.ASTERISK_TYPE || !(name = lambdaType.toString()).startsWith("lambdaArg:[")) return -1;
		return name.length() == 13 ? name.charAt(11) - '0' : Integer.parseInt(name.substring(11, name.length()-1));
	}
	public static List<IType> getLambdaArgs(IType lambdaType) {
		return ((Asterisk) lambdaType).getTraits();
	}

	/**
	 * lambda类型不是给TypeCast用的！
	 * @see roj.compiler.resolve.Inferrer#cast(IType, IType)
	 */
	public static Asterisk typedLambda(List<IType> arguments) {return new Asterisk("lambda.arg:["+arguments.size()+"]", arguments);}
	public static Asterisk untypedLambda(int argc) {return new Asterisk("lambda.arg:["+argc+"]", null);}
	public static IType typeOf(List<VariableDeclare> args) {
		var argc = args.size();
		if (argc == 0) return untypedLambda[0];

		var untyped = args.get(0).type == null;
		if (untyped) return argc < 10 ? untypedLambda[argc] : untypedLambda(argc);

		return typedLambda(Flow.of(args).map(vd -> vd.type).toList());
	}

	private List<VariableDeclare> args;
	private MethodNode impl;
	private ParseTask task;

	private Expr methodRef;
	private String methodName;

	public MethodNode getImpl() {return impl;}

	// args -> {...}
	public Lambda(List<VariableDeclare> args, MethodNode impl, ParseTask task) {
		this.args = args;
		this.impl = impl;
		this.task = task;
	}
	// parent::methodRef
	public Lambda(Expr methodRef, String methodName) {
		this.args = Collections.emptyList();
		this.methodRef = methodRef;
		this.methodName = methodName;
	}

	@Override
	public String toString() {return methodRef == null
			? (args.size() == 1 && args.get(0).type == null
				? args.get(0)
				: "("+TextUtil.join(args, ", ")+")")+" -> "+impl
			: methodRef+"::"+methodName;}

	@Override
	public IType type() {
		return methodRef != null && args == Collections.EMPTY_LIST
				? ARGC_UNKNOWN_TYPE
				: typeOf(Helpers.cast(args));
	}
	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException {
		if (methodRef != null) {
			if (methodRef instanceof MemberAccess d) {
				methodRef = null;
				Expr next = d.resolveEx(ctx, n1 -> {
					var n = (ClassDefinition) n1;
					methodRef = constant(Type.klass(n.name()), new CstClass(n.name()));
				}, null);
				if (methodRef == null) methodRef = next;
			} else {
				methodRef = methodRef.resolve(ctx);
			}

			var data = ctx.resolve(methodRef.type());
			int mid = -1;
			var methods = data.methods();
			for (int i = 0; i < methods.size(); i++) {
				var method = methods.get(i);
				if (methodName.equals(method.name())) {
					if (mid >= 0) {
						//ctx.report(this, Kind.WARNING, "lambda unfriendly overloading");
						args = Collections.emptyList();
						break;
					}
					mid = i;
					args = Flow.of(method.parameters()).map(t -> new VariableDeclare(t, "<anonymous>")).toList();
				}
			}
		}
		return this;
	}

	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		var ctx = CompileContext.get();
		if (cast.getType1() == null) {
			ctx.report(this, Kind.ERROR, "lambda.untyped");
			return;
		}

		IType lambda目标类型 = cast.getType1();
		var lambda接口 = ctx.compiler.resolve(lambda目标类型.owner());

		var resolveHelper = ctx.compiler.link(lambda接口);
		int lambda转换方式 = resolveHelper.getLambdaType();
		var lambda方法 = resolveHelper.getLambdaMethod();
		LambdaCall generator = LambdaCall.INVOKE_DYNAMIC;
		switch (lambda转换方式) {
			default -> {
				ctx.report(this, Kind.ERROR, "lambda.unsupported."+lambda转换方式);
				return;
			}
			case 1 -> {
				if (ctx.compiler.getMaximumBinaryCompatibility() < Compiler.JAVA_8)
					generator = LambdaCall.ANONYMOUS_CLASS;
				else
					ctx.file.setMinimumBinaryCompatibility(Compiler.JAVA_8);
			}
			case 2 -> generator = LambdaCall.ANONYMOUS_CLASS;
		}

		var lambda实参 = ctx.inferrer.getGenericParameters(lambda接口, lambda方法, lambda目标类型).rawDesc();

		var lambda入参 = new ArrayList<Type>();
		List<Expr> lambda入参的取值表达式;

		ClassNode lambda实现所属的类;

		if (methodRef != null) {
			lambda实现所属的类 = ctx.compiler.resolve(methodRef.type().owner());

			List<Type> args = Type.methodDesc(lambda实参);
			args.remove(args.size()-1);

			var r = ctx.getMethodListOrReport(lambda实现所属的类, methodName, methodRef).findMethod(ctx, Helpers.cast(args), 0);
			if (r == null) return;

			// 方法引用
			impl = r.method;

			lambda入参的取值表达式 = Collections.emptyList();

			// This
			if ((impl.modifier&Opcodes.ACC_STATIC) == 0) {
				methodRef.write(cw);
				lambda入参.add(0, methodRef.type().rawType());
			} else if (!(methodRef instanceof Literal)) {
				ctx.report(this, Kind.WARNING, "symbol.isStatic", lambda实现所属的类, methodName, this);
			}
		} else {
			ctx.file.methods.add(impl);
			impl.name(ctx.lambdaName());
			impl.rawDesc(lambda实参);

			lambda入参的取值表达式 = new ArrayList<>();
			var next = CompileContext.push();
			// lambda参数注入 (逆向出来的，我也不知道去哪找文档看):
			// lambda实现的签名：隐含的this,[注入参数,lambda参数]
			// invokeDyn的签名: lambda类 lambda方法名 (this, 注入参数)
			ctx.pushNestContext(NestContext.lambda(ctx, impl.parameters().subList(0, 0), lambda入参, lambda入参的取值表达式));
			try {
				next.isArgumentDynamic = true;
				next.setClass(ctx.file);
				next.lexer.state = JavaTokenizer.STATE_EXPR;
				task.parse(next);
				ctx.file._setCtx(ctx);
			} catch (ParseException e) {
				throw new ResolveException("Lambda表达式解析失败", e);
			} finally {
				next.isArgumentDynamic = false;
				ctx.popNestContext();
				CompileContext.pop();
			}

			lambda实现所属的类 = ctx.file;

			// This
			if (next.thisUsed) {
				ctx.thisUsed = true;
				cw.vars(Opcodes.ALOAD, ctx.thisSlot);
				lambda入参.add(0, Type.klass(ctx.file.name()));
			}
		}

		if (cw == null) return;

		// lambda类 (returnType)
		lambda入参.add(Type.klass(lambda接口.name()));

		generator.generate(ctx, cw, lambda接口, lambda方法, lambda实参, lambda入参, lambda入参的取值表达式, lambda实现所属的类, impl);
	}
}