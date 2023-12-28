package roj.mod.fp;

import roj.asm.util.Context;
import roj.collect.MyHashSet;
import roj.collect.TrieTree;
import roj.concurrent.OperationDone;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.ui.CLIUtil;
import roj.util.ByteList;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static roj.mod.Shared.CONFIG;
import static roj.mod.Shared.TMP_DIR;

/**
 * 1.13 or higher
 *
 * @author Roj234
 * @since 2020/8/30 11:31
 */
public final class Forge extends WorkspaceBuilder {
	@Override
	public String getId() {
		return "forge";
	}

	private static File mcpConfigFile;

	File forgeInstallerPath, forgeUniv, mcClearSrg;
	InputStream serverLzmaInput;

	Map<String, Map<String, List<String>>> paramMap;

	@Override
	public void loadLibraries(File root, Collection<String> libraries) {
		File specialSource;
		CharList forge = new CharList();

		for (String s : libraries) {
			if (s.startsWith("net/minecraftforge/forge/")) {
				if (s.endsWith("-universal.jar") || s.endsWith("-client.jar")) {
					continue;
				}

				forge.append(s);
				forge.setLength(forge.length() - 4);
				break;
			}
		}

		List<File> files = getSpecialSource(root);
		if (files.isEmpty()) {
			throw new IllegalArgumentException("未找到net/md-5/SpecialSource/*.jar");
		}

		int len = forge.length();

		if (len == 0) throw new IllegalArgumentException("未找到net/minecraftforge/forge/xxx/forge-xxx.jar");

		File srgCli = new File(root, forge.append("-client.jar").toString());

		File forgeInstaller;
		try {
			forge.setLength(len);
			String forgeInst = forge.append("-installer.jar").toString();
			String instUrl = CONFIG.getString("ForgeMaven仓库地址") + forgeInst;
			forgeInstaller = new File(TMP_DIR, forgeInst.substring(forgeInst.lastIndexOf('/') + 1));
			//MCLauncher.downloadAndVerifyMD5(instUrl, );
		} catch (Exception e) {
			CLIUtil.warning("文件下载失败, 请检查网络", e);
			System.exit(-4);
			return;
		}

		forge.setLength(len);
		File forgeNormal = new File(root, forge.append(".jar").toString());
		forge.setLength(len);
		File forgeUniversal = new File(root, forge.append("-universal.jar").toString());

		InputStream server_lzma;
		File mcClear;
		try {
			ZipFile zf = new ZipFile(forgeInstaller); // 顺便read一下
			ZipEntry ze = zf.getEntry("data/server.lzma");
			if (ze == null) throw OperationDone.INSTANCE;
			server_lzma = new ByteArrayInputStream(IOUtil.read(zf.getInputStream(ze)));

			ze = zf.getEntry("install_profile.json");
			if (ze == null) throw OperationDone.INSTANCE;
			CMapping installconf = JSONParser.parses(IOUtil.readUTF(zf.getInputStream(ze))).asMap();

			zf.close();

			String mcSrgClient = installconf.getDot("data.MC_SRG.client").asString();
			mcClear = new File(root, IOUtil.mavenPath(mcSrgClient.substring(1, mcSrgClient.length() - 1)).toString());
		} catch (IOException e) {
			throw new IllegalArgumentException("IO eror, forge安装器 '" + forgeInstaller.getAbsolutePath() + "'", e);
		} catch (OperationDone | ParseException e) {
			throw new IllegalArgumentException("forge安装器 '" + forgeInstaller.getAbsolutePath() + "' 有错误", e);
		}

		loadClass("net.md_5.specialsource.SpecialSource", files);
		this.file.putInt(0, srgCli);
		this.file.putInt(10, forgeNormal);
		this.file.putInt(11, forgeUniversal);
		/*this.mcJar = srgCli;
		this.forgeJar = forgeNormal; // normal*/
		this.forgeUniv = forgeUniversal; // univer
		this.serverLzmaInput = server_lzma;
		this.mcClearSrg = mcClear;
	}

	@Nonnull
	private static List<File> getSpecialSource(File librariesPath) {
		return IOUtil.findAllFiles(librariesPath, (file -> {
			String n = file.getName();
			// f@@k them ALL
			return n.startsWith("SpecialSource") || n.startsWith("asm-") || n.startsWith("guava-") || n.startsWith("jopt-simple-");
		}));
	}

	@Override
	void loadLibraries1(File root, TrieTree<String> artifacts) {
		skipLib = new MyHashSet<>(artifacts.valueMatches("net/minecraftforge/forge/", 99));
	}

	public Forge() {}

	public void run() {

	}

	private static List<Context> readZipReplaced(Charset charset, File mcJar, File forgeReplaced) throws IOException {
		MyHashSet<String> map = new MyHashSet<>();
		List<Context> ctx = new ArrayList<>();
		ByteList bl = new ByteList();

		ZipFile zf = new ZipFile(forgeReplaced, charset);
		Enumeration<? extends ZipEntry> en = zf.entries();
		while (en.hasMoreElements()) {
			ZipEntry zn;
			try {
				zn = en.nextElement();
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("可能是编码错误! 请指定编码", e);
			}
			if (zn.isDirectory()) continue;
			if (zn.getName().endsWith(".class")) {
				map.add(zn.getName());
				InputStream in = zf.getInputStream(zn);
				Context c = new Context(zn.getName().replace('\\', '/'), bl.readStreamFully(in).toByteArray());
				in.close();
				bl.clear();
				ctx.add(c);
			}
		}

		zf.close();
		zf = new ZipFile(mcJar, charset);
		en = zf.entries();
		while (en.hasMoreElements()) {
			ZipEntry zn;
			try {
				zn = en.nextElement();
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("可能是编码错误! 请指定编码", e);
			}
			if (zn.isDirectory()) continue;
			if (zn.getName().endsWith(".class") && map.add(zn.getName())) {
				InputStream in = zf.getInputStream(zn);
				Context c = new Context(zn.getName().replace('\\', '/'), bl.readStreamFully(in).toByteArray());
				in.close();
				bl.clear();
				ctx.add(c);
			}
		}

		zf.close();

		return ctx;
	}
}