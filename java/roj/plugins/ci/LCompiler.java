package roj.plugins.ci;

import roj.asmx.ClassResource;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.compiler.JavaCompileUnit;
import roj.compiler.LavaCompiler;
import roj.compiler.diagnostic.Kind;
import roj.compiler.diagnostic.TextDiagnosticReporter;
import roj.config.ParseException;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.ui.Terminal;
import roj.ui.Text;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2025/4/4 1:01
 */
public final class LCompiler extends TextDiagnosticReporter implements Compiler {
	private final LavaCompiler compiler = new LavaCompiler();

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

	public synchronized List<? extends ClassResource> compile(List<String> options, List<File> sources, boolean showDiagnosticId) {
		if (sources.isEmpty()) return Collections.emptyList();

		total = err = warn = 0;
		showErrorCode = showDiagnosticId;
		buf = new CharList(1024);

		List<CompileUnit> files = new ArrayList<>();
		for (File source : sources) {
			try {
				files.add(JavaCompileUnit.create(source.getName(), IOUtil.readString(source)));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		CompileContext.set(compiler.createContext());
		block:
		try {
			for (int i = files.size() - 1; i >= 0; i--) {
				// 如果解析失败或者不需要解析
				if (!files.get(i).S1parseStruct()) files.remove(i);
			}
			if (compiler.hasError()) break block;

			compiler.getParsableUnits(files);
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S2p1resolveName();
			}
			if (compiler.hasError()) break block;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S2p2resolveType();
			}
			if (compiler.hasError()) break block;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S2p3resolveMethod();
			}
			if (compiler.hasError()) break block;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S3processAnnotation();
			}
			if (compiler.hasError()) break block;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S4parseCode();
			}
		} catch (ParseException e) {
			e.printStackTrace();
		} finally {
			CompileContext.set(null);
		}

		boolean isOK = !compiler.hasError();

		super.summary();
		System.out.println(new Text(buf).bgColor16(isOK ? Terminal.BLUE : Terminal.RED).color16(Terminal.WHITE + Terminal.HIGHLIGHT).toAnsiString());
		buf._free();
		return isOK ? files : null;
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