package roj.sql;

import roj.asmx.injector.Inject;
import roj.asmx.injector.Shadow;
import roj.asmx.injector.Weave;
import roj.asmx.launcher.Autoload;
import roj.collect.ArrayList;
import roj.collect.IntBiMap;
import roj.collect.ListMap;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2025/08/24 05:35
 */
@Autoload(Autoload.Target.NIXIM)
@Weave("roj.plugins.web.error.GreatErrorPage")
final class JDBCErrorType {
	@Shadow
	public static void addCustomTag(String tag, Function<Object, Map<String, ?>> fn) {}

	@Inject(at = Inject.At.TAIL)
	public static void registerCustomTag() {
		addCustomTag("QUERIES", req -> {
			QueryBuilder inst = QueryBuilder.INSTANCE.get();
			if (inst == null) return null;

			List<String> values = new ArrayList<>(inst.logs);
			IntBiMap<String> index = new IntBiMap<>(values.size());
			for (int i = 0; i < values.size(); ) index.put(i, String.valueOf(++i));
			return new ListMap<>(index, values);
		});
	}
}
