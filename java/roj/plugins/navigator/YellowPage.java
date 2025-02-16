package roj.plugins.navigator;

import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZipFile;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.auto.Optional;
import roj.config.data.CMap;
import roj.config.table.TableParser;
import roj.config.table.TableReader;
import roj.config.table.TableWriter;
import roj.http.HttpUtil;
import roj.http.IllegalRequestException;
import roj.http.server.PostSetting;
import roj.http.server.Request;
import roj.http.server.ZipRouter;
import roj.http.server.auto.*;
import roj.io.IOUtil;
import roj.plugin.PermissionHolder;
import roj.plugin.Plugin;
import roj.text.CharList;
import roj.text.Formatter;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 网址导航插件.
 * 本地csv转换自<a href="https://github.com/hitokoto-osc/">Hitokoto-OSC</a>的语句库（AGPL），不过并不包含在公开的项目中
 * @author Roj234
 * @since 2025/3/10 0010 16:28
 */
public class YellowPage extends Plugin implements TableReader {
	private Plugin easySso;
	private Formatter index, site;
	private List<String> db;
	private int multiplier;

	@Override
	protected void onEnable() throws Exception {
		easySso = getPluginManager().getPluginInstance("EasySSO");

		CMap config = new CMap();//getConfig();
		config.put("path", "nav");

		ZipFile archive = getDescription().getArchive();
		registerRoute(config.getString("path"), new OKRouter().addPrefixDelegation("assets/", new ZipRouter(archive, "assets/")).register(this), "PermissionManager");

		index = Formatter.simple(IOUtil.readString(archive.getStream("index.html")));
		site = Formatter.simple(IOUtil.readString(archive.getStream("site.html")));

		db = new SimpleList<>();
		multiplier = ThreadLocalRandom.current().nextInt();
		File csvFile = new File(getDataFolder(), "motd.csv");

		TableParser.csvParser().table(csvFile, this);
		//try (var tr = TextReader.auto(csvFile)) {
		//	hitokotoCsvForm = ConfigMaster.CSV.readObject(SerializerFactory.SAFE.listOf(HitokotoCsv.class), tr);
		//}
	}

	@Optional
	public static class HitokotoCsv {
		String hitokoto, type, from, from_who, creator;
		int creator_uid;
		String commit_from;
		long created_at;
	}
	private List<HitokotoCsv> hitokotoCsvForm;

	@Override
	public void onRow(int rowNumber, List<String> row) {
		if (rowNumber == 1) return;
		if (row.size() < 3) {
			if (row.get(0).startsWith("v")) {
				getLogger().debug("数据库版本 {}", row.get(0));
				return;
			}
			db.add(row.get(0));
			return;
		}
		//hitokoto,type,from,from_who,creator,creator_uid,commit_from,created_at
		String s = row.size() < 4 || row.get(3) == null ? "" : row.get(3);
		db.add(row.get(2) == null ? row.get(0) : row.get(0)+"——"+ s +"《"+row.get(2)+"》");
	}

	private static String checkNumber(String id) {return TextUtil.isNumber(id, TextUtil.INT_MAXS) == 0 ? id : "-1";}

	@POST("set/:id(\\d+)")
	public String set(Request req) {
		var id = Integer.parseInt(checkNumber(req.argument("id")));
		ByteList byteList = req.postBuffer();
		String newHito = byteList.readUTF(byteList.readableBytes());
		db.set(id, newHito);
		return "ok";
	}

	@POST
	public String add(Request req) {
		ByteList byteList = req.postBuffer();
		String newHito = byteList.readUTF(byteList.readableBytes());
		db.add(newHito);
		return String.valueOf(db.size());
	}

	@GET("get/:id(\\d+)")
	@Mime("text/plain")
	public String get(Request req) throws IllegalRequestException {
		var id = Integer.parseInt(checkNumber(req.argument("id")));
		if (id < 0 || id >= db.size()) throw IllegalRequestException.BAD_REQUEST;

		req.responseHeader().put("cache-control", HttpUtil.IMMUTABLE);
		return db.get(id);
	}

	@GET("")
	public CharSequence index(Request req) throws IOException, ParseException {
		MyHashMap<String, Object> env = new MyHashMap<>();

		int i = ((int)(System.currentTimeMillis()/3600000) * multiplier) % db.size();
		if (i < 0) i = -i % db.size();

		int uid = getUID(req);
		File file;
		if (uid < 0 || !(file = new File(getDataFolder(), "data/"+uid+".csv")).isFile())
			file = new File(getDataFolder(), "def.csv");

		var sites = new CharList();
		TableParser.csvParser().table(file, (rowNumber, value) -> {
			env.put("name", value.get(0));
			env.put("url", value.get(1));
			String val = value.get(2);
			env.put("image", val == null ? "assets/img/default.png" : val);

			site.format(env, sites).append('\n');
		});

		env.put("hito_id", i);
		env.put("hito", db.get(i));
		env.put("sites", sites);

		CharList format = index.format(env, new CharList());
		sites._free();
		return format;
	}

	@POST
	public String reset(Request req) {
		int uid = getUID(req);
		if (uid < 0) return "请登录";
		File file = new File(getDataFolder(), "data/"+uid+".csv");
		return file.delete() ? "ok" : "fail";
	}

	@Interceptor public void save(PostSetting ps) {ps.postAccept(65536, 100);}
	@POST
	@Interceptor("save")
	@SuppressWarnings("unchecked")
	public String save(Request req) {
		int uid = getUID(req);
		if (uid < 0) return "请登录";

		try {
			List<List<String>> parse = (List<List<String>>) ConfigMaster.JSON.parse(req.postBuffer()).unwrap();
			IOUtil.writeFileEvenMoreSafe(getDataFolder(), "data/"+uid+".csv", file -> {
				try (var csv = TableWriter.csvWriter(file)) {
					for (List<String> strings : parse) {
						csv.writeRow(strings);
					}
				} catch (IOException e) {
					Helpers.athrow(e);
				}
			});
		} catch (IOException | ParseException | ClassCastException e) {
			return "数据格式错误";
		}


		return "ok";
	}

	@Nullable private PermissionHolder getUser(Request req) {return easySso == null ? null : easySso.ipc(new TypedKey<>("getUser"), req);}
	private int getUID(Request req) {var user = getUser(req);return user == null ? -1 : user.getId();}
}
