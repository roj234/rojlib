package roj.plugin;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.collect.MyHashSet;
import roj.reflect.ModulePlus;
import roj.text.Escape;
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

/**
 * @author Roj234
 * @since 2023/12/25 0025 15:48
 */
public class PluginClassLoader extends ClassLoader {
	public static final ThreadLocal<PluginClassLoader> PLUGIN_CONTEXT = new ThreadLocal<>();

	final PluginDescriptor desc;
	final PluginDescriptor[] accessible;
	final ZipFile archive;

	public PluginClassLoader(ClassLoader parent, PluginDescriptor plugin, PluginDescriptor[] accessible) throws IOException {
		super(parent);
		this.desc = plugin;
		this.accessible = accessible;
		this.archive = new ZipFile(plugin.source, ZipFile.FLAG_VERIFY|ZipFile.FLAG_BACKWARD_READ, plugin.charset);
		this.archive.reload();

		if (Panger.class.getModule().isNamed()) {
			var packages = new MyHashSet<String>();
			for (ZEntry entry : this.archive.entries()) {
				String name = entry.getName();
				if (!name.endsWith(".class")) continue;
				packages.add(name.substring(0, name.lastIndexOf('/')).replace('/', '.'));
			}

			if (!packages.isEmpty()) {

			}
			var md = ModuleDescriptor.newAutomaticModule(Escape.escapeFileName(plugin.fileName)).packages(packages).build();
			var module = ModulePlus.INSTANCE.createModule(null, this, md, URI.create("panger://plugin/"+plugin.fileName));
			System.out.println(module);
		}
	}

	@Override
	protected Class<?> findClass(String moduleName, String name) {
		System.out.println("moduleName = " + moduleName + ", name = " + name);
		try {
			return findClass(name);
		} catch (ClassNotFoundException e) {}
		return null;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		String klass = name.replace('.', '/').concat(".class");
		ZEntry entry = archive.getEntry(klass);
		if (entry == null) {
			for (var s : accessible) {
				if (s.cl != null && s.cl.archive.getEntry(klass) != null) {
					return s.cl.findClass(name);
				}
			}
			throw new ClassNotFoundException(name);
		}

		PluginClassLoader prev = PLUGIN_CONTEXT.get();
		PLUGIN_CONTEXT.set(this);
		try {
			ByteList buf = new ByteList().readStreamFully(archive.getStream(entry));
			var cs = new CodeSource(new URL("jar:file:/"+desc.fileName+"!/"+Escape.encodeURI(klass)), (CodeSigner[]) null);
			PanSecurityManager.transform(name, buf);
			var clazz = defineClass(name, buf.list, 0, buf.wIndex(), new ProtectionDomain(cs, null));
			buf._free();
			return clazz;
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
				return new URL("jar:file:/"+desc.fileName+"!/"+Escape.encodeURI(name));
			} catch (MalformedURLException ignored) {}
		}
		return null;
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