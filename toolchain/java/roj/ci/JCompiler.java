package roj.ci;

import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asmx.ClassResource;
import roj.collect.ArrayList;
import roj.reflect.ClassDefiner;
import roj.reflect.Proxy;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.URICoder;
import roj.ui.Tty;
import roj.ui.Text;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
		if ("true".equals(project.conf.variables.get("javac:use_module"))) {
			options.add("--module-path");
			options.add(options.get(options.indexOf("-cp")+1));
		}
	}

	public synchronized List<? extends ClassResource> compile(List<String> options, List<File> sources, boolean showDiagnosticId) {
		if (sources.isEmpty()) return Collections.emptyList();

		ignored = warnings = errors = 0;
		showErrorCode = showDiagnosticId;
		buf = new CharList(1024);
		buf.append(Tty.Screen.clearAfter);

		List<MyJFO> compiled = new ArrayList<>();
		var jfm = javac.getStandardFileManager(this, Locale.getDefault(), StandardCharsets.UTF_8);
		jfm = createOrUseDelegation(jfm, compiled, basePath);

		var jfo = jfm.getJavaFileObjectsFromFiles(sources);
		var task = javac.getTask(null, jfm, this, options, null, jfo);

		boolean result = task.call();

		if (errors > 0) buf.append('\n').append(errors).append(" 个 错误");
		if (warnings > 0) buf.append('\n').append(warnings).append('/').append(warnings+ignored).append(" 个 警告");
		if (buf.length() > 3)
			System.out.println(new Text(buf).bgColor16(result ? Tty.BLUE : Tty.RED).color16(Tty.WHITE+Tty.HIGHLIGHT).toAnsiString());
		buf._free();
		return result ? compiled : null;
	}

	private static StandardJavaFileManager createOrUseDelegation(Object...par) {
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
	private static Function<Object[], StandardJavaFileManager> createDelegation() throws Exception {
		Method proxyGetOutput = StandardJavaFileManager.class.getMethod("getJavaFileForOutput", JavaFileManager.Location.class, String.class, JavaFileObject.Kind.class, FileObject.class);

		ClassNode data = new ClassNode();
		data.name("roj/ci/JCompiler$MySFM");

		int listId = data.newField(0, "bo", Type.klass("java/util/List"));
		int nameId = data.newField(0, "aa", Type.klass("java/lang/String"));

		Proxy.proxyClass(data, new Class<?>[] {StandardJavaFileManager.class}, (m, cw) -> {
			if (m.equals(proxyGetOutput)) {
				int s = TypeHelper.paramSize(cw.mn.rawDesc())+1;
				cw.visitSize(s+2,s);
				cw.insn(Opcodes.ALOAD_0);
				cw.field(Opcodes.GETFIELD, data, listId);
				cw.insn(Opcodes.ALOAD_0);
				cw.field(Opcodes.GETFIELD, data, nameId);
				cw.invoke(Opcodes.INVOKESTATIC, "roj/ci/JCompiler", "proxyGetOutput", TypeHelper.class2asm(new Class<?>[]{StandardJavaFileManager.class, JavaFileManager.Location.class, String.class, JavaFileObject.Kind.class, FileObject.class, List.class, String.class}, JavaFileObject.class));
				return true;
			}
			return false;
		}, listId, nameId);

		return Helpers.cast(ClassDefiner.newInstance(data));
	}
	public static JavaFileObject proxyGetOutput(StandardJavaFileManager delegation,
												JavaFileManager.Location location, String className,
												JavaFileObject.Kind kind, FileObject sibling,
												List<MyJFO> outputs,
												String basePath) throws IOException {
		if (kind == JavaFileObject.Kind.CLASS) {
			try {
				MyJFO blo = new MyJFO(className, basePath);
				outputs.add(blo);
				return blo;
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
		}
		return delegation.getJavaFileForOutput(location, className, kind, sibling);
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

	/**
	 * @author solo6975
	 * @since 2021/10/2 14:00
	 */
	private static final class MyJFO extends SimpleJavaFileObject implements ClassResource {
		private final String name;
		private final ByteList output;

		MyJFO(String className, String basePath) throws URISyntaxException {
			super(new URI("file://"+ URICoder.encodeURIComponent(basePath)+className.replace('.', '/')+".class"), Kind.CLASS);
			this.output = new ByteList();
			this.name = className.replace('.', '/')+".class";
		}

		@Override public OutputStream openOutputStream() {return output;}
		@Override public String getFileName() {return name;}
		@Override public ByteList getClassBytes() {return output;}
	}
}