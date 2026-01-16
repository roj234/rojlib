package roj.ci;

import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asmx.ClassResource;
import roj.asmx.TransformUtil;
import roj.ci.annotation.IndirectReference;
import roj.io.IOUtil;
import roj.reflect.Reflection;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.URICoder;
import roj.ui.Text;
import roj.ui.Tty;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2021/6/1 1:54
 */
public final class JCompiler implements Compiler, DiagnosticListener<JavaFileObject> {
	private static final JavaCompiler javac = ToolProvider.getSystemJavaCompiler();

	private final String basePath;
	private final Factory factory;
	private int ignored, warnings, errors;
	private CharList buf;
	private boolean showErrorCode;

	public JCompiler(Factory factory, String basePath) {
		if (javac == null) throw new IllegalStateException("请安装JDK");

		this.factory = factory;
		this.basePath = basePath;
	}

	@Override public Compiler.Factory factory() {return factory;}

	@Override
	public void modifyOptions(List<String> options, Project project) {
		if ("true".equals(project.variables.get("javac:use_module"))) {
			options.add("--module-path");
			options.add(options.get(options.indexOf("-cp")+1));
		}
	}

	@Override
	public synchronized boolean compile(List<String> options, List<File> sources, List<ClassResource> classes, List<ClassResource> resources, boolean showDiagnosticId) {
		if (sources.isEmpty()) return true;

		ignored = warnings = errors = 0;
		showErrorCode = showDiagnosticId;
		buf = new CharList(1024);
		buf.append(Tty.Screen.clearAfter);

		var jfm = javac.getStandardFileManager(this, Locale.getDefault(), StandardCharsets.UTF_8);
		jfm = createForwarder(jfm, classes, resources, basePath);

		var jfo = jfm.getJavaFileObjectsFromFiles(sources);
		var task = javac.getTask(null, jfm, this, options, null, jfo);

		boolean result = task.call();

		if (errors > 0) buf.append('\n').append(errors).append(" 个 错误");
		if (warnings > 0) buf.append('\n').append(warnings).append('/').append(warnings+ignored).append(" 个 警告");
		if (buf.length() > 3)
			System.out.println(new Text(buf).bgColor16(result ? Tty.BLUE : Tty.RED).color16(Tty.WHITE+Tty.HIGHLIGHT).toAnsiString());
		buf._free();
		return result;
	}

	private static StandardJavaFileManager createForwarder(Object...par) {
		if (delegationClass == null) {
			synchronized (JCompiler.class) {
				if (delegationClass == null) {
					try {
						delegationClass = createDelegation();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		return delegationClass.apply(par);
	}
	private static volatile Function<Object[], StandardJavaFileManager> delegationClass;
	private static Function<Object[], StandardJavaFileManager> createDelegation() {
		ClassNode data = new ClassNode();
		data.name("roj/ci/JCompiler$MySFM");

		int listId = data.newField(0, "classList", Type.klass("java/util/List"));
		int list2Id = data.newField(0, "resList", Type.klass("java/util/List"));
		int nameId = data.newField(0, "basePath", Type.klass("java/lang/String"));

		TransformUtil.proxyClass(data, new Class<?>[] {StandardJavaFileManager.class}, (m, cw) -> {
			if (m.getName().equals("getJavaFileForOutput") || m.getName().equals("getJavaFileForOutputForOriginatingFiles")) {
				int s = TypeHelper.paramSize(cw.method.rawDesc())+1;
				cw.visitSize(s+2,s);
				cw.insn(Opcodes.POP);
				cw.insn(Opcodes.ALOAD_0);
				cw.field(Opcodes.GETFIELD, data, listId);
				cw.insn(Opcodes.ALOAD_0);
				cw.field(Opcodes.GETFIELD, data, nameId);
				cw.invoke(Opcodes.INVOKESTATIC, "roj/ci/JCompiler", "proxyGetOutput", TypeHelper.class2asm(new Class<?>[]{StandardJavaFileManager.class, JavaFileManager.Location.class, String.class, JavaFileObject.Kind.class, List.class, String.class}, JavaFileObject.class));
				return true;
			}
			if (m.getName().equals("getFileForOutput") || m.getName().equals("getFileForOutputForOriginatingFiles")) {
				int s = TypeHelper.paramSize(cw.method.rawDesc())+1;
				cw.visitSize(s+2,s);
				cw.insn(Opcodes.POP);
				cw.insn(Opcodes.ALOAD_0);
				cw.field(Opcodes.GETFIELD, data, list2Id);
				cw.insn(Opcodes.ALOAD_0);
				cw.field(Opcodes.GETFIELD, data, nameId);
				cw.invoke(Opcodes.INVOKESTATIC, "roj/ci/JCompiler", "proxyGetOutput", TypeHelper.class2asm(new Class<?>[]{StandardJavaFileManager.class, JavaFileManager.Location.class, String.class, String.class, List.class, String.class}, FileObject.class));
				return true;
			}

			return false;
		}, listId, list2Id, nameId);

		return Helpers.cast(Reflection.createInstance(JCompiler.class, data));
	}
	@IndirectReference
	public static JavaFileObject proxyGetOutput(StandardJavaFileManager delegation,
												JavaFileManager.Location location, String className,
												JavaFileObject.Kind kind,
												List<MyJFO> outputs,
												String basePath) {
		var fileName = className.replace('.', '/')+kind.extension;
		var uri = URI.create("file://"+URICoder.encodeURIComponent(basePath)+fileName);

		MyJFO jfo = new MyJFO(fileName, uri, kind);
		if (kind == JavaFileObject.Kind.CLASS)
			outputs.add(jfo);
		return jfo;
	}
	@IndirectReference
	public static FileObject proxyGetOutput(StandardJavaFileManager delegation,
												JavaFileManager.Location location, String packageName,
												String relativeName,
												List<MyJFO> outputs,
												String basePath) {

		var fileName = packageName.replace('.', '/')+relativeName;
		var uri = URI.create("file://"+URICoder.encodeURIComponent(basePath)+fileName);

		MyJFO jfo = new MyJFO(fileName, uri, JavaFileObject.Kind.OTHER);
		outputs.add(jfo);
		return jfo;
	}

	@Override
	public void report(Diagnostic<? extends JavaFileObject> diag) {
		if (diag.getKind() == Diagnostic.Kind.ERROR || !factory.ignoredDiagnostics.contains(diag.getCode())) {
			CharList sb = buf;
			block1:
			if (diag.getSource() != null) {
				String file = diag.getSource().toUri().getPath();
				if (file == null) break block1;
				int off = 1;
				if (file.startsWith(basePath, 1)) {
					off += basePath.length() + 1;
				}
				sb.append(file, off, file.length()).append(':');
				if (diag.getLineNumber() > 0) sb.append(diag.getLineNumber()).append(':');
				sb.append(' ');
			}
			sb.append(errorMsg(diag.getKind())).append(':').append(' ').append(diag.getMessage(Locale.CHINA)).append('\n');
			if (diag.getLineNumber() > 0) {
				try {
					sb.append(getNearCode(diag.getSource(), diag.getLineNumber())).append('\n');
					for (int i = 0; i < diag.getColumnNumber(); i++) sb.append(' ');
					sb.set(sb.length()-1, '^');
				} catch (Throwable e) {
					sb.append("无法显示代码: ").append(e.getClass().getName()).append(": ").append(e.getMessage());
				}
				sb.append('\n');
			}
			if (showErrorCode) sb.append(diag.getCode()).append('\n');
		} else {
			ignored++;
		}
	}

	private static String getNearCode(JavaFileObject source, long lineNumber) {
		if (lineNumber == -1) return "";
		try (var tr = TextReader.auto(source.openInputStream())) {
			while (true) {
				var line = tr.readLine();
				if (line == null || --lineNumber == 0) return line;
			}
		} catch (IOException e) {
			e.printStackTrace();
			return "<ERROR> failed to get " + source.getName() + " due to " + e;
		}
	}

	private String errorMsg(Diagnostic.Kind kind) {
		switch (kind) {
			case NOTE: return "注";
			case ERROR: errors++; return "错误";
			case OTHER: return "其他";
			case WARNING: warnings++; return "警告";
			case MANDATORY_WARNING: warnings++; return "强警告";
		}
		return "";
	}

	private static final class MyJFO extends SimpleJavaFileObject implements ClassResource {
		private final String name;
		private final ByteList data;

		MyJFO(String fileName, URI uri, Kind kind) {
			super(uri, kind);
			this.name = fileName;
			this.data = new ByteList();
		}

		@Override public InputStream openInputStream() {return data.slice().asInputStream();}
		@Override public OutputStream openOutputStream() {return data;}
		@Override public Reader openReader(boolean ignoreEncodingErrors) {return new InputStreamReader(openInputStream());}
		@Override public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {return IOUtil.read(openReader(ignoreEncodingErrors));}

		@Override public String getFileName() {return name;}
		@Override public ByteList getClassBytes() {return data;}
	}
}