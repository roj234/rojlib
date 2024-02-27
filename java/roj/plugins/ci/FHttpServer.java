package roj.plugins.ci;

import roj.net.http.IllegalRequestException;
import roj.net.http.server.Request;
import roj.net.http.server.auto.Field;
import roj.net.http.server.auto.GET;
import roj.net.http.server.auto.POST;
import roj.text.TextWriter;

import java.io.File;
import java.nio.file.Files;

import static roj.plugins.ci.Shared.*;

/**
 * @author Roj234
 * @since 2024/8/11 0011 0:46
 */
class FHttpServer {
	@POST
	public void config__edit(Request req, String name, @Field(orDefault = "") String cfgFile) throws Exception {
		if (Project.matcher.pattern().matcher(name).matches())
			throw IllegalRequestException.badRequest("文件名不合法");

		File file = new File(PROJECT_DIR, name+"/project.json");
		if (cfgFile.isEmpty()) {
			// DELETE
			Files.deleteIfExists(file.toPath());
		} else {
			// ADD or EDIT
			Project.projects.remove(name);
			try (var out = TextWriter.to(file)) {
				out.append(cfgFile);
			}

			new File(BASE, "projects/"+name+"/java").mkdirs();
		}

		if (project != null && file.equals(project.getFile())) {
			if (cfgFile.isEmpty()) project = null;
			else project.reload();
		} else if (!cfgFile.isEmpty()) {
			// check validity (should be done by frontend...)
			Project.load(name);
			//
//			if (atInput.getText().length() > 0 && !WINDOWS_FILE_NAME.reset(atInput.getText()).matches()) {
//				error.add("AT文件名不合法");
//			}
//
//			try {
//				p.charset = Charset.forName(charsetInp.getText());
//			} catch (Throwable e1) {
//				error.add("编码不支持");
//			}
//
//			if (!MOD_VERSION.reset(verInp.getText()).matches()) {
//				error.add("版本不合法");
//			}
		}
	}

	@GET
	public void config__list(Request req) throws Exception {
		for (File file : PROJECT_DIR.listFiles()) {

		}
	}

	@GET
	public String config__current(Request req) {return project == null ? null : project.name;}
}
