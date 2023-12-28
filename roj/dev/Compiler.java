package roj.dev;

import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.reflect.FastInit;
import roj.reflect.Proxy;
import roj.text.CharList;
import roj.text.LineReader;
import roj.ui.AnsiString;
import roj.ui.CLIUtil;
import roj.util.Helpers;

import javax.annotation.processing.Processor;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2021/6/1 1:54
 */
public class Compiler implements DiagnosticListener<JavaFileObject> {
	private static boolean showErrorCode;

	@Deprecated
	private static final JavaCompiler compiler;
	static {
		compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) throw new InternalError("请安装JDK");
	}

	public static void showErrorCode(boolean show) {
		showErrorCode = show;
	}

	private final String basePath;
	private final PrintStream out;
	private final Set<String> skipErrors;
	private int ignored, warnings, errors;
	private final CharList buf;
	private List<ByteListOutput> compiled;

	public Compiler() {
		this(null, null, null, "");
	}

	public Compiler(PrintStream out) {
		this(null, out, null, "");
	}

	public Compiler(PrintStream out, Set<String> skipErrors) {
		this(null, out, skipErrors, "");
	}

	public Compiler(List<Processor> processor, PrintStream out, Set<String> skipErrors, String basePath) {
		this.out = out == null ? System.out : out;
		this.skipErrors = skipErrors == null ? Collections.emptySet() : skipErrors;
		this.basePath = basePath;
		this.buf = new CharList(1024);
	}

	public synchronized boolean compile(List<String> options, List<File> files) {
		if (files.isEmpty()) {
			return true;
		}

		ignored = 0;
		warnings = 0;
		errors = 0;
		buf.clear();

		// TODO use Lavac
		StandardJavaFileManager fm = compiler.getStandardFileManager(this, Locale.getDefault(), StandardCharsets.UTF_8);
		fm = createOrUseDelegation(fm, compiled = new SimpleList<>(), basePath);

		Iterable<? extends JavaFileObject> unit = fm.getJavaFileObjectsFromFiles(files);
		JavaCompiler.CompilationTask task = compiler.getTask(new OutputStreamWriter(out), fm, this, options, null, unit);

		boolean result = false;
		try {
			result = task.call();
		} catch (ArrayIndexOutOfBoundsException e) {
			this.report(new MyDiagnostic("用户类文件中的class读取失败", Diagnostic.Kind.ERROR));
		}

		if (errors > 0) buf.append('\n').append(errors).append(" 个 错误");
		if (warnings > 0) buf.append('\n').append(warnings).append('/').append(warnings+ ignored).append(" 个 警告");
		System.out.println(new AnsiString(buf).bgColor16(result ? CLIUtil.BLUE : CLIUtil.RED).color16(CLIUtil.WHITE + CLIUtil.HIGHLIGHT).toAnsiString());
		return result;
	}

	@Deprecated
	private static StandardJavaFileManager createOrUseDelegation(Object...par) {
		if (delegationClass == null) {
			synchronized (Compiler.class) {
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

	@Deprecated
	private static volatile Function<Object[], StandardJavaFileManager> delegationClass;
	@Deprecated
	private static Function<Object[], StandardJavaFileManager> createDelegation() throws Exception {
		Method proxyGetOutput = StandardJavaFileManager.class.getMethod("getJavaFileForOutput", JavaFileManager.Location.class, String.class, JavaFileObject.Kind.class, FileObject.class);

		ConstantData data = new ConstantData();
		data.name("roj/dev/Compiler$SFMHelper");

		int listId = data.newField(0, "bo", new Type("java/util/List"));
		int nameId = data.newField(0, "aa", new Type("java/lang/String"));

		Proxy.proxyClass(data, new Class<?>[] {StandardJavaFileManager.class}, (m, cw) -> {
			if (m.equals(proxyGetOutput)) {
				int s = TypeHelper.paramSize(cw.mn.rawDesc())+1;
				cw.visitSize(s+2,s);
				cw.one(Opcodes.ALOAD_0);
				cw.field(Opcodes.GETFIELD, data, listId);
				cw.one(Opcodes.ALOAD_0);
				cw.field(Opcodes.GETFIELD, data, nameId);
				cw.invoke(Opcodes.INVOKESTATIC, "roj/dev/Compiler", "proxyGetOutput", TypeHelper.class2asm(new Class<?>[]{StandardJavaFileManager.class, JavaFileManager.Location.class, String.class, JavaFileObject.Kind.class, FileObject.class, List.class, String.class}, JavaFileObject.class));
				return true;
			}
			return false;
		}, listId, nameId);

		FastInit.prepare(data);
		return Helpers.cast(FastInit.make(data));
	}

	@Deprecated
	public static JavaFileObject proxyGetOutput(StandardJavaFileManager delegation,
												JavaFileManager.Location location, String className,
												JavaFileObject.Kind kind, FileObject sibling,
												List<ByteListOutput> outputs,
												String basePath) throws IOException {
		if (kind == JavaFileObject.Kind.CLASS) {
			try {
				ByteListOutput blo = new ByteListOutput(className, basePath);
				outputs.add(blo);
				return blo;
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
		}
		return delegation.getJavaFileForOutput(location, className, kind, sibling);
	}


	public List<ByteListOutput> getCompiled() {
		return compiled;
	}

	@Override
	public void report(Diagnostic<? extends JavaFileObject> diag) {
		if (diag.getKind() == Diagnostic.Kind.ERROR || !skipErrors.contains(diag.getCode())) {
			CharList sb = buf;
			if (diag.getSource() != null) {
				String file = diag.getSource().toUri().getPath();
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
			if (showErrorCode) out.println(diag.getCode());
		} else {
			ignored++;
		}
	}

	private static String getNearCode(JavaFileObject source, long lineNumber) {
		if (lineNumber == -1) return "";
		try {
			return LineReader.readSingleLine(IOUtil.readUTF(source.openInputStream()), (int) lineNumber);
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
}