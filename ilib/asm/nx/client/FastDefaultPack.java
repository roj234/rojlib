package ilib.asm.nx.client;

import roj.archive.zip.ZipArchive;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.io.IOUtil;
import roj.util.Helpers;

import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.ResourceIndex;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj233
 * @since 2022/5/20 3:52
 */
@Nixim("/")
class FastDefaultPack extends DefaultResourcePack {
	@Copy(unique = true)
	private final ZipArchive zf;
	@Shadow
	private ResourceIndex resourceIndex;

	@Inject(value = "/", at = Inject.At.TAIL)
	FastDefaultPack(ResourceIndex _lvt_1_) throws IOException {
		super(null);

		zf = new ZipArchive(Helpers.getJarByClass(DefaultResourcePack.class), ZipArchive.FLAG_BACKWARD_READ);
	}

	@Inject
	private InputStream getResourceStream(ResourceLocation res) {
		String path = name(res);

		try {
			InputStream in = zf.getStream(path);
			if (in != null) return in;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		path = "/".concat(path);
		//if (hasNotFile(path)) return null;
		//Exception: continue
		return DefaultResourcePack.class.getResourceAsStream(path);
	}

	@Inject("/")
	public boolean resourceExists(ResourceLocation res) {
		if (resourceIndex.isFileExisting(res)) return true;
		String path = name(res);
		if (zf.getEntries().containsKey(path)) return true;

		path = "/".concat(path);
		return null != DefaultResourcePack.class.getResource(path);//!hasNotFile(path);
		//Exception: true
	}

    /*@Copy(unique = true)
    private static boolean hasNotFile(String path) throws IOException {
        URL url = DefaultResourcePack.class.getResource(path);
        return url == null || !shadow1(new File(url.getFile()), path);
    }

    @Shadow(value = "func_191384_a", owner = "net.minecraft.client.resources.FolderResourcePack")
    private static boolean shadow1(File a, String b) throws IOException { return false; }*/

	@Copy(unique = true)
	private static String name(ResourceLocation res) {
		return IOUtil.getSharedCharBuf().append("assets/").append(res.getNamespace()).append('/').append(res.getPath()).toString();
	}
}
