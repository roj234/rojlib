package roj.platform;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.util.ByteList;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Enumeration;

/**
 * @author Roj234
 * @since 2023/12/25 0025 15:48
 */
public class PluginClassLoader extends ClassLoader {
	private final ClassLoader parent;
	private final ZipArchive archive;

	public PluginClassLoader(ClassLoader parent, PluginDescriptor plugin) throws IOException {
		super(parent);
		this.parent = parent;
		this.archive = new ZipArchive(plugin.source, ZipArchive.FLAG_VERIFY|ZipArchive.FLAG_BACKWARD_READ, plugin.charset);
		this.archive.reload();
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		ZEntry entry = archive.getEntries().get(name.replace('.', '/').concat(".class"));
		if (entry == null) throw new ClassNotFoundException(name);
		try {
			ByteList buf = new ByteList().readStreamFully(archive.getInput(entry));
			CodeSource cs = new CodeSource(new URL("file://"+archive.getFile().toString()+"v"+System.currentTimeMillis()), (CodeSigner[]) null);
			Class<?> clazz = defineClass(name, buf.list, 0, buf.wIndex(), new ProtectionDomain(cs, null));
			buf._free();
			return clazz;
		} catch (Exception e) {
			throw new ClassNotFoundException("failed", e);
		}
	}

	@Nullable
	@Override
	public URL getResource(String name) { throw new UnsupportedOperationException(); }

	@Override
	public Enumeration<URL> getResources(String name) { throw new UnsupportedOperationException(); }

	@Nullable
	@Override
	public InputStream getResourceAsStream(String name) {
		ZEntry entry = archive.getEntries().get(name);
		if (entry == null) return parent.getResourceAsStream(name);
		try {
			return archive.getStream(entry);
		} catch (Exception e) {
			return null;
		}
	}

	public void close() throws IOException { archive.close(); }
}
