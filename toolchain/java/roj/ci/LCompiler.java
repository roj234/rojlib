package roj.ci;

import roj.asm.ClassDefinition;
import roj.asmx.ClassResource;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.compiler.LavaCompileUnit;
import roj.compiler.LavaCompiler;
import roj.compiler.diagnostic.Kind;
import roj.compiler.diagnostic.TextDiagnosticReporter;
import roj.compiler.library.JarLibrary;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.Text;
import roj.ui.Tty;
import roj.util.function.Flow;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Roj234
 * @since 2025/4/4 1:01
 */
public final class LCompiler extends TextDiagnosticReporter implements Compiler {
	private final String basePath;
	private final Factory factory;
	private boolean showErrorCode;
	private CharList buf;

	public LCompiler(Factory factory, String basePath) {
		super(9990,9990,0);
		this.factory = factory;
		this.basePath = basePath;
	}

	@Override public Factory factory() {return factory;}

	@Override
	public synchronized boolean compile(List<String> options, List<File> sources, List<ClassResource> classes, List<ClassResource> resources, boolean showDiagnosticId) {
		if (sources.isEmpty()) return true;

		total = err = warn = 0;
		showErrorCode = showDiagnosticId;
		buf = new CharList(1024);

		List<CompileUnit> files = new ArrayList<>();
		for (File source : sources) {
			try {
				files.add(LavaCompileUnit.create(source.getName(), IOUtil.readString(source)));
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}

		var compiler = new LavaCompiler();

		compiler.features.add(roj.compiler.api.Compiler.EMIT_INNER_CLASS);
		compiler.features.add(roj.compiler.api.Compiler.OPTIONAL_SEMICOLON);
		compiler.features.add(roj.compiler.api.Compiler.VERIFY_FILENAME);
		compiler.features.add(roj.compiler.api.Compiler.OMISSION_NEW);
		compiler.features.add(roj.compiler.api.Compiler.SHARED_STRING_CONCAT);
		compiler.features.add(roj.compiler.api.Compiler.OMIT_CHECKED_EXCEPTION);

		for (int i = 0; i < options.size(); i++) {
			String option = options.get(i);
			if (option.equals("-cp")) {
				for (String s : TextUtil.split(options.get(++i), File.pathSeparatorChar)) {
					try {
						compiler.addLibrary(new JarLibrary(new File(s)));
					} catch (IOException e) {
						e.printStackTrace();
						return false;
					}
				}
			}
		}

		CompileContext.set(compiler.createContext());
		List<? extends ClassDefinition> result;
		try {
			result = compiler.compile(files);
		} finally {
			CompileContext.set(null);
		}

		super.summary();
		System.out.println(new Text(buf).bgColor16(result != null ? Tty.BLUE : Tty.RED).color16(Tty.WHITE+Tty.HIGHLIGHT).toAnsiString());
		buf._free();
		if (result == null) return false;
		Flow.of(result).map(ClassResource::fromDefinition).forEach(classes::add);
		return true;
	}

	@Override
	public Boolean apply(roj.compiler.diagnostic.Diagnostic diag) {
		if (diag.getKind() == Kind.ERROR || !factory.ignoredDiagnostics.contains(diag.getCode())) {
			Boolean apply = super.apply(diag);
			if (showErrorCode) System.out.println(diag.getCode());
			return apply;
		} else {
			total++;
			return false;
		}
	}
}