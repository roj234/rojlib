package roj.compiler.asm;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.attr.BootstrapMethods;
import roj.asm.cp.Constant;
import roj.asm.cp.CstMethodHandle;
import roj.asm.cp.CstMethodType;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.ast.expr.Expr;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2025/3/29 17:30
 */
public enum LambdaCall {
	INVOKE_DYNAMIC {
		/**
		 *  前三个参数由VM提供
		 * 		 MethodHandles.Lookup caller
		 * 		 String interfaceMethodName
		 * 		 MethodType factoryType
		 *  后三个参数由这里的arguments提供, 含义列在下方了
		 * @see java.lang.invoke.LambdaMetafactory#metafactory(MethodHandles.Lookup, String, MethodType, MethodType, MethodHandle, MethodType)
		 */
		@Override
		public void generate(CompileContext ctx, MethodWriter cw,
							 ClassNode lambdaOwner, MethodNode lambdaMethod, String lambdaActualArguments,
							 List<Type> capturedTypes, List<Expr> capturedTypesExpr,
							 ClassNode implementationOwner, MethodNode implementation) {

			// 注入参数
			for (var node : capturedTypesExpr) node.write(cw);

			byte refType = (implementationOwner.modifier& ACC_INTERFACE) != 0 ? Constant.INTERFACE : Constant.METHOD;
			var lambdaImplRef = cw.cpw.getRefByType(implementation.owner(), implementation.name(), implementation.rawDesc(), refType);

			var item = new BootstrapMethods.Item(
					cw.cpw.getRefByType(
							"java/lang/invoke/LambdaMetafactory",
							"metafactory",
							"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
							Constant.METHOD
					),

					// CMethodType: lambda方法的形参(不解析泛型)
					//   (Ljava/lang/Object;)Ljava/lang/Object;
					new CstMethodType(lambdaMethod.rawDesc()),
					// CMethodHandle: lambda实现方法
					//   REF_invokeStatic java/lang/String.valueOf:(Ljava/lang/Object;)Ljava/lang/String;
					new CstMethodHandle((implementation.modifier&ACC_STATIC) != 0? BootstrapMethods.Kind.INVOKESTATIC : BootstrapMethods.Kind.INVOKEVIRTUAL, lambdaImplRef),
					// CMethodType: lambda方法的实参(解析泛型)
					//   (Ljava/lang/Integer;)Ljava/lang/String;
					new CstMethodType(lambdaActualArguments)
			);

			int tableIdx = ctx.file.addLambdaRef(item);
			cw.invokeDyn(tableIdx, lambdaMethod.name(), Type.getMethodDescriptor(capturedTypes), 0);
		}
	},
	ANONYMOUS_CLASS {
		@Override
		public void generate(CompileContext ctx, MethodWriter cw,
							 ClassNode lambdaOwner, MethodNode lambdaMethod, String lambdaActualArguments,
							 List<Type> capturedTypes,
							 List<Expr> capturedTypesExpr, ClassNode implementationOwner, MethodNode implementation) {

			ClassNode node = ctx.file.newAnonymousClass_NoBody(ctx.method, null);
			node.addInterface(lambdaOwner.name());

			//
			capturedTypes.set(capturedTypes.size() - 1, Type.VOID_TYPE);

			// 生成构造器
			CodeWriter ctorCw = node.newMethod(ACC_PUBLIC, "<init>", Type.getMethodDescriptor(capturedTypes));
			ctorCw.vars(ALOAD, 0);
			ctorCw.invoke(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

			// 存储捕获变量
			int varSlot = 1;
			var hasMulti = false;
			for (int i = 0; i < capturedTypes.size()-1; i++) {
				Type type = capturedTypes.get(i);

				node.newField(ACC_PRIVATE | ACC_FINAL, "val$"+i, type);

				ctorCw.vars(ALOAD, 0);
				ctorCw.vars(type.getOpcode(ILOAD), varSlot);
				ctorCw.field(PUTFIELD, node, i);

				int length = type.length();
				if (length > 1) hasMulti = true;
				varSlot += length;
			}
			ctorCw.visitSize(hasMulti ? 3 : 2, varSlot);
			cw.visitSizeMax(0, varSlot); // 调用者至少也要这么大的栈
			ctorCw.insn(RETURN);
			ctorCw.finish();

			// 实现接口方法
			CodeWriter implCw = node.newMethod(ACC_PUBLIC | ACC_FINAL, lambdaMethod.name(), lambdaMethod.rawDesc());

			// 加载捕获变量
			for (int i = 0; i < capturedTypes.size()-1; i++) {
				implCw.vars(ALOAD, 0);
				implCw.field(GETFIELD, node, i);
			}

			// 加载接口参数
			int localIndex = 1;

			var arguments = new ArrayList<Type>();
			var argRealType1 = new ArrayList<Type>();
			Type.getArgumentTypes(lambdaMethod.rawDesc(), arguments);
			Type.getArgumentTypes(lambdaActualArguments, argRealType1);
			var argRealType2 = implementation.parameters().subList(capturedTypes.size()-1, implementation.parameters().size());

			for (int i = 0; i < arguments.size(); i++) {
				Type argType = arguments.get(i);
				implCw.vars(argType.getOpcode(ILOAD), localIndex);
				ctx.castTo(argType, argRealType1.get(i), -3).write(implCw);
				ctx.castTo(argRealType1.get(i), argRealType2.get(i), 0).write(implCw);
				localIndex += argType.length();
			}

			// 调用目标方法
			implementation.modifier &= ~ACC_PRIVATE;
			//ctx.file.j11PrivateConstructor(implementation);
			byte invokeOpcode = (implementation.modifier & ACC_STATIC) != 0 ? INVOKESTATIC : INVOKEVIRTUAL;
			String owner = implementation.owner();

			implCw.invoke(invokeOpcode,
					owner,
					implementation.name(),
					implementation.rawDesc(),
					false);

			int localSize = TypeHelper.paramSize(lambdaMethod.rawDesc());
			int stackSize = Math.max(localSize, TypeHelper.paramSize(implementation.rawDesc()));
			implCw.visitSizeMax(Math.max(stackSize, lambdaMethod.returnType().length()), localSize + (((lambdaMethod.modifier&ACC_STATIC) != 0) ? 0 : 1));
			//implCw.computeFrames(Code.COMPUTE_SIZES);

			Type returnMethodType = Type.getReturnType(lambdaMethod.rawDesc());
			// 返回结果
			ctx.castTo(implementation.returnType(), argRealType1.getLast(), 0).write(implCw);
			ctx.castTo(argRealType1.getLast(), returnMethodType, -3).write(implCw);
			implCw.return_(returnMethodType);

			// 5. 生成调用代码 (NEW + DUP + 加载捕获变量 + INVOKESPECIAL)
			cw.clazz(NEW, node.name());
			cw.insn(DUP);

			// 加载所有捕获变量
			for (var node1 : capturedTypesExpr) node1.write(cw);

			cw.invokeD(node.name(), "<init>", Type.getMethodDescriptor(capturedTypes));
		}
	}
	;

	/**
	 * 生成Lambda表达式的调用代码
	 *
	 * @param lambdaOwner           函数式接口的类型
	 * @param lambdaMethod          函数式接口中要实现的抽象方法
	 * @param lambdaActualArguments Lambda方法的实际参数（包含泛型）
	 * @param capturedTypes         Lambda表达式捕获的外部变量类型列表（按顺序）
	 * @param capturedTypesExpr     Lambda表达式捕获的外部变量取值表达式（按顺序）
	 * @param implementationOwner   包含Lambda实现方法的类：
	 *                              - 对于方法引用(::语法)：是被引用方法所在的类
	 *                              - 对于块/表达式Lambda：是当前正在编译的类
	 * @param implementation        Lambda表达式的具体实现方法：
	 *                              - 对于方法引用：是被引用的目标方法
	 *                              - 对于块/表达式Lambda：是编译器生成的包含Lambda体的方法
	 */
	public abstract void generate(CompileContext ctx, MethodWriter cw,
								  ClassNode lambdaOwner, MethodNode lambdaMethod,
								  String lambdaActualArguments, List<Type> capturedTypes,
								  List<Expr> capturedTypesExpr, ClassNode implementationOwner, MethodNode implementation);
}
