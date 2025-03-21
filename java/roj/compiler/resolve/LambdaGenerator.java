package roj.compiler.resolve;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.BootstrapMethods;
import roj.asm.cp.Constant;
import roj.asm.cp.CstMethodHandle;
import roj.asm.cp.CstMethodType;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.compiler.context.LocalContext;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2025/3/29 0029 17:30
 */
public enum LambdaGenerator {
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
		public void generate(LocalContext ctx, CodeWriter cw,
							 ClassNode lambda接口, MethodNode lambda方法,
							 String lambda实参, List<Type> lambda入参,
							 ClassNode lambda实现所属的类, MethodNode lambda实现) {

			byte refType = (lambda实现所属的类.modifier& Opcodes.ACC_INTERFACE) != 0 ? Constant.INTERFACE : Constant.METHOD;
			var lambdaImplRef = cw.cpw.getRefByType(lambda实现.owner, lambda实现.name(), lambda实现.rawDesc(), refType);

			var item = new BootstrapMethods.Item(
					"java/lang/invoke/LambdaMetafactory",
					"metafactory",
					"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
					// metafactory的调用类型
					BootstrapMethods.Kind.INVOKESTATIC,
					// metafactory是一个方法（不确定具体意思）
					Constant.METHOD,
					Arrays.asList(
							// CMethodType: lambda方法的形参(不解析泛型)
							//   (Ljava/lang/Object;)Ljava/lang/Object;
							new CstMethodType(lambda方法.rawDesc()),
							// CMethodHandle: lambda实现方法
							//   REF_invokeStatic java/lang/String.valueOf:(Ljava/lang/Object;)Ljava/lang/String;
							// TODO 如果编译的类是接口要怎么填写？
							new CstMethodHandle((lambda实现.modifier&Opcodes.ACC_STATIC) != 0? BootstrapMethods.Kind.INVOKESTATIC : BootstrapMethods.Kind.INVOKEVIRTUAL, lambdaImplRef),
							// CMethodType: lambda方法的实参(解析泛型)
							//   (Ljava/lang/Integer;)Ljava/lang/String;
							new CstMethodType(lambda实参)
					)
			);

			int tableIdx = ctx.file.addLambdaRef(item);
			cw.invokeDyn(tableIdx, lambda方法.name(), Type.toMethodDesc(lambda入参), 0);
		}
	},
	ANONYMOUS_CLASS {
		@Override
		public void generate(LocalContext ctx, CodeWriter cw,
							 ClassNode lambda接口, MethodNode lambda方法,
							 String lambda实参, List<Type> lambda入参,
							 ClassNode lambda实现所属的类, MethodNode lambda实现) {

			var node = new ClassNode();
			node.addInterface(lambda接口.name());

			var lambda构造器 = node.newMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, lambda方法.name(), Type.toMethodDesc(lambda入参));
			// new一个对象，然后putfield

			var lambda转换器 = node.newMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL, lambda方法.name(), lambda方法.rawDesc());
			// 根据实参做一下Cast，然后带上入参里的上下文

			//ctx.classes.addGeneratedClass(node);

			cw.invokeS(node.name(), lambda方法.name(), Type.toMethodDesc(lambda入参));
			throw new IllegalStateException("未实现");
		}
	}
	;

	public abstract void generate(LocalContext ctx, CodeWriter cw,
								  ClassNode lambda接口, MethodNode lambda方法,
								  String lambda实参, List<Type> lambda入参,
								  ClassNode lambda实现所属的类, MethodNode lambda实现);
}
