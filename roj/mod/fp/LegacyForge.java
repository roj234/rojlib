package roj.mod.fp;

import roj.archive.qz.xz.LZMAInputStream;
import roj.archive.zip.ZipFileWriter;
import roj.asm.type.Desc;
import roj.asm.util.Context;
import roj.asmx.mapper.Mapper;
import roj.asmx.mapper.Mapping;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.TrieTree;
import roj.collect.TrieTreeSet;
import roj.concurrent.task.AsyncTask;
import roj.mod.FMDInstall;
import roj.mod.FMDMain;
import roj.mod.Shared;
import roj.mod.mapping.ClassMerger;
import roj.mod.mapping.GDiffPatcher;
import roj.mod.mapping.MappingFormat;
import roj.ui.CLIUtil;
import roj.ui.EasyProgressBar;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipFile;

import static roj.mod.Shared.TMP_DIR;

/**
 * @author Roj234
 * @since 2020/8/30 11:31
 */
public final class LegacyForge extends WorkspaceBuilder {
	@Override
	public String getId() {
		return "legacy_forge";
	}

	private Map<Desc, List<String>> paramMap;

	public LegacyForge() {}

	@Override
	protected void loadLibraries1(File root, TrieTree<String> artifacts) {
		skipLib = new MyHashSet<>();

		String forge = get1(artifacts, "net/minecraftforge");
		skipLib.add(forge);

		File forgeFile = new File(root, forge);
		file.putInt(10, forgeFile);
	}

	public void run() {
		GDiffPatcher patcher = new GDiffPatcher();
		File forgeJar = file.get(10);
		try (ZipFile zf = new ZipFile(forgeJar)) {
			patcher.setup112(zf.getInputStream(zf.getEntry("binpatches.pack.lzma")));
		} catch (Exception e) {
			error = 1;
			throw new IllegalStateException("readBinPatch 失败", e);
		}

		EasyProgressBar bar = new EasyProgressBar("Prepare");

		List<Context>[] arr = Helpers.cast(new List<?>[4]);
		arr[0] = new ArrayList<>();

		AsyncTask<List<Context>> prepareClient = new AsyncTask<>(() -> {
			List<Context> list = Context.fromZip(file.get(0));

			bar.addMax(list.size());
			for (int i = 0; i < list.size(); i++, bar.addCurrent(1)) {
				Context ctx = list.get(i);
				ByteList result = patcher.patchClient(ctx.getFileName(), ctx.get());
				if (result != null) {
					synchronized (arr[0]) { arr[0].add(new Context(ctx.getFileName(), ctx.get(false))); }
					ctx.set(result);
				}
				ctx.getData();
			}
			patcher.patchClientEmpty(list);
			System.out.println("prepareClient: done");

			return list;
		});
		AsyncTask<List<Context>> prepareServer = new AsyncTask<>(() -> {
			TrieTreeSet set = new TrieTreeSet();
			FMDMain.readTextList(set::add, "忽略服务端jar中以以下文件名开头的文件");

			List<Context> list = Context.fromZip(file.get(1), name -> !set.strStartsWithThis(name));

			bar.addMax(list.size());
			for (int i = 0; i < list.size(); i++, bar.addCurrent(1)) {
				Context ctx = list.get(i);
				ByteList result = patcher.patchServer(ctx.getFileName(), ctx.get());
				if (result != null) {
					synchronized (arr[0]) { arr[0].add(new Context(ctx.getFileName(), ctx.get(false))); }
					ctx.set(result);
				}
				ctx.getData();
			}
			patcher.patchServerEmpty(list);
			System.out.println("prepareServer: done");

			return list;
		});
		AsyncTask<List<Context>> prepareMapping = new AsyncTask<>(() -> {
			try (ZipFile zf = new ZipFile(forgeJar)) {
				Mapping forgeSrg = new Mapping();
				forgeSrg.loadMap(new LZMAInputStream(zf.getInputStream(zf.getEntry("deobfuscation_data-"+mf_cfg.get("version")+".lzma"))), false);
				mf_cfg.put("map.legacy_forge", forgeSrg);
			}

			bar.addMax(100);
			List<Context> list = Context.fromZip(forgeJar);

			MappingFormat.MapResult maps = mf.map(mf_cfg, TMP_DIR);
			bar.addCurrent(100);

			mf = null;
			mf_cfg = null;

			maps.tsrgCompile.saveMap(FMDInstall.MCP2SRG_PATH);
			mapper.merge(maps.tsrgDeobf, true);
			paramMap = maps.paramMap;
			System.out.println("prepareMapping: done");

			return list;
		});

		Shared.Task.pushTask(prepareClient);
		Shared.Task.pushTask(prepareServer);
		Shared.Task.pushTask(prepareMapping);
		try {
			arr[1] = prepareClient.get();
			arr[2] = prepareServer.get();
			arr[3] = prepareMapping.get();
		} catch (InterruptedException | ExecutionException e) {
			e.getCause().printStackTrace();
			bar.end("失败");
			error = 1;
			return;
		}

		if (patcher.errorCount != 0) CLIUtil.warning("补丁失败数量: " + patcher.errorCount);
		patcher.reset();

		bar.end("完成");

		error = post_process(arr, mapper, paramMap) ? 0 : 6;
	}

	// arr[1] = client, arr[2] = server, arr[3] = forge
	static boolean post_process(List<Context>[] arr, Mapper mapper, Map<Desc, List<String>> paramMap) {
		ClassMerger merger = new ClassMerger();

		List<Context> merged = new SimpleList<>(merger.process(arr[1], arr[2]));
		merged.addAll(arr[3]);

		CLIUtil.info("SCB=" + merger.serverOnly + "/" + merger.clientOnly + "/" + merger.both +
			", FMR=" + merger.mergedField + "/" + merger.mergedMethod + "/" + merger.replaceMethod);

		// arr[0]是未打补丁的文件
		mapper.loadLibraries(Arrays.asList(arr[0], merged));
		mapper.setParamMap(paramMap);

		long start = System.nanoTime();
		mapper.map(merged);
		long end = System.nanoTime();

		merged.sort((o1, o2) -> o1.getFileName().compareTo(o2.getFileName()));

		CLIUtil.info(merged.size()+"个文件在"+(end-start)/1000000+"ms内映射成功");

		try (ZipFileWriter zfw = new ZipFileWriter(destination)) {
			for (int i = 0; i < merged.size(); i++) {
				Context ctx = merged.get(i);
				if (ctx.getFileName().startsWith("net/minecraft/") && ctx.getFileName().endsWith("package-info.class")) continue;

				ByteList list;
				try {
					list = ctx.getCompressedShared();
				} catch (Throwable e) {
					CLIUtil.warning(ctx.getFileName() + " 验证失败", e);
					continue;
				}
				zfw.writeNamed(ctx.getFileName(), list);
			}
			merged.clear();
			CLIUtil.info("writeFile done");
		} catch (IOException e) {
			CLIUtil.error("writeFile 失败", e);
			return false;
		}
		return true;
	}
}