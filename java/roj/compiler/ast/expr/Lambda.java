package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.cp.Constant;
import roj.asm.cp.*;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.BootstrapMethods;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.LavaFeatures;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.ParseTask;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.NestContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.ParseException;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.List;

/**
 * Lambda要么是方法参数，要么是Assign的目标
 * @author Roj234
 * @since 2024/1/23 0023 11:32
 */
public class Lambda extends ExprNode {
	private static final Asterisk[] AnyLambda = new Asterisk[10];
	static {
		for (int i = 0; i < 10; i++) AnyLambda[i] = new Asterisk("<Lambda/"+i+">");
	}
	public static int getLambdaArgCount(IType anyLambda) {
		String name;
		if (anyLambda.genericType() != IType.ASTERISK_TYPE || !(name = anyLambda.toString()).startsWith("<Lambda/")) return -1;

		char c = name.charAt(8);
		return c != '/' ? c - '0' : Integer.parseInt(name.substring(8, name.length() - 1));
	}

	private List<String> args;
	private ExprNode expr;
	private MethodNode imp;
	private String isMethodRef;
	private ParseTask task;

	// args[0] -> expr
	public Lambda(List<String> args, ExprNode expr) {
		this.args = args;
		this.expr = expr;
	}
	// args -> {...}
	public Lambda(List<String> args, MethodNode imp, ParseTask task) {
		this.args = args;
		this.imp = imp;
		this.task = task;
	}
	// parent::methodRef
	public Lambda(ExprNode parent, String methodRef) {
		this.expr = parent;
		this.isMethodRef = methodRef;
	}

	@Override
	public String toString() { return (args.size() == 1 ? args.get(0) : "("+TextUtil.join(args, ", ")+")")+" -> "+expr; }
	@Override
	public IType type() {return args.size() < 10 ? AnyLambda[args.size()] : new Asterisk("<Lambda//"+args.size()+">");}
	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		ctx.file.setMinimumBinaryCompatibility(LavaFeatures.COMPATIBILITY_LEVEL_JAVA_8);

		if (isMethodRef != null) {
			if (expr instanceof DotGet d) {
				expr = null;
				ExprNode next = d.resolveEx(ctx, n1 -> {
					var n = ((IClass) n1);
					expr = new roj.compiler.ast.expr.Constant(new Type(n.name()), new CstClass(n.name()));
				}, null);
				if (expr == null) expr = next;
			} else {
				expr = expr.resolve(ctx);
			}
		}
		return this;
	}

	static sealed class Backend {
		void generate(ConstantData lambdaType, MethodNode lambdaReceiver, String lambdaDescWithPrependArgs, CstRef implementor) {

		}
	}
	static final class InvokeDynamicBackend extends Backend {}
	static final class AnonymousClassBackend extends Backend {

	}

	@Override
	public final void write(MethodWriter cw, boolean noRet) {write(cw, null);}
	@Override
	public void write(MethodWriter cw, @Nullable TypeCast.Cast _returnType) {
		var ctx = LocalContext.get();

		if (_returnType == null) {
			ctx.report(Kind.ERROR, "lambda.untyped");
			return;
		}

		var lambdaType = ctx.classes.getClassInfo(_returnType.getType1().owner());
		var lambdaRh = ctx.classes.getResolveHelper(lambdaType);
		int lambdaCallType = lambdaRh.getLambdaType();

		switch (lambdaCallType) {
			default -> {
				ctx.report(Kind.ERROR, "lambda.unsupported."+lambdaCallType);
				return;
			}
			case 1 -> {
				// interface lambda
			}
			case 2 -> {
				// abstract class lambda
				ctx.report(Kind.INCOMPATIBLE, "抽象类自动lambda转化暂未实现，请耐心等待！");
			}
		}

		var lambdaReceiver = lambdaRh.getLambdaMethod();

		var _implementDesc = ctx.inferrer.getGenericParameters(lambdaType, lambdaReceiver, _returnType.getType1()).desc;
		var impDesc = _implementDesc == null ? lambdaReceiver.rawDesc() : TypeHelper.getMethod(Arrays.asList(_implementDesc));
		var impArg = new SimpleList<IType>();

		CstRef impRef;
		if (isMethodRef != null) {
			var refType = ctx.classes.getClassInfo(expr.type().owner());

			var ml = ctx.methodListOrReport(refType, isMethodRef);
			if (ml == null) {
				ctx.report(Kind.ERROR, "lambda.notfound");
				return;
			}
			var r = ml.findMethod(ctx, Helpers.cast(lambdaReceiver.parameters()), 0);
			if (r == null) return;

			// 方法引用，方法已存在
			imp = r.method;
			impRef = (refType.modifier()&Opcodes.ACC_INTERFACE) != 0
				? ctx.file.cp.getItfRef(imp.owner, imp.name(), imp.rawDesc())
				: ctx.file.cp.getMethodRef(imp.owner, imp.name(), imp.rawDesc());
		} else {
			if (imp == null) {
				imp = new MethodNode(0, ctx.file.name, "lambda$1", impDesc);
				var cw1 = ctx.classes.createMethodWriter(ctx.file, imp);

				// 表达式lambda
				ctx.enclosing.add(NestContext.lambda(ctx, ctx.file, cw1, new SimpleList<>()));
				try {
					var node = expr.resolve(ctx);
					node.write(cw1, ctx.castTo(node.type(), imp.returnType(), 0));
				} finally {
					ctx.enclosing.pop();
				}
			} else {
				// 块lambda
				imp.name("lambda$1");
				imp.rawDesc(impDesc);

				// TODO codeWriter should be singleton
				//var cw1 = ctx.classes.createMethodPoet(ctx.file, imp);
				//ctx.enclosing.add(NestContext.lambda(ctx, ctx.file, cw1, new SimpleList<>()));

				var next = LocalContext.next();
				try {
					next.setClass(ctx.file);
					next.setMethod(imp);
					next.lexer.state = JavaLexer.STATE_EXPR;
					task.parse(next);
				} catch (ParseException e) {
					e.printStackTrace();
				} finally {
					//ctx.enclosing.pop();
					LocalContext.prev();
				}
			}

			impRef = ctx.file.cp.getMethodRef(ctx.file.name, imp.name(), imp.rawDesc());
			ctx.file.methods.add(imp);
		}

		// TODO method throws and/or check static
		//mn.modifier |= ACC_STATIC;
		if ((imp.modifier&Opcodes.ACC_STATIC) == 0) {
			cw.one(Opcodes.ALOAD_0);
			impArg.add(new Type(ctx.file.name));
		}

		// ... 参数注入方式
		// impRef[Decl]: 接口实现方法：[this,注入参数，接口参数]
		// invokeDyn[Ref]: 接口方法名 (this,注入参数)接口类

		// returnType
		impArg.add(new Type(lambdaType.name()));

		/**
		 *  前三个参数由VM提供
		 * 		 MethodHandles.Lookup caller
		 * 		 String interfaceMethodName
		 * 		 MethodType factoryType
		 *  后三个参数由这里的arguments提供, 分别是(describeConstantable)
		 * @see java.lang.invoke.LambdaMetafactory#metafactory(MethodHandles.Lookup, String, MethodType, MethodType, MethodHandle, MethodType)
		 */
		var item = new BootstrapMethods.Item("java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
			"Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", BootstrapMethods.Kind.INVOKESTATIC,
			Constant.METHOD, Arrays.asList(
			// CMethodType: 接口方法的形参(不解析泛型)
			//   (Ljava/lang/Object;)Ljava/lang/Object;
			new CstMethodType(lambdaReceiver.rawDesc()),
			// CMethodHandle: 实际调用的方法
			//   REF_invokeStatic java/lang/String.valueOf:(Ljava/lang/Object;)Ljava/lang/String;
			new CstMethodHandle((imp.modifier&Opcodes.ACC_STATIC) != 0 ? BootstrapMethods.Kind.INVOKESTATIC : BootstrapMethods.Kind.INVOKEVIRTUAL, impRef),
			// CMethodType: 接口方法的实参(解析泛型)
			//   (Ljava/lang/Integer;)Ljava/lang/String;
			new CstMethodType(impDesc)
		));

		int tableIdx = ctx.file.addLambdaRef(item);
		cw.invokeDyn(tableIdx, lambdaReceiver.name(), TypeHelper.getMethod(impArg), 0);
	}
}