package roj.plugins.minecraft;

import roj.collect.IntList;
import roj.config.ConfigMaster;
import roj.io.IOUtil;
import roj.text.TextReader;
import roj.ui.Terminal;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * @author Roj234
 * @since 2024/8/22 17:37
 */
public class PicMap {
	public static final class MapRoot {
		final NbtMap data;
		final int DataVersion = 3953;
		public MapRoot(NbtMap data) {this.data = data;}
		MapRoot() {this.data = null;}
	}

	public static final class NbtMap {
		public final byte[] colors = new byte[16384];
		public String dimension;

		public boolean locked;
		public byte scale;
		public boolean trackingPosition, unlimitedTracking;
		public int xCenter, zCenter;
	}

	static int[] colors;
	static {
		var list = new IntList();
		try (var tr = TextReader.auto(new File("map_color.txt"))) {
			while (true) {
				String line = tr.readLine();
				if (line == null) break;
				int mainId = Integer.parseInt(line.substring(0, line.indexOf('\t')));
				for (int i = 0; i < 4; i++) list.add(getHex(tr));
			}
			colors = list.toArray();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private static int getHex(TextReader tr) throws IOException {
		var line = tr.readLine();
		return Integer.parseInt(line.substring(2), 16);
	}

	private static double getDistance(int c1, int c2) {
		double rmean = (((c1>>16)&255) + ((c2>>16)&255)) / 2.0;
		double r = (((c1>>16)&255) - ((c2>>16)&255));
		double g = (((c1>>>8)&255) - ((c2>>>8)&255));
		int b = (c1&255) - (c2&255);
		double weightR = 2.0 + rmean / 256.0;
		double weightG = 4.0;
		double weightB = 2.0 + (255.0 - rmean) / 256.0;
		return weightR * r * r + weightG * g * g + weightB * b * b;
	}

	public static BufferedImage resizeImage(Image image, int w, int h) {
		var out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		var g2d = out.createGraphics();
		g2d.drawImage(image, 0, 0, w, h, null);
		g2d.dispose();
		return out;
	}

	public static byte getNearest(int color) {
		if ((color >>> 24) < 128) return 0;

		int index = 0;
		double best = -1.0;

		for(int i = 4; i < colors.length; ++i) {
			double distance = getDistance(color, colors[i]);
			if (distance < best || best == -1.0) {
				best = distance;
				index = i;
			}
		}

		return (byte)(index < 128 ? index : -129 + (index - 127));
	}


	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.out.println("请在第一个参数放入一张图片！");
			return;
		}

		File file = new File(args[0]);
		File out = new File(file.getParentFile(), IOUtil.fileName(args[0])+".dat");

		convertMap(file, out);
	}

	private static void convertMap(File in, File out) throws IOException {
		var image = ImageIO.read(in);
		if (image.getWidth() != 128 || image.getHeight() != 128) {
			Terminal.warning("图片必须是128x128的,已经拉伸或压缩以适应");
			image = resizeImage(image, 128, 128);
		}

		NbtMap map = new NbtMap();
		map.dimension = "minecraft:overworld";
		map.locked = true;
		for (int y = 0; y < 128; y++) {
			for (int x = 0; x < 128; x++) {
				map.colors[y * 128 + x] = getNearest(image.getRGB(x, y));
			}
		}

		try (var os = new GZIPOutputStream(new FileOutputStream(out))) {
			ConfigMaster.NBT.writeObject(new MapRoot(map), os);
		}
	}
}
