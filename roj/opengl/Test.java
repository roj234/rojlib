package roj.opengl;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import roj.collect.SimpleList;
import roj.config.data.CMapping;
import roj.io.FileUtil;
import roj.math.Vec3f;
import roj.opengl.render.SkyRenderer;
import roj.opengl.texture.TextureAtlas;
import roj.opengl.texture.TextureManager;
import roj.opengl.util.Util;
import roj.opengl.util.VboUtil;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;
import roj.text.TextUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * @author Roj233
 * @since 2021/9/19 13:50
 */
public class Test extends Game {
	TextureAtlas atlas = new TextureAtlas();
	boolean tPressed;

	public static void main(String[] args) throws LWJGLException, IOException {
		Game game = new Test();
		game.width = 854;
		game.height = 548;
		game.x = -2;
		game.y = 1;
		game.z = -5;
		game.yaw = 180;

		CMapping cfg = new CMapping();
		cfg.put("title", "VisuAlgo");
		cfg.put("MSAA", 2);
		game.create(cfg);
		while (!Display.isCloseRequested()) {
			game.mainLoop();
		}
	}

	protected void init() {
		FileUtil.findAllFiles(new File("D:\\mc\\FMD-1.5.2\\projects\\mi\\resources"), file -> {
			if (file.getName().endsWith(".png")) {
				String path = file.getAbsolutePath();
				if (!path.contains("gui") && !path.contains("sky"))
				try {
					atlas.register(path, new TextureManager().readShared(new FileInputStream(file)));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return false;
		});

		lock.acquireUninterruptibly();
		try {
			d.releaseContext();
			Display.makeCurrent();

			atlas.bake();

			Display.releaseContext();
		} catch (LWJGLException e) {
			e.printStackTrace();
		}
		lock.release();

		atlas.lock();
		Util.sharedVertexBuilder = vb;
	}

	@Override
	protected void renderLoading() {
		super.renderLoading();
	}

	int id = 0;
	SimpleInputBox box;
	@Override
	public void processInput() {
		if (box != null) {
			while (Mouse.next()) {
				if (Mouse.getEventButtonState()) {
					box.mouseDown(Mouse.getEventX(), height-Mouse.getEventY(), Mouse.getEventButton());
				}
			}
			while (Keyboard.next()) {
				if (Keyboard.getEventKeyState()) {
					box.keyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey());
				}
			}

			return;
		}

		if (activeKeys.getOrDefaultInt(20, 0) == 1) {
			tPressed = !tPressed;
		}
		try {
			// p
			if (activeKeys.getOrDefaultInt(25, 0) == 1) {
				box = new SimpleInputBox(180, 64, width - 40, 20, "") {
					@Override
					protected void onChange(String value) {
						try {

						} catch (Exception e) {
							e.printStackTrace();
						}
						box = null;
						closeGUI();
					}

					@Override
					protected boolean isValidText(String text) {
						return TextUtil.isNumber(text) == 0;
					}
				};
				box.setFocused(true);
				box.setPlaceholder("删除的序号");
				openGUI();
			}

		} catch (Throwable e) {
			log("Exception: " + e.getMessage());
			e.printStackTrace();
		}

		super.processInput();
	}

	List<String> logs = new SimpleList<>();
	int logsTimer;
	private void log(String s) {
		logs.add(s);
	}

	@Override
	protected void renderSky() {
		glCullFace(GL11.GL_BACK);
		SkyRenderer.renderStar();
		glCullFace(GL11.GL_FRONT);
	}

	@Override
	protected void renderUI() {
		super.renderUI();
		if (box != null) {
			fr.renderStringWithShadow("在此输入文字: ", 0, 64);
			box.render(Mouse.getX(),height-Mouse.getY());
		}

		if (!logs.isEmpty()) {
			fr.renderStringWithShadow("Logs: ", width-24, 48);
			int off = 68;
			for (int i = logs.size() - 1; i >= 0; i--) {
				fr.renderStringWithShadow(logs.get(i), width-fr.getStringWidth(logs.get(i)), off);
				off += 18;
			}
			if (logsTimer++ == 600 || logs.size() > 50) logs.remove(0);
		} else {
			logsTimer = 0;
		}

		glDisable(GL_TEXTURE_2D);
		glDisable(GL_BLEND);

		glBegin(GL_LINES);
		glColor4f(1,1,1,1);
		int CROSSHAIR_SIZE = 10;
		glVertex2i(width/2-CROSSHAIR_SIZE,height/2);
		glVertex2i(width/2+CROSSHAIR_SIZE,height/2);
		glVertex2i(width/2,height/2-CROSSHAIR_SIZE);
		glVertex2i(width/2,height/2+CROSSHAIR_SIZE);
		glEnd();

		glEnable(GL_TEXTURE_2D);
		glEnable(GL_BLEND);
	}

	@Override
	protected void render3D() {
		glDisable(GL_CULL_FACE);
		glDisable(GL_TEXTURE_2D);

		Util.drawXYZ(33);

		drawTower();

		glEnable(GL_TEXTURE_2D);
		if (tPressed) glDisable(GL_ALPHA_TEST);

		Util.bindTexture(atlas.texture());

		VertexBuilder vb = Util.sharedVertexBuilder;
		vb.begin(VertexFormat.POSITION_TEX_COLOR);
		int size = 10;
		int[] uv = new int[] {
			0,0,0,1,1,1,1,0
		};
		vb.pos(-size, -size, -size).tex(uv[0], uv[1]).color(-1).endVertex();
		vb.pos(-size, -size, size).tex(uv[2], uv[3]).color(-1).endVertex();
		vb.pos(size, -size, size).tex(uv[4], uv[5]).color(-1).endVertex();
		vb.pos(size, -size, -size).tex(uv[6], uv[7]).color(-1).endVertex();
		VboUtil.drawCPUVertexes(GL_QUADS, vb);

		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		// 先凑合吧
		glRotatef(180,0,0,1);
		fr.renderString("终于，字体渲染做好了 ABCDE abcde A B C D E", 0, -2.5f, -1);

		glDisable(GL_BLEND);

		glEnable(GL_CULL_FACE);
	}

	private void drawTower() {
		float rtri = 30 + (System.currentTimeMillis()%360_000 / 100f);
		glPushMatrix();
		//glTranslatef(4.0f, 0.5f, 6.0f);
		//glRotatef(rtri, 0.0f, 1.0f, 0.0f);

		vb.begin(VertexFormat.POSITION_COLOR);

		Vec3f center = new Vec3f(0.5f,0.5f,0.5f);
		Vec3f tmp1 = new Vec3f(), tmp2 = new Vec3f();
		for (int axis = 0; axis < 6; axis++) {
			for (int x = 0; x < 48; x++) {
				float xPct = (x-16) / 16f;
				for (int y = 0; y < 48; y++) {
					float yPct = (y-16) / 16f;

					vb.pos(xPct,yPct,0).color(255,0,0, 255).endVertex();
					vb.pos(0.5,0.5,0).color(255,0,0, 255).endVertex();
					VboUtil.drawCPUVertexes(GL_LINES, vb);
				}
			}
		}

		vb.end();
		glPopMatrix();
	}
}
