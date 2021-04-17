package ilib.asm.FeOrg;

import com.google.common.collect.Lists;
import ilib.asm.Loader;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ContainerType;
import net.minecraftforge.fml.common.discovery.ITypeDiscoverer;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import net.minecraftforge.fml.relauncher.CoreModManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Nixim(value = "/", copyItf = true)
final class JarDiscoverer extends net.minecraftforge.fml.common.discovery.JarDiscoverer implements Runnable {
	@Copy(staticInitializer = "init", targetIsFinal = true)
	private static Matcher cfMatcher;
	@Copy
	private static byte inited;
	private static void init() {
		cfMatcher = ITypeDiscoverer.classFile.matcher("");
		Loader.EVENT_BUS.add("preInit", new JarDiscoverer());
	}
	@Copy
	public void run() { inited |= 1; }

	@Inject("/")
	public List<ModContainer> discover(ModCandidate candidate, ASMDataTable table) {
		if ((inited&2) == 0) {
			inited |= 2;
			for (String mod : CoreModManager.getIgnoredMods()) {
				discover(new ModCandidate(new File("*|NUL>CLASSPATHROOT"), new File("mods", mod), ContainerType.JAR), table);
			}
			inited |= 4;
		}

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

		if ((1&inited) == 0) Loader.handleASMData(table);
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

					// LoliASM workaround
					if ((inited&4) != 0)
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
