package roj.compiler.plugins.jni;

import roj.asm.*;
import roj.asm.annotation.Annotation;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.HashMap;
import roj.compiler.CompileContext;
import roj.compiler.LavaCompiler;
import roj.compiler.api.Compiler;
import roj.compiler.api.CompilerPlugin;
import roj.compiler.api.Processor;
import roj.compiler.api.Types;
import roj.compiler.resolve.TypeCast;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.DateFormat;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2025/4/8 18:24
 */
@CompilerPlugin(name = "jniheaders", desc = "为本地方法生成JNI头文件")
public class JNIHeadersPlugin implements Processor {
	public void pluginInit(Compiler api) {
		api.addAnnotationProcessor(this);
		//api.addCompletedCallback();
	}

	static final TypedKey<CharList> sbType = new TypedKey<>("jniheaders:sb");

	@Override
	public Set<String> acceptedAnnotations() {return Collections.singleton("roj/compiler/plugins/jni/JNIHeaders");}

	@Override
	public void handle(CompileContext ctx, ClassDefinition file, Attributed node, Annotation annotation) {
		writeJNIHeaders(ctx.compiler, Helpers.cast(Collections.singletonList(file)));
	}

	public static void writeJNIHeaders(LavaCompiler ctx, List<ClassNode> classes) {
		var cast = new TypeCast();
		cast.context = ctx;

		CharList h = ((Compiler) ctx).attachment(sbType);

		for (var klass : classes) {
			var nativeMethods = new HashMap<String, List<MethodNode>>();
			for (MethodNode method : klass.methods) {
				if ((method.modifier&Opcodes.ACC_NATIVE) != 0) {
					nativeMethods.computeIfAbsent(method.name(), Helpers.fnArrayList()).add(method);
				}
			}
			if (nativeMethods.isEmpty()) continue;

			if (h == null) {
				h = new CharList();
				((Compiler) ctx).attachment(sbType, h);

				h.append("/* THIS FILE IS MACHINE GENERATED, DO NOT EDIT IT MANUALLY!!!\n");
				h.append(" * Update on: ").append(DateFormat.toLocalDateTime(System.currentTimeMillis())).append(" */\n");
				h.append("""
				#include <jni.h>

				#ifdef __cplusplus
				extern "C" {
				#endif

				""");
			}

			var headerName = klass.name().replace('/', '_');
			h.append("#ifndef _Included_").append(headerName).append("\n#define _Included_").append(headerName).append("\n\n");

			for (var entry : nativeMethods.entrySet()) {
				List<MethodNode> list = entry.getValue();
				for (MethodNode method : list) {
					h.append("/*")
							.append(" * Class:     ").append(klass.name()).append("\n")
							.append(" * Method: ").append(TypeHelper.humanize(Type.methodDesc(method.rawDesc()), method.name(), true)).append("\n")
							.append(" */");
					h.append("JNIEXPORT ").append(jniType(cast, method.returnType())).append(" JNICALL ").append(encodeMethod(method, list.size() > 1));
					h.append("  (JNIEnv *, ");
					h.append(((method.modifier&Opcodes.ACC_STATIC) != 0) ? "jclass" : "jobject");
					for (Type arg : method.parameters()) {
						h.append(", ").append(jniType(cast, arg));
					}
					h.append(");\n\n");
				}
			}
			h.append("#endif\n");
		}
	}

	private static void writeFooter(CharList h) {
		h.append("""
				#ifdef __cplusplus
				}
				#endif
				""");
	}

	private static String jniType(TypeCast cast, Type t) {
		return switch (t.getActualType()) {
			default -> null;
			case VOID -> "void";
			case BOOLEAN -> "jboolean";
			case BYTE -> "jbyte";
			case CHAR -> "jchar";
			case SHORT -> "jshort";
			case INT -> "jint";
			case LONG -> "jlong";
			case FLOAT -> "jfloat";
			case DOUBLE -> "jdouble";
			case CLASS -> {
				if (t.array() >= 1) {
					if (t.array() != 1) yield "jobjectArray";
					yield switch (t.type) {
						case BOOLEAN -> "jbooleanArray";
						case BYTE -> "jbyteArray";
						case CHAR -> "jcharArray";
						case SHORT -> "jshortArray";
						case INT -> "jintArray";
						case LONG -> "jlongArray";
						case FLOAT -> "jfloatArray";
						case DOUBLE -> "jdoubleArray";
						default -> "jobjectArray";
					};
				}

				if (t.owner.equals("java/lang/String")) {
					yield "jstring";
				} else if (t.owner.equals("java/lang/Class")) {
					yield "jclass";
				} else if (cast.checkCast(t, Types.THROWABLE_TYPE).type >= 0) {
					yield "jthrowable";
				} else {
					yield "jobject";
				}
			}
		};
	}

	private static String encodeMethod(MethodNode method, boolean isOverloaded) {
		var sb = IOUtil.getSharedCharBuf();
		sb.append("Java_");
		encodeJni(sb, method.owner());
		sb.append('_');
		encodeJni(sb, method.name());
		if (isOverloaded) {
			String parDesc = method.rawDesc();
			sb.append("__");
			encodeJni(sb, parDesc.substring(1, parDesc.lastIndexOf(')')));
		}
		return sb.toString();
	}

	private static void encodeJni(CharList sb, String str) {
		for (int i = 0; i < str.length(); i++) {
			var ch = str.charAt(i);
			if (isalnum(ch)) sb.append(ch);
			else switch (ch) {
				case '/', '.' -> sb.append("_");
				case '_' -> sb.append("_1");
				case ';' -> sb.append("_2");
				case '[' -> sb.append("_3");
				default -> sb.append(encodeChar(ch));
			}
		}
	}

	private static char[] encodeChar(char ch) {
		String s = Integer.toHexString(ch);
		int nzeros = 5 - s.length();
		char[] result = new char[6];
		result[0] = '_';
		for (int i = 1; i <= nzeros; i++) {
			result[i] = '0';
		}
		for (int i = nzeros + 1, j = 0; i < 6; i++, j++) {
			result[i] = s.charAt(j);
		}
		return result;
	}

	/* Warning: Intentional ASCII operation. */
	private static boolean isalnum(char ch) {
		return ch <= 0x7f && /* quick test */
				((ch >= 'A' && ch <= 'Z')  ||
						(ch >= 'a' && ch <= 'z')  ||
						(ch >= '0' && ch <= '9'));
	}
}
