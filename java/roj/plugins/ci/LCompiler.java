package roj.plugins.ci;

import roj.asmx.ClassResource;
import roj.collect.SimpleList;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.JavaCompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.diagnostic.TextDiagnosticReporter;
import roj.compiler.plugin.GlobalContextApi;
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
	private final GlobalContextApi ctx = new GlobalContextApi();

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

		List<CompileUnit> files = new SimpleList<>();
		for (File source : sources) {
			try {
				files.add(JavaCompileUnit.create(source.getName(), IOUtil.readString(source)));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		var lc = ctx.createLocalContext();
		LocalContext.set(lc);
		block:
		try {
			for (int i = files.size() - 1; i >= 0; i--) {
				// 如果解析失败或者不需要解析
				if (!files.get(i).S1_Struct()) files.remove(i);
			}
			if (ctx.hasError()) break block;

			ctx.addGeneratedCompileUnits(files);
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S2_ResolveName();
			}
			if (ctx.hasError()) break block;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S2_ResolveType();
			}
			if (ctx.hasError()) break block;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S2_ResolveMethod();
			}
			if (ctx.hasError()) break block;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S3_Annotation();
			}
			if (ctx.hasError()) break block;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S4_Code();
			}
		} catch (ParseException e) {
			e.printStackTrace();
		} finally {
			LocalContext.set(null);
		}

		boolean isOK = !ctx.hasError();

		super.printSum();
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