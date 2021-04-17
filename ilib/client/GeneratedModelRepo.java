package ilib.client;

import ilib.Config;
import ilib.ImpLib;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.IOUtil;

import net.minecraft.block.Block;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.ResourcePackFileNotFoundException;
import net.minecraft.util.ResourceLocation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class GeneratedModelRepo extends AbstractResourcePack {
	private static final Map<String, Object> data = new MyHashMap<>();
	private static final Set<String> domains = new MyHashSet<>();

	public static void addModel() {
		GeneratedModelRepo repo = new GeneratedModelRepo();
		data.put("pack.mcmeta", ("{\"pack\": {\"description\":\"IMPLIB生成的模型\"," + "\"pack_format\": 3}}").getBytes(StandardCharsets.UTF_8));
		TextureHelper.load(repo);
	}

	public static void addModel(String path, CharSequence data) {
		register(path, IOUtil.SharedCoder.get().encode(data));
	}

	public static void addModel(Block path, CharSequence data) {
		ResourceLocation k = path.getRegistryName();
		addModel("assets/" + k.getNamespace() + "/blockstates/" + k.getPath() + ".json", data);
	}

	public static void register(String path, byte[] b) {
		if (!path.startsWith("assets/")) {
			throw new IllegalArgumentException("无效的资源路径 " + path);
		}

		if ((Config.debug & 8) != 0) {
			File file = new File("model_export", path);
			file.getParentFile().mkdirs();
			try (FileOutputStream out = new FileOutputStream(file)) {
				out.write(b);
			} catch (IOException e) {
				ImpLib.logger().warn("无法保存文件{}到{}: {}", path, file, e);
			}
		}

		String domain = path.substring(7);
		domain = domain.substring(0, domain.indexOf('/'));

		domains.add(domain);
		data.put(path, b);
	}

	public static void register(String path, File b) {
		if (!path.startsWith("assets/")) {
			throw new IllegalArgumentException("无效的资源路径 " + path);
		}

		String domain = path.substring(7);
		domain = domain.substring(0, domain.indexOf('/'));

		domains.add(domain);
		data.put(path, b);
	}

	public static String registerFileTexture(String texture, File base) {
		if (texture.indexOf(':') != -1) return texture;

		File real = new File(base, texture);
		if (!real.isFile()) {
			ImpLib.logger().warn("File not found: " + real.getAbsolutePath());
			return "missingno";
		}

		domains.add("fake");
		data.put("assets/fake/textures/" + texture, real);

		return "fake:" + texture;
	}

	private GeneratedModelRepo() {
		super(new File("IL模型生成器"));
	}

	@Override
	protected InputStream getInputStreamByName(String name) throws IOException {
		Object buf = data.get(name);
		if (buf instanceof byte[]) return new ByteArrayInputStream((byte[]) buf);
		if (buf instanceof File) return new FileInputStream((File) buf);
		throw new ResourcePackFileNotFoundException(resourcePackFile, name);
	}

	@Override
	protected boolean hasResourceName(String key) {
		return data.containsKey(key);
	}

	@Override
	public Set<String> getResourceDomains() {
		return domains;
	}
}
