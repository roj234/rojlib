package ilib.asm.FeOrg;

import com.google.common.collect.Lists;
import ilib.asm.Loader;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ITypeDiscoverer;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Nixim("/")
abstract class JarDiscoverer extends net.minecraftforge.fml.common.discovery.JarDiscoverer {
	@Copy(staticInitializer = "init")
	static Matcher cfMatcher;
	@Copy
	static boolean inited;
	private static void init() {
		cfMatcher = ITypeDiscoverer.classFile.matcher("");
	}
	@Copy
	public static void IL_preinit() {
		inited = true;
	}

	@Inject("/")
	public List<ModContainer> discover(ModCandidate candidate, ASMDataTable table) {
		List<ModContainer> found = Lists.newArrayList();

		try (ZipFile jar = new ZipFile(candidate.getModContainer())) {
			ZipEntry modInfo = jar.getEntry("mcmod.info");
			MetadataCollection mc;
			if (modInfo != null) {
				try (InputStream in = jar.getInputStream(modInfo)) {
					mc = MetadataCollection.from(in, candidate.getModContainer().getName());
				}
			} else {
				mc = MetadataCollection.from(null, "");
			}
			findClassesASM_my(candidate, table, jar, found, mc);
		} catch (Exception e) {
			FMLLog.log.warn("文件 {} 无法读取", candidate.getModContainer().getName(), e);
		}

		if (!inited) Loader.handleASMData(table);
		else FMLLog.bigWarning("在preInit之后加载mod: " + candidate.getModContainer());

		return found;
	}

	@Copy
	private void findClassesASM_my(ModCandidate candidate, ASMDataTable table, ZipFile jar, List<ModContainer> foundMods, MetadataCollection mc) throws IOException {
		Enumeration<? extends ZipEntry> ee = jar.entries();
		while (ee.hasMoreElements()) {
			ZipEntry ze;
			try {
				ze = ee.nextElement();
			} catch (IllegalArgumentException e) {
				throw new RuntimeException("非UTF-8编码的ZIP文件 " + jar.getName() + " 中出现了非ASCII字符的文件名");
			}
			String name = ze.getName();

			if (name.startsWith("__MACOSX")) continue;
			if (name.endsWith(".class") && cfMatcher.reset(name).matches()) {
				try (InputStream in = jar.getInputStream(ze)) {
					ASMModParser mp = new ASMModParser(in);

					mp.validate();
					mp.sendToTable(table, candidate);

					ModContainer c = ModContainerFactory.instance().build(mp, candidate.getModContainer(), candidate);
					if (c != null) {
						table.addContainer(c);
						foundMods.add(c);
						c.bindMetadata(mc);
						c.setClassVersion(mp.getClassVersion());
					}

					candidate.addClassEntry(name);
				} catch (LoaderException e) {
					FMLLog.log.error("无法加载 " + candidate.getModContainer().getName() + "#!" + name + " - 也许文件损坏了", e);
					jar.close();
					throw e;
				}
			}
		}
	}
}
