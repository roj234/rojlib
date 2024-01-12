package roj.platform;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.net.URIUtil;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
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
	private final ZipArchive archive;

	public PluginClassLoader(ClassLoader parent, PluginDescriptor plugin) throws IOException {
		super(parent);
		this.desc = plugin;
		this.archive = new ZipArchive(plugin.source, ZipArchive.FLAG_VERIFY|ZipArchive.FLAG_BACKWARD_READ, plugin.charset);
		this.archive.reload();
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		String klass = name.replace('.', '/').concat(".class");
		ZEntry entry = archive.getEntries().get(klass);
		if (entry == null) throw new ClassNotFoundException(name);

		PluginClassLoader prev = PLUGIN_CONTEXT.get();
		PLUGIN_CONTEXT.set(this);
		try {
			ByteList buf = new ByteList().readStreamFully(archive.getInput(entry));
			CodeSource cs = new CodeSource(new URL("jar:file:/"+desc.fileName+"!/"+URIUtil.encodeURI(klass)), (CodeSigner[]) null);

			DefaultPluginSystem.transform(name, buf);
			Class<?> clazz = defineClass(name, buf.list, 0, buf.wIndex(), new ProtectionDomain(cs, null));
			buf._free();
			return clazz;
		} catch (Exception e) {
			throw new ClassNotFoundException("failed", e);
		} finally {
			if (prev == null) PLUGIN_CONTEXT.remove();
			else PLUGIN_CONTEXT.set(prev);
		}
	}

	@Nullable
	@Override
	public URL getResource(String name) {
		if (archive.getEntries().containsKey(name)) {
			try {
				return new URL("jar:file:/"+desc.fileName+"!/"+URIUtil.encodeURI(name));
			} catch (MalformedURLException ignored) {}
		}
		return null;
	}

	@Override
	public Enumeration<URL> getResources(String name) { return archive.getEntries().containsKey(name) ? Collections.enumeration(Collections.singleton(getResource(name))) : Collections.emptyEnumeration(); }

	@Nullable
	@Override
	public InputStream getResourceAsStream(String name) {
		ZEntry entry = archive.getEntries().get(name);
		if (entry == null) return getParent().getResourceAsStream(name);
		try {
			return archive.getStream(entry);
		} catch (Exception e) {
			return null;
		}
	}

	public void close() throws IOException { archive.close(); }
}