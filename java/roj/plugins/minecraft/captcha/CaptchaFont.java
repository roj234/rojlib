package roj.plugins.minecraft.captcha;

import roj.collect.BitArray;
import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.math.Vec3d;
import roj.math.Vec4d;
import roj.plugins.minecraft.server.data.Block;
import roj.plugins.minecraft.server.data.BlockSet;
import roj.plugins.minecraft.server.data.world.World;
import roj.text.LineReader;

import java.util.List;
import java.util.Random;

/**
 * 验证码字体
 * @author Roj234
 * @since 2024/3/19 0019 16:38
 */
public final class CaptchaFont {
	final String id;
	final Char[] chars;

	private CaptchaFont(String id, int len) {
		this.id = id;
		this.chars = new Char[len];
	}

	public Captcha generate(Random rnd, BlockSet blockset, int length) {
		MyBitSet[] choices = new MyBitSet[length];
		World world = new World();

		world.createPhysicalBorder(8, 0, 127, Block.getBlock("minecraft:barrier"));

		int[] state = blockset.createState(rnd);

		Block sinState = blockset.getNextState(state, 0, rnd);
		for (int i = -128; i < 128; i++) {
			int y = (int) ((Math.sin(i / 256d * Math.PI)+1) * 96);
			world.setBlock(i, y, i, sinState);
		}

		int x_offset = 64;
		final int x_alignment = 8;

		Vec4d point = new Vec4d();
		Vec4d rotation = new Vec4d();
		for (int i = 0; i < length; i++) {
			int selectionAxis = rnd.nextInt(2);
			Vec3d axis = new Vec3d(0, selectionAxis==1?1:0, selectionAxis==0?1:0);

			double angle = Math.PI / 2 * rnd.nextDouble();
			rotation.makeRotation(angle, axis);
			System.out.println(Math.toDegrees(angle));

			Char c = chars[rnd.nextInt(chars.length)];
			choices[i] = c.choice;

			Block[] blocks = new Block[c.typeCount];
			for (int j = 0; j < blocks.length; j++)
				blocks[j] = blockset.getNextState(state, j, rnd);

			int off = 0;
			for (int y = 0; y < c.height; y++) {
				for (int x = 0; x < c.width; x++) {
					Block block = blocks[c.data.get(off++)];
					point.set(-x, -y, 0, 0).applyRotation(rotation);
					world.setBlock(x_offset + (int)(point.x), 32 + (int)(point.y), (int)(point.z), block);
				}
				if (point.x != point.x) System.out.println("isNaN: "+rotation);
			}
			x_offset -= c.width + x_alignment;
		}

		return new Captcha(id, choices, world);
	}

	static final class Char {
		MyBitSet choice;
		BitArray data;
		int typeCount;
		int width, height;
	}

	public static CaptchaFont[] load(CMap data) {
		List<CEntry> cfgFonts = data.getList("fonts").raw();
		CaptchaFont[] fonts = new CaptchaFont[cfgFonts.size()];
		for (int i = 0; i < cfgFonts.size(); i++) {
			CMap cfgFont = cfgFonts.get(i).asMap();
			List<CEntry> cfgChars = cfgFont.getList("chars").raw();
			CaptchaFont font = fonts[i] = new CaptchaFont(cfgFont.getString("id"), cfgChars.size());
			for (int j = 0; j < cfgChars.size(); j++) {
				CMap cfgXCaptcha = cfgChars.get(j).asMap();
				Char c = font.chars[j] = new Char();
				c.choice = MyBitSet.from(cfgXCaptcha.asMap().getString("choice"));

				String text = cfgXCaptcha.asMap().getString("text");
				Int2IntMap types = new Int2IntMap();

				List<String> lines = LineReader.getAllLines(text, false);

				for (String line : lines) {
					c.width = Math.max(c.width, line.length());
					for (int k = 0; k < line.length(); k++) {
						types.putIntIfAbsent(line.charAt(k), types.size());
					}
				}
				if (types.containsKey('\n')) System.out.println("why?");
				c.height = lines.size();
				c.typeCount = types.size();
				c.data = new BitArray(32-Integer.numberOfLeadingZeros(types.size()), c.width * c.height);

				int off = 0;
				for (int k = 0; k < lines.size(); k++) {
					String line = lines.get(k);
					for (int i1 = 0; i1 < line.length(); i1++) c.data.set(off++, types.getOrDefaultInt(line.charAt(i1), 0));
					for (int i1 = line.length(); i1 < c.width; i1++) c.data.set(off++, types.getOrDefaultInt(' ', 0));
				}
			}
		}
		return fonts;
	}
}