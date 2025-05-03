package roj.plugins.ci;

import roj.asmx.ClassResource;
import roj.collect.SimpleList;
import roj.config.data.CMap;
import roj.reflect.Bypass;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * @author Roj234
 * @since 2024/6/24 17:24
 */
public interface Compiler {
	Factory factory();
	List<? extends ClassResource> compile(List<String> options, List<File> sources, boolean showDiagnosticId);

	/**
	 * @author Roj234
	 * @since 2025/2/12 13:19
	 */
	class Factory {
		final Set<String> ignoredDiagnostics;
		final List<String> options;
		final BiFunction<Factory, String, ? extends Compiler> supplier;

		@SuppressWarnings("unchecked")
		public Factory(CMap config) throws ClassNotFoundException {
			ignoredDiagnostics = config.getList("ignoredDiagnostics").toStringSet();
			options = config.getList("options").toStringList();
			supplier = Bypass.builder(BiFunction.class).constructFuzzy(Class.forName(config.getString("type")), "apply").build();
		}

		public SimpleList<String> getDefaultOptions() {return new SimpleList<>(options);}
		public Compiler getInstance(String basePath) {return supplier.apply(this, basePath);}
	}
}