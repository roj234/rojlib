package roj.compiler.util;

import roj.asm.Attributed;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.attr.Attribute;
import roj.asm.type.TypeHelper;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.compiler.LavaCompileUnit;
import roj.compiler.LavaCompiler;
import roj.compiler.api.Compiler;
import roj.compiler.doc.Javadoc;
import roj.compiler.doc.JavadocProcessor;
import roj.compiler.library.JarLibrary;
import roj.compiler.library.RuntimeLibrary;
import roj.io.IOUtil;
import roj.text.CharList;

import java.io.File;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2025/11/17 08:29
 */
public class TypeStubForLLM {
	public static class AttachedJavadoc extends Attribute {
		Javadoc javadoc;

		public AttachedJavadoc(Javadoc javadoc) {
			this.javadoc = javadoc;
		}

		@Override public String name() {return "Javadoc";}
		@Override public boolean writeIgnore() {return true;}
		@Override public String toString() {return "Javadoc";}
	}

	public static void main(String[] args) throws Exception {
		TypeHelper.TYPE_TOSTRING_NO_PACKAGE = false;
		var file = new File(args[0]);

		var compiler = new LavaCompiler(false) {
			@Override
			public JavadocProcessor createJavadocProcessor(Javadoc javadoc, CompileUnit file) {
				return node -> node.addAttribute(new AttachedJavadoc(javadoc));
			}
		};
		compiler.structOnly = true;
		compiler.features.add(Compiler.EMIT_METHOD_PARAMETERS);
		compiler.addLibrary(RuntimeLibrary.getSystemModules(List.of("java.base")));
		compiler.addLibrary(new JarLibrary(IOUtil.getJar(TypeStubForLLM.class)));
		// TODO add more library via -cp

		CompileContext ctx = compiler.createContext();
		CompileContext.set(ctx);

		var unit = new LavaCompileUnit(file.getName(), IOUtil.readString(file));

		List<? extends ClassNode> results = compiler.compile(ArrayList.asModifiableList(unit));

		for (ClassNode result : results) {
			ArrayList<MethodNode> methods = result.methods;
			for (int i = methods.size() - 1; i >= 0; i--) {
				MethodNode method = methods.get(i);
				if (method.getAttribute("Code") != null)
					methods.remove(i);
			}

			System.out.println(toString1(result));
		}
	}

	public static String toString1(ClassNode node) {
		CharList sb = new CharList(1000);

		String jd = getSummary(node);
		if (jd != null) sb.append("/**\n ").append(jd).append("\n */\n");

		var _a = node.getAttribute(Attribute.VisibleAnnotations);
		if (_a != null) _a.toString(sb, 0);
		_a = node.getAttribute(Attribute.InvisibleAnnotations);
		if (_a != null) _a.toString(sb, 0);

		int acc = node.modifier;
		if ((acc&ACC_ANNOTATION) != 0) acc &= ~(ACC_ABSTRACT|ACC_INTERFACE);
		else if ((acc&ACC_INTERFACE) != 0) acc &= ~(ACC_ABSTRACT);
		showModifiers(acc, ACC_SHOW_CLASS, sb);

		var _seal = node.getAttribute(Attribute.PermittedSubclasses);
		if (_seal != null) sb.append("sealed ");

		var _module = node.getAttribute(Attribute.Module);
		if (_module != null) sb.append(_module.self.toString());
		else {
			if ((acc&(ACC_ENUM|ACC_INTERFACE|ACC_MODULE|ACC_ANNOTATION)) == 0) sb.append("class ");
			TypeHelper.toStringOptionalPackage(sb, node.name());
		}

		var _sign = node.getAttribute(Attribute.SIGNATURE);
		if (_sign != null) sb.append(_sign);
		else {
			String parent = node.parent();
			if (!"java/lang/Object".equals(parent) && parent != null) {
				TypeHelper.toStringOptionalPackage(sb.append(" extends "), parent);
			}

			var _list = node.interfaces();
			if (!_list.isEmpty()) {
				sb.append(" implements ");
				for (int j = 0; j < _list.size();) {
					String i = _list.get(j);
					TypeHelper.toStringOptionalPackage(sb, i);
					if (++j == _list.size()) break;
					sb.append(", ");
				}
			}
		}
		if (_seal != null) {
			sb.append(" permits ");
			var _list = _seal.value;
			for (int j = 0; j < _list.size();) {
				String i = _list.get(j);
				TypeHelper.toStringOptionalPackage(sb, i);
				if (++j == _list.size()) break;
				sb.append(", ");
			}
		}

		sb.append(" {");
		if (_module != null) {
			_module.writeModuleInfo(sb);
		}
		if (!node.fields.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < node.fields.size(); i++) {
				var fn = node.fields.get(i);
				String summary = getSummary(fn);
				if (summary != null) sb.append("/**\n ").append(summary).append("\n */\n");
				fn.toString(sb, node, 4, true).append("\n");
			}
		}
		if (!node.methods.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < node.methods.size(); i++) {
				MethodNode mn = node.methods.get(i);
				String summary = getSummary(mn);
				if (summary != null) sb.append("/**\n ").append(summary).append("\n */\n");
				mn.toString(sb, node, 4).append('\n');
			}
		}

		return sb.append('}').toStringAndFree();
	}

	private static String getSummary(Attributed node) {
		AttachedJavadoc attachedJavadoc = (AttachedJavadoc) node.getAttribute("Javadoc");
		if (attachedJavadoc == null) return null;
		String result = "";
		var itr = attachedJavadoc.javadoc.lines.iterator();
		if (itr.hasNext()) {
			CharList tmp = new CharList();
			while (true) {
				var next = itr.next();
				tmp.append(next.trim());
				if (!itr.hasNext()) break;
				if (!next.startsWith("@")) tmp.append("\n * ");
				else tmp.append(' ');
			}
			result = tmp.toStringAndFree();
		}

		return result.trim();
	}
}
