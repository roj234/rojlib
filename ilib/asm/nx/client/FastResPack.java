package ilib.asm.nx.client;

import roj.archive.zip.ZipArchive;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.MyHashSet;

import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.ResourcePackFileNotFoundException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * @author Roj233
 * @since 2022/5/20 21:31
 */
@Nixim("/")
abstract class FastResPack extends FileResourcePack {
	@Copy(unique = true)
	private volatile ZipArchive mzf;
	@Shadow
	private ZipFile resourcePackZipFile;
	@Copy(unique = true)
	private MyHashSet<String> set;

	public FastResPack(File file) {
		super(file);
	}

	@Copy(unique = true)
	private ZipArchive getZip() throws IOException {
		if (this.mzf == null) {
			synchronized (this) {
				if (this.mzf == null) {
					this.mzf = new ZipArchive(this.resourcePackFile, ZipArchive.FLAG_BACKWARD_READ);
				}
			}
		}

		return this.mzf;
	}

	@Inject("/")
	protected InputStream getInputStreamByName(String name) throws IOException {
		InputStream in = getZip().getStream(name);
		if (in == null) {
			throw new ResourcePackFileNotFoundException(this.resourcePackFile, name);
		} else {
			return in;
		}
	}

	@Inject("/")
	public boolean hasResourceName(String name) {
		try {
			return this.getZip().getEntries().containsKey(name);
		} catch (IOException e) {
			return false;
		}
	}

	@Inject("/")
	public Set<String> getResourceDomains() {
		if (set != null) return set;

		ZipArchive mzf;
		try {
			mzf = this.getZip();
		} catch (IOException e) {
			return Collections.emptySet();
		}

		MyHashSet<String> path = new MyHashSet<>();
		for (String name : mzf.getEntries().keySet()) {
			if (name.startsWith("assets/")) {
				int j = name.indexOf('/', 7);
				if (j < 0) continue;
				String id = name.substring(7, j);
				if (!id.isEmpty()) {
					if (id.equals(id.toLowerCase(Locale.ROOT))) {
						path.add(id);
					} else {
						this.logNameNotLowercase(id);
					}
				}
			}
		}

		return set = path;
	}

	@Inject(value = "/", at = Inject.At.REMOVE)
	protected void finalize() throws IOException {
		System.err.println("MOJANG IS SB");
	}

	@Inject("/")
	public void close() throws IOException {
		if (this.resourcePackZipFile != null) {
			this.resourcePackZipFile.close();
			this.resourcePackZipFile = null;
		}
		if (mzf != null) {
			mzf.close();
			mzf = null;
		}
	}
}
