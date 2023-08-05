package roj.misc;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.collect.IntBiMap;
import roj.collect.SimpleList;
import roj.config.JSONParser;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.data.Type;
import roj.math.Vec3f;
import roj.math.Vec3i;
import roj.math.Vec4d;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2023/2/1 0001 0:22
 */
public class RaytraceCulling {
	static final class Box {
		public float fx, fy, fz;
		public float tx, ty, tz;

		public final CMapping[] faces = new CMapping[6];
		public final int[] cullface = new int[6];

		void rotate(double rotation, double ox, double oy, double oz, String axis1) {
			Vec3i axis = new Vec3i();
			switch (axis1) {
				case "x": axis.x = 1; break;
				case "y": axis.y = 1; break;
				case "z": axis.z = 1; break;
			}
			Vec4d rot = new Vec4d().makeRotation(Math.toRadians(rotation), axis);
			Vec4d pos = new Vec4d(ox, oy, oz, 0);

			Vec3f from = (Vec3f) new Vec3f(fx,fy,fz).sub(pos, 3);
			Vec3f to = (Vec3f) new Vec3f(tx,ty,tz).sub(pos, 3);

			pos.applyRotation(rot);
			from.add(pos, 3);
			to.add(pos, 3);

			fx = from.x;
			fy = from.y;
			fz = from.z;
			tx = to.x;
			ty = to.y;
			tz = to.z;
		}

		void moveToRelative() {
			fx /= 16;
			fy /= 16;
			fz /= 16;
			tx /= 16;
			ty /= 16;
			tz /= 16;
		}

		public void save() {
			for (int i = 0; i < faces.length; i++) {
				CMapping f = faces[i];
				if (f != null) {
					int cull = cullface[i];
					if (cull != 0) {
						f.put("cullface", pos2id.get(cull));
					}
				}
			}
		}
	}

	public static void main(String[] args) throws Exception {
		Matcher MODEL = Pattern.compile("assets/[a-z0-9]+?/models/(.+?)\\.json").matcher("");
		try(ZipArchive mzf = new ZipArchive(new File(args[0]))) {
			for (Map.Entry<String, ZEntry> entry : mzf.getEntries().entrySet()) {
				if (MODEL.reset(entry.getKey()).matches()) {
					CMapping data = new JSONParser().parseRaw(mzf.getStream(entry.getValue())).asMap();
					if (!data.containsKey("elements")) continue;
					List<Box> model = loadModel(data.getOrCreateList("elements"));
					raytraceCull(model);
					for (Box box : model) box.save();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static List<Box> loadModel(CList cuboids) {
		List<Box> models = new SimpleList<>();
		for (int i = 0; i < cuboids.size(); i++) {
			Box model = new Box();
			models.add(model);

			CMapping map = cuboids.get(i).asMap();
			CList from = map.getOrCreateList("from");
			model.fx = (float) from.get(0).asDouble();
			model.fy = (float) from.get(1).asDouble();
			model.fz = (float) from.get(2).asDouble();

			CList to = map.getOrCreateList("to");
			model.tx = (float) to.get(0).asDouble();
			model.ty = (float) to.get(1).asDouble();
			model.tz = (float) to.get(2).asDouble();

			if (map.containsKey("rotation")) {
				CMapping rotation = map.getOrCreateMap("rotation");
				CList origin = rotation.getOrCreateList("origin");
				model.rotate(rotation.getDouble("angle"),
					origin.get(0).asDouble(), origin.get(1).asDouble(), origin.get(2).asDouble(),
					rotation.getString("axis"));
			}

			CMapping faces = map.getOrCreateMap("faces");
			for (Map.Entry<String, CEntry> entry : faces.entrySet()) {
				CMapping map1 = entry.getValue().asMap();
				int faceid = pos2id.getInt(entry.getKey());
				model.faces[faceid] = map1;

				CEntry entry1 = map1.get("cullface");
				if (entry1.getType() == Type.STRING) {
					model.cullface[faceid] = 1 << pos2id.getInt(entry1.asString());
				} else {
					model.cullface[faceid] = 0;
					CList cullfaces = entry1.asList();
					for (int j = 0; j < cullfaces.size(); j++) {
						model.cullface[faceid] |= 1 << pos2id.getInt(cullfaces.get(j).asString());
					}
				}
			}
		}
		return models;
	}

	static final IntBiMap<String> pos2id = new IntBiMap<>();
	static {
		map(0, "down", new Vec3i(0, -1, 0));
		map(1, "up", new Vec3i(0, 1, 0));
		map(2, "north", new Vec3i(0, 0, -1));
		map(3, "south", new Vec3i(0, 0, 1));
		map(4, "west", new Vec3i(-1, 0, 0));
		map(5, "east", new Vec3i(1, 0, 0));
	}
	private static void map(int i, String down, Vec3i i1) {
		pos2id.putInt(i, down);
	}

	private static void raytraceCull(List<Box> model) {
		Vec3f center = new Vec3f(0.5f,0.5f,0.5f);
		Vec3f tmp1 = new Vec3f(), tmp2 = new Vec3f();
		for (int axis = 0; axis < 6; axis++) {
			for (int x = 0; x < 48; x++) {
				float xPct = (x-16) / 16f;
				for (int y = 0; y < 48; y++) {
					float yPct = (y-16) / 16f;

					for (int i = 0; i < model.size(); i++) {
						Box box = model.get(i);
					}
				}
			}
		}
	}
}
