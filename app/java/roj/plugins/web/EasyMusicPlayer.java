package roj.plugins.web;

import roj.config.serial.ToJson;
import roj.http.server.*;
import roj.http.server.auto.GET;
import roj.http.server.auto.Interceptor;
import roj.http.server.auto.OKRouter;
import roj.http.server.auto.POST;
import roj.plugin.Plugin;
import roj.text.URICoder;
import roj.text.TextWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * @author Roj234
 * @since 2024/11/28 5:32
 */
public class EasyMusicPlayer extends Plugin {
	@Override
	protected void onEnable() throws Exception {
		registerRoute("music/api/v2/", new OKRouter().register(this));
		//registerRoute("music/", new PathRouter());
	}

	@Interceptor
	public void userSongPostHandler(PostSetting setting) {
		if (setting != null && setting.postExceptLength() < 2097152) {
			setting.postHandler(new UrlEncodedParser());
			setting.postAccept(2097152, 2000);
		}
	}

	@Interceptor("userSongPostHandler")
	@POST("data/set/:key")
	public Content userSong(Request req) throws IOException {
		var file = new File(getDataFolder(), "users/"+ URICoder.escapeFilePath(req.argument("user"))+".json.gz");

		var handler = (UrlEncodedParser) req.postHandler();
		if (handler == null) {
			req.server().code(204);
			return null;
		} else {
			req.argument("user");
			try (var tw = new ToJson().sb(new TextWriter(new GZIPOutputStream(new FileOutputStream(file)), StandardCharsets.UTF_8))) {
				tw.valueMap();
				for (var entry : handler.fields.entrySet()) {
					tw.key(entry.getKey());
					tw.mapValue().append(entry.getValue().toString());
				}
				tw.pop();
			}

			return Content.json("{\"ok\":1}");
		}
	}

	@GET("data/get/:key?")
	public Content loadUserSong(Request req) throws IOException {
		var file = new File(getDataFolder(), "users/"+ URICoder.escapeFilePath(req.argument("user"))+".json.gz");

		if (!file.isFile()) return Content.json("{\"history\":[], \"playing\":[], \"custom_list\":null}");
		return Content.file(req, new GZFileInfo(file));
	}

	@GET("sync/netease/:uid(\\d+)")
	public void syncNeteaseList(Request req) {

	}

	@GET("playlist")
	public void getSongLists(Request req) {

	}

	@GET("playlist/:id")
	public void getSongList(Request req) {

	}

	@GET("song/:id/url")
	public void url(Request req) {

	}

	@GET("song/:id/cover")
	public void pic(Request req) {

	}

	@GET("song/:id/lyric")
	public void lyric(Request req) {

	}

	@GET("song/:id/delete")
	public void deleteFile(Request req) {

	}

	@GET("song/:source/search/:kind(name|album|artist)/:name/:page(\\d{1,3})?")
	public void search(Request req) {

	}
}
