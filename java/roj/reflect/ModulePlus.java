package roj.reflect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.module.ModuleDescriptor;
import java.net.URI;

/**
 * @author Roj234
 * @since 2024/11/7 0007 22:24
 */
public interface ModulePlus {
	ModulePlus INSTANCE = Bypass.builder(ModulePlus.class).construct(Module.class, "createModule").access(Class.class, "module", null, "setModule").build();

	Module createModule(@Nullable ModuleLayer layer,
						@Nullable ClassLoader loader,
						@NotNull ModuleDescriptor descriptor,
						@NotNull URI uri);

	void setModule(Class<?> klass, Module module);
}
