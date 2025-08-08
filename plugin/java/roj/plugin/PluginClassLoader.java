package roj.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.asmx.ConstantPoolHooks;
import roj.asmx.Context;
import roj.asmx.Transformer;
import roj.asmx.injector.CodeWeaver;
import roj.asmx.launcher.MainM;
import roj.collect.HashSet;
import roj.collect.ArrayList;
import roj.io.source.FileSource;
import roj.text.URICoder;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/12/25 15:48
 */
public class PluginClassLoader extends ClassLoader {
	public static final ThreadLocal<PluginClassLoader> PLUGIN_CONTEXT = new ThreadLocal<>();

	final PluginDescriptor desc;
	final PluginDescriptor[] accessible;
	final ZipFile archive;

	final List<Transformer> transformers = new ArrayList<>();
	ConstantPoolHooks hooks;
	CodeWeaver weaver;

	public PluginClassLoader(ClassLoader parent, PluginDescriptor plugin, PluginDescriptor[] accessible) throws IOException {
		super(parent);
		this.desc = plugin;
		this.accessible = accessible;
		this.archive = new ZipFile(plugin.source, ZipFile.FLAG_VERIFY|ZipFile.FLAG_BACKWARD_READ, plugin.charset);
		this.archive.reload();

		if (Jocker.class.getModule().isNamed() && Jocker.useModulePluginIfAvailable) {
			var packages = new HashSet<String>();
			for (ZEntry entry : this.archive.entries()) {
				String name = entry.getName();
				if (name.startsWith("META-INF/")) continue;
				if (!name.endsWith(".class")) continue;
				packages.add(name.substring(0, name.lastIndexOf('/')).replace('/', '.'));
			}

			if (!packages.isEmpty()) {
				//ClassNode moduleInfo = Parser.parse(IOUtil.read(getResourceAsStream("module-info.class")));

				if (plugin.isModulePlugin) {
					var depend = new HashSet<>(plugin.javaModuleDepend);
					depend.add("java.base");
					depend.add("roj.core");

					var builder = ModuleDescriptor.newModule(plugin.id).packages(packages);
					for (String require : depend) builder.requires(require);
					try {
						var m = MainM.defineSubModule(builder.build(), Jocker.class.getModule().getLayer(), this, URI.create("panger://plugin/"+plugin.fileName));
						for (String require : depend) MainM.doAddReads(m.getLayer(), m, require);
					} catch (Throwable e) {
						throw new RuntimeException(e);
					}
				} else {
					var builder = ModuleDescriptor.newAutomaticModule(plugin.id).packages(packages);
					try {
						var m = MainM.defineSubModule(builder.build(), Jocker.class.getModule().getLayer(), this, URI.create("panger://plugin/legacy/"+plugin.fileName));
						for (var ml : m.getLayer().parents()) {
							for (Module require : ml.modules()) {
								MainM.doAddReads(m.getLayer(), m, require.getName());
							}
						}
					} catch (Throwable e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
	}

	public ConstantPoolHooks getHooks() {
		if (hooks == null) {
			hooks = new ConstantPoolHooks();
			transformers.add(hooks);
		}
		return hooks;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		String klass = name.replace('.', '/').concat(".class");
		ZEntry entry = archive.getEntry(klass);
		if (entry == null) {
			for (var s : accessible) {
				if (s.classLoader != null && s.classLoader.archive.getEntry(klass) != null) {
					return s.classLoader.findClass(name);
				}
			}
			throw new ClassNotFoundException(name);
		}

		PluginClassLoader prev = PLUGIN_CONTEXT.get();
		PLUGIN_CONTEXT.set(this);
		try {
			var ctx = new Context(klass, archive.getStream(entry));
			name = ctx.getClassName();

			var cs = new CodeSource(getUrl(klass), (CodeSigner[]) null);

			for (Transformer transformer : transformers) {
				transformer.transform(name, ctx);
			}
			PanSecurityManager.transformer.transform(name, ctx);

			ByteList buf = ctx.get();
			return defineClass(name.replace('/', '.'), buf.list, 0, buf.wIndex(), new ProtectionDomain(cs, null));
		} catch (Exception e) {
			throw new ClassNotFoundException("failed", e);
		} finally {
			if (prev == null) PLUGIN_CONTEXT.remove();
			else PLUGIN_CONTEXT.set(prev);
		}
	}

	@Override
	protected URL findResource(String name) {
		if (archive.getEntry(name) != null) {
			try {
				return getUrl(name);
			} catch (MalformedURLException ignored) {}
		}
		return null;
	}

	@NotNull
	private URL getUrl(String name) throws MalformedURLException {
		return new URL("jar:file:/"+(desc.source instanceof FileSource fs ? fs.getFile().getAbsolutePath() : desc.fileName)+"!/"+ URICoder.encodeURI(name));
	}

	@Override
	protected Enumeration<URL> findResources(String name) { return archive.getEntry(name) != null ? Collections.enumeration(Collections.singleton(getResource(name))) : Collections.emptyEnumeration(); }

	@Nullable
	@Override
	public InputStream getResourceAsStream(String name) {
		ZEntry entry = archive.getEntry(name);
		if (entry == null) return getParent().getResourceAsStream(name);
		try {
			return archive.getStream(entry);
		} catch (Exception e) {
			return null;
		}
	}

	public void close() throws IOException { archive.close(); }
}