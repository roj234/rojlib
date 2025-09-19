package roj.reflect.resolver;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassDefinition;
import roj.asm.ClassNode;
import roj.collect.HashMap;
import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.util.FastFailException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2025/09/19 19:51
 */
public class SimpleResolver implements IResolver {
	private final Map<CharSequence, LinkedClass> definitions = new HashMap<>();
	private final Function<CharSequence, LinkedClass> resolveToNode;

	public SimpleResolver(ClassLoader classLoader) {
		this.resolveToNode = s -> {
			String typeName = s.toString().concat(".class");
			try (InputStream in = classLoader == null ? ClassLoader.getSystemResourceAsStream(typeName) : classLoader.getResourceAsStream(typeName)) {
				if (in != null)
					return new LinkedClass(ClassNode.parseSkeleton(IOUtil.getSharedByteBuf().readStreamFully(in).toByteArray()));
			} catch (IOException ignored) {}
			return null;
		};
	}

	@Override public synchronized ClassNode resolve(CharSequence name) {
		LinkedClass linkedClass = definitions.computeIfAbsent(name, resolveToNode);
		return linkedClass == null ? null : linkedClass.owner;
	}
	@Override public synchronized @NotNull ToIntMap<String> getHierarchyList(ClassDefinition info) {return definitions.computeIfAbsent(info.name(), resolveToNode).getHierarchyList(this);}

	private static final class LinkedClass {
		final ClassNode owner;

		private ToIntMap<String> hierarchyList;
		private boolean query;

		public LinkedClass(ClassNode owner) {this.owner = owner;}

		public ToIntMap<String> getHierarchyList(IResolver ctx) {
			if (hierarchyList != null) return hierarchyList;

			if (query) throw new FastFailException("semantic.resolution.cyclicDepend:"+owner.name());
			query = true;

			ToIntMap<String> list = new ToIntMap<>();

			ClassDefinition info = owner;
			while (true) {
				String owner = info.name();
				try {
					int i = list.size();
					list.putInt(owner, (i << 16) | i);
				} catch (IllegalArgumentException e) {
					throw new FastFailException("semantic.resolution.cyclicDepend:"+owner);
				}

				owner = info.parent();
				if (owner == null) break;
				info = ctx.resolve(owner);
				if (info == null) throw new FastFailException("symbol.noSuchClass:"+owner);
			}

			info = owner;
			int castDistance = 1;
			int justAnId = list.size();
			while (true) {
				List<String> itf = info.interfaces();
				for (int i = 0; i < itf.size(); i++) {
					String name = itf.get(i);

					ClassDefinition itfInfo = ctx.resolve(name);
					if (itfInfo == null) break;

					list.putInt(name, (justAnId++ << 16) | castDistance);

					for (var entry : ctx.getHierarchyList(itfInfo).selfEntrySet()) {
						name = entry.getKey();
						int id = entry.value;

						// id's castDistance is smaller
						// parentList是包含自身的
						if ((list.getInt(name)&0xFFFF) > (id&0xFFFF)) {
							list.putInt(name, (castDistance == 1 ? 0x80000000 : 0) | (justAnId++ << 16) | (castDistance + (id & 0xFFFF)));
						}
					}
				}

				castDistance++;
				String owner = info.parent();
				if (owner == null) break;
				info = ctx.resolve(owner);
				if (info == null) throw new FastFailException("symbol.noSuchClass:"+owner);
			}

			query = false;
			return this.hierarchyList = list;
		}
	}
}
