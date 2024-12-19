package roj.plugins.share;

import roj.collect.CollectionX;
import roj.collect.MyHashMap;
import roj.config.ConfigMaster;
import roj.config.auto.Serializer;
import roj.config.auto.SerializerFactory;
import roj.net.http.IllegalRequestException;
import roj.net.http.server.DiskFileInfo;
import roj.net.http.server.Response;
import roj.plugin.Panger;
import roj.plugin.PathIndexRouter;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.ui.terminal.Argument;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/12/19 23:02
 */
@SimplePlugin(id = "fileShare", version = "1.0", desc = "直链共享: fileshare <path> <url>")
public class FileShare extends Plugin {
	private Map<String, ShareInfo> shares;
	private Serializer<Map<String, ShareInfo>> ser;
	private File dataFile;
	static class ShareInfo {
		File path;
		String code;
		long expire;

		public ShareInfo() {}
		public ShareInfo(File path) {this.path = path;}
	}

	@Override
	protected void onEnable() throws Exception {
		dataFile = new File(Panger.getInstance().getPluginFolder(), "Core/fileShare.yml");
		SerializerFactory.SAFE.serializeCharListToString().serializeFileToString();
		ser = SerializerFactory.SAFE.mapOf(ShareInfo.class);
		shares = dataFile.isFile() ? ConfigMaster.YAML.readObject(ser, dataFile) : new MyHashMap<>();

		for (var itr = shares.entrySet().iterator(); itr.hasNext(); ) {
			var entry = itr.next();
			var url = entry.getKey();
			var share = entry.getValue();
			if (share.expire != 0) {
				long expire = share.expire - System.currentTimeMillis();
				if (expire < 0) {
					itr.remove();
					continue;
				}
				getScheduler().delay(() -> removeShare(url), expire);
			}

			var path = share.path;
			addShare(path, url);
		}

		registerCommand(literal("fileshare")
			.then(literal("remove").fastFail().then(argument("网页路径", Argument.oneOf(CollectionX.toMap(shares.keySet()))).executes(ctx -> {
				removeShare(ctx.argument("网页路径", String.class));
			})))
			.then(argument("网页路径", Argument.string()).then(argument("文件路径", Argument.path()).executes(ctx -> {
				String url = ctx.argument("网页路径", String.class);
				File path = ctx.argument("文件路径", File.class);
				var share = new ShareInfo(path);

				synchronized (shares) {
					if (null != shares.putIfAbsent(url, share)) {
						getLogger().warn("直链/share/{}已存在", url);
						return;
					}
					ConfigMaster.YAML.writeObject(ser, shares, dataFile);
				}
				addShare(path, url);
				getLogger().info("直链/share/{}注册成功", url);
			}))));
	}

	private void removeShare(String url) throws IOException {
		ShareInfo info;
		synchronized (shares) {
			info = shares.remove(url);
			if (info == null) return;
			ConfigMaster.YAML.writeObject(ser, shares, dataFile);
		}

		unregisterRoute(info.path.isDirectory() ? "share/"+url+"/" : "share/"+url);
		getLogger().info("移除了直链{}", url);
	}

	private void addShare(File path, String url) {
		if (path.isDirectory()) {
			registerRoute("share/"+url+"/", new PathIndexRouter(path), "PermissionManager");
		} else {
			DiskFileInfo info = new DiskFileInfo(path, true);
			registerRoute("share/"+url, (req, rh) -> {
				if (req.path().isEmpty()) return Response.file(req, info);
				throw IllegalRequestException.NOT_FOUND;
			}, "PermissionManager");
		}
	}

}