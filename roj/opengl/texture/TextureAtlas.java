package roj.opengl.texture;

import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.math.MathUtils;
import roj.opengl.util.Util;
import roj.opengl.vertex.VertexBuilder;
import roj.util.DirectByteList;
import roj.util.Helpers;

import java.awt.image.*;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;

/**
 * @author Roj233
 * @since 2021/9/18 15:46
 */
public class TextureAtlas {
	private final AtlasMap uvPosition = new AtlasMap();
	private int textureId;
	private FastStitcher stitcher;
	private boolean baked;

	public TextureAtlas() {
		this(4096);
	}

	public TextureAtlas(int maxWH) {
		maxWH = MathUtils.getMin2PowerOf(maxWH);
		textureId = -1;
		stitcher = new FastStitcher(maxWH, maxWH, 0, 0);
	}

	public void register(String name, BufferedImage img) {
		if (stitcher == null) throw new IllegalStateException("Atlas locked and disposed necessary for adding new textures");
		if (uvPosition.containsKey(name)) throw new IllegalStateException("Texture " + name + " already exists");
		img = new BufferedImage(img.getColorModel(), img.copyData(null), false, null);
		uvPosition.put(name, img);
		Sprite entry = (Sprite) uvPosition.getEntry(name);
		entry.w = img.getWidth();
		entry.h = img.getHeight();
		stitcher.addSprite(entry);
		baked = false;
	}

	public void bake() {
		if (baked) return;
		if (stitcher == null) throw new IllegalStateException("Atlas locked and disposed necessary for adding new textures");
		stitcher.stitch();

		int prev = glGetInteger(GL_TEXTURE_BINDING_2D);
		if (textureId == -1) {
			textureId = glGenTextures();
			Util.bindTexture(textureId);

			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);

			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
			glTexParameteri(GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);
		}

		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, stitcher.getWidth(), stitcher.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
		Util.bindTexture(textureId);

		DirectByteList nm = TextureManager.tryLockAndGetBuffer();
		Set<Sprite> set = Helpers.cast(uvPosition.entrySet());
		for (Sprite piece : set) {
			int a = TextureManager.copyImageToNative(nm, piece.v, 0);
			glTexSubImage2D(GL_TEXTURE_2D, 0, piece.x, piece.y, piece.w, piece.h, a >>> 16, a & 0xFFFF, nm.nioBuffer());
		}
		TextureManager.unlockAndReturnBuffer(nm);

		GL30.glGenerateMipmap(GL_TEXTURE_2D);

		Util.bindTexture(prev);
		baked = true;
	}

	public void lock() {
		for (Map.Entry<String, BufferedImage> entry : uvPosition.entrySet()) {
			entry.setValue(null);
		}
		stitcher = null;
	}

	public void delete() {
		lock();
		if (textureId >= 0) {
			glDeleteTextures(textureId);
			textureId = -1;
		}
		uvPosition.clear();
	}

	public int texture() {
		if (!baked) throw new IllegalStateException("Not baked");
		return textureId;
	}
	public void bind() {
		Util.bindTexture(texture());
	}

	private static final class AtlasMap extends MyHashMap<String, BufferedImage> {
		@Override
		protected Entry<String, BufferedImage> createEntry(String id) {
			return new Sprite(id);
		}
	}

	public static final class Sprite extends MyHashMap.Entry<String, BufferedImage> implements IAtlasPiece {
		int x,y,w,h;
		float u1, v1, u2, v2;

		public Sprite(String k) {
			super(k, Helpers.cast(IntMap.UNDEFINED));
		}

		public float getUMin() {
			return u1;
		}
		public float getVMin() {
			return v1;
		}
		public float getUMax() {
			return u2;
		}
		public float getVMax() {
			return v2;
		}

		@Override
		public int getPieceWidth() {
			return w;
		}
		@Override
		public int getPieceHeight() {
			return h;
		}

		@Override
		public String getPieceName() {
			return getKey();
		}

		@Override
		public void onStitched(int atlasW, int atlasH, float atlasU1, float atlasV1, int x, int y, int actualW, int actualH, boolean rotated) {
			this.x = x;
			this.y = y;
			w = Math.min(actualW, w);
			h = Math.min(actualH, h);
			u1 = atlasU1 *x;
			u2 = atlasU1 *(x+w);
			v1 = atlasV1 *y;
			v2 = atlasV1 *(y+h);
		}


		public static final byte faceDown = 0b01001011;
		public static final byte faceUp = (byte) 0b10110100;
		public static final byte faceNorth = 0b00011110;
		public static final byte faceSouth = (byte) 0b11100001;
		public static final byte faceWest = (byte) 0b10000111;
		public static final byte faceEast = 0b01111000;
		public static final int[] faces = {faceDown,faceUp,faceNorth,faceSouth,faceWest,faceEast};

		public void appendToRenderer(VertexBuilder vb, int xSize, int ySize, int zSize, int uv, int color) {
			//vb.pos(1,1,1)
			vb.pos(-xSize, -ySize, -zSize).tex(uv&1, (uv>>>1)&1).color(color).endVertex();
			vb.pos(-xSize, -ySize, zSize).tex((uv>>>2)&1, (uv>>>3)&1).color(color).endVertex();
			vb.pos(xSize, -ySize, zSize).tex((uv>>>4)&1, (uv>>>5)&1).color(color).endVertex();
			vb.pos(xSize, -ySize, -zSize).tex((uv>>>6)&1, (uv>>>7)&1).color(color).endVertex();
		}
	}
}
