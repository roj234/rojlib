package roj.opengl;

import org.lwjgl.LWJGLException;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.glu.Project;
import roj.collect.Int2IntMap;
import roj.config.data.CMapping;
import roj.math.MathUtils;
import roj.opengl.text.FontTex;
import roj.opengl.text.TextRenderer;
import roj.opengl.texture.TextureManager;
import roj.opengl.util.Util;
import roj.opengl.util.VboUtil;
import roj.opengl.vertex.VertexBuilder;
import roj.opengl.vertex.VertexFormat;
import roj.reflect.TraceUtil;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.logging.Logger;
import roj.text.logging.LoggingStream;
import roj.util.DirectByteList;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_MULTISAMPLE;

/**
 * @author Roj233
 * @since 2021/9/17 23:15
 */
public abstract class Game {
	public static Game instance;

	static final float SQRT_2 = (float) Math.sqrt(2);

	protected int width = 512, height = 512;
	int bakWidth, bakHeight;

	public void create(CMapping cfg) throws LWJGLException, IOException {
		instance = this;
		startTime = System.currentTimeMillis();
		try {
			Display.setDisplayMode(new DisplayMode(width, height));
		} catch (LWJGLException ignored) {}
		Display.setResizable(true);
		Display.setTitle(cfg.getString("title", "LWJGL Window"));

		loadImage:
		try {
			InputStream in = Game.class.getClassLoader().getResourceAsStream(cfg.getString("image", "icon.png"));
			if (in == null) {
				ByteBuffer buf = ByteBuffer.allocate(0);
				Display.setIcon(new ByteBuffer[] {buf, buf});
				break loadImage;
			}
			BufferedImage img = ImageIO.read(in);

			DirectByteList nm = TextureManager.tryLockAndGetBuffer();
			int a = TextureManager.copyImageToNative(nm, img, 1);
			TextureManager.unlockAndReturnBuffer(nm);
			byte[] array = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
			ByteBuffer buf = ByteBuffer.allocate((array.length * 3) >> 2);
			for (int i = 0; i < array.length; i += 4) {
				buf.put(array, i, 3);
			}
			buf.rewind();
			// should use 16x16 + 32x32
			Display.setIcon(new ByteBuffer[] {buf, buf});
		} catch (Throwable ignored) {}

		try {
			// 4x MSAA
			Display.create(new PixelFormat(0,24,0).withSamples(cfg.getInteger("MSAA")));
		} catch (LWJGLException e) {
			e.printStackTrace();

			Display.create();
		}

		prevTime = precisionTime();
		fps = 60;
		vb = new VertexBuilder(262144);
		fr = new TextRenderer(new FontTex("黑体-28").antiAliasing(true).sameWidth("0123456789"), TextRenderer.COLOR_CODE, vb);

		LoggingStream out = new LoggingStream(Logger.getLogger());
		System.setOut(out);
		System.setErr(out);

		Thread.currentThread().setName("Renderer");

		d = new SharedDrawable(Display.getDrawable());
		Display.getDrawable().releaseContext();
		d.makeCurrent();

		glViewport(0, 0, width, height);
		glPushAttrib(GL_ENABLE_BIT);

		glEnable(GL_DEPTH_TEST);
		glDepthFunc(GL_LEQUAL);

		glEnable(GL_ALPHA_TEST);
		glAlphaFunc(GL_GEQUAL, 0.5f);

		glEnable(GL_TEXTURE_2D);

		Thread loader = new Thread(this::init);
		loader.setName("Computer");
		loader.start();

		while (loader.isAlive()) {
			if (Display.isCloseRequested()) System.exit(-1);

			glPushAttrib(GL_ENABLE_BIT);

			glClearDepth(1);
			glClearColor(1,1,1,1);
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

			glMatrixMode(GL_PROJECTION);
			glLoadIdentity();
			glOrtho(0, width, height, 0, -1000, 1000);

			glMatrixMode(GL_MODELVIEW);
			glLoadIdentity();
			glColor4f(0,0,0,1);

			fr.setFontSize(30);

			renderLoading();

			glPopAttrib();

			while (true) {
				try {
					if (lock.tryAcquire(100, TimeUnit.MILLISECONDS)) break;
				} catch (InterruptedException ignored) {}
				if (!loader.isAlive()) System.exit(-1);
			}
			Display.update();
			lock.release();

			Display.sync(10);
		}

		glPopAttrib();

		d.releaseContext();
		Display.getDrawable().makeCurrent();

		lock = null;
		d = null;

		Mouse.create();
		Keyboard.create();
	}

	protected Semaphore lock = new Semaphore(1);
	protected Drawable d;
	protected long startTime;
	protected void renderLoading() {
		String fakeLogo = "OJNG Studio";

		Random r = new Random(Math.min(System.currentTimeMillis()-startTime, 1919));
		fr.setFontSize(36);
		float fw = fr.getStringWidth(fakeLogo);
		glPushMatrix();
		glTranslatef(0,0,-1);
		for (int i = 40; i >= 0; i--) {
			int xpos = r.nextInt(height - 36);
			int ypos = r.nextInt((int) (width - fw));
			float rpos = r.nextFloat() * 360 - 180;
			glRotatef(rpos, 0, 0, 1);
			fr.renderString(fakeLogo, xpos, ypos, 0xFFEEEEEE);
		}
		glPopMatrix();

		Runtime m = Runtime.getRuntime();

		glDisable(GL_TEXTURE_2D);

		vb.begin(VertexFormat.POSITION_COLOR);
		Util.drawBar(vb, 8, 26, width-16, 24, 0xFF000000);
		Util.drawBar(vb, 10, 28, (int) ((width-28) * ((double)m.totalMemory() / m.maxMemory())), 20, -1);
		Util.drawBar(vb, 12, 30, (int) ((width-24) * ((double)(m.totalMemory()-m.freeMemory()) / m.maxMemory())), 16, 0xFFFF0000);
		VboUtil.drawCPUVertexes(GL_QUADS, vb);
		glEnable(GL_TEXTURE_2D);

		fr.setFontSize(24);

		CharList time = new ACalendar(TimeZone.getTimeZone("UTC")).format("H:i:s", System.currentTimeMillis()-startTime);
		fr.renderString(time, width-fr.getStringWidth(time), 0);

		fr.renderString("加载中", 0, 0);

		fr.renderStringCenterX("内存: " +
			TextUtil.scaledNumber(m.totalMemory()-m.freeMemory()) + "B / " +
			TextUtil.scaledNumber(m.totalMemory()) + "B / " + TextUtil.scaledNumber(m.maxMemory()) + "B", 26, width, 0xFF00FF00);

		int logoHeight = 72;
		fr.setFontSize(logoHeight);
		fr.renderStringWithShadow(fakeLogo, (width-fr.getStringWidth(fakeLogo)) / 2f, (height - logoHeight) / 2f, 0xFFCC0000);
	}

	static final float SYSTEM_UPDATE_MS = 50;
	private long prevTime;
	public float partialTick;
	public int tick;
	private static long precisionTime() {
		return Sys.getTime() * 1000L / Sys.getTimerResolution();
	}

	public TextRenderer fr;
	public VertexBuilder vb;


	public double prevX, prevY, prevZ, prevYaw, prevPitch;
	public double x, y, z, yaw, pitch, scale = 1;
	public double motionX, motionY, motionZ;

	private int moveForward, moveLeft, moveUp;
	public boolean cameraMode;
	public double mouseSensitive = .2, moveFactor = .03;

	public int fps;

	private int frameCounter;
	private float avgFrameTime;

	private boolean grabbed;
	private int baseX, baseY;
	private boolean guiOpen;

	private boolean fullscreen;
	protected final Int2IntMap activeKeys = new Int2IntMap();

	private void processMove() {
		if (moveForward != 0 || moveLeft != 0 || moveUp != 0) {
			double sy = MathUtils.sin(yaw);
			double cy = MathUtils.cos(yaw);
			double sp = MathUtils.sin(pitch);

			motionX += moveFactor * (moveLeft * cy - moveForward * sy);
			motionY += moveFactor * (moveUp + (cameraMode ? moveForward * sp : 0));
			motionZ += moveFactor * (moveForward * cy + moveLeft * sy);

			moveForward = moveLeft = moveUp = 0;
		}

		prevX = x;
		prevY = y;
		prevZ = z;

		x += motionX;
		y += motionY;
		z += motionZ;

		motionX *= 0.85;
		motionY *= 0.85;
		motionZ *= 0.85;
	}

	public void processInput() {
		// keyboard
		for (Int2IntMap.Entry e : activeKeys.selfEntrySet()) {
			switch (e.getIntKey()) {
				case 1:
					if (e.v == 1 && !grabbed) System.exit(0);
					grabbed = false;
					Mouse.setGrabbed(false);
					break;
				case 17:
					moveForward--;
					break;
				case 30:
					moveLeft--;
					break;
				case 31:
					moveForward++;
					break;
				case 32:
					moveLeft++;
					break;
				case 12:
					if (activeKeys.containsKey(13)) scale = 1;
					else if (scale > 0.25) scale -= 0.02;
					break;
				case 13:
					if (scale < 1.5f) scale += 0.02;
					break;
				case 57:
					moveUp++;
					break;
				case 42:
					moveUp--;
					break;
				case 87:
					if (e.v == 1) {
						try {
							fullscreen(!fullscreen);
						} catch (LWJGLException exception) {
							exception.printStackTrace();
						}
					}
					break;
			}
			e.v++;
		}
		// mouse
		prevYaw = yaw;
		prevPitch = pitch;
		if (grabbed) {
			int dx = Mouse.getX() - baseX;
			int dy = baseY - Mouse.getY();
			if (dx != 0 || dy != 0) {
				Mouse.setCursorPosition(width / 2, height / 2);
				double y = yaw + mouseSensitive * dx * Math.PI / 180;
				if (y < -Math.PI) {
					y += 2*Math.PI;
					prevYaw += 2*Math.PI;
				} else if (y > Math.PI) {
					y -= 2*Math.PI;
					prevYaw -= 2*Math.PI;
				} else if (y != y) y = 0;

				yaw = y;
				pitch = MathUtils.clamp(pitch + mouseSensitive * dy * Math.PI / 180, -Math.PI / 2, Math.PI / 2);
			}
		}
	}

	public void fullscreen(boolean full) throws LWJGLException {
		if (fullscreen != full) {
			fullscreen = full;
			if (full) {
				DisplayMode mode = Display.getDesktopDisplayMode();
				bakWidth = Display.getWidth();
				bakHeight = Display.getHeight();
				Display.setDisplayModeAndFullscreen(mode);
				width = mode.getWidth();
				height = mode.getHeight();
			} else {
				Display.setDisplayMode(new DisplayMode(width = bakWidth, height = bakHeight));
				Display.setFullscreen(false);
			}
			activeKeys.clear();
			Mouse.setCursorPosition(baseX = width / 2, baseY = height / 2);
		}
	}

	public boolean isFullscreen() {
		return fullscreen;
	}

	public void mainLoop() {
		long t = precisionTime();
		if (frameCounter > 49) frameCounter = 49;
		avgFrameTime = (avgFrameTime * frameCounter++ + (int) (t - prevTime)) / frameCounter;

		partialTick += (t - prevTime) / SYSTEM_UPDATE_MS;
		tick = (int)partialTick;
		partialTick -= tick;

		prevTime = t;
		if (tick > 0) {
			//System.out.println("渲染/计算同步 FG=" + avgFrameTime);
			tick = 0;

			if (!guiOpen) {
				boolean reqGrab = false;
				while (Mouse.next()) {
					if (Mouse.getEventButtonState()) {
						reqGrab = true;
						activeKeys.getEntryOrCreate(-Mouse.getEventButton() - 1, 1);
					} else {
						activeKeys.remove(-Mouse.getEventButton() - 1);
					}
				}
				if (reqGrab) {
					grabMouse();
				}
				while (Keyboard.next()) {
					if (Keyboard.getEventKeyState()) {
						activeKeys.getEntryOrCreate(Keyboard.getEventKey(), 1);
					} else {
						activeKeys.remove(Keyboard.getEventKey());
					}
				}
			}

			processInput();
			processMove();
		}

		if (Display.wasResized()) {
			width = Display.getWidth();
			height = Display.getHeight();
		}

		glPushAttrib(GL_ENABLE_BIT);

		glEnable(GL_MULTISAMPLE);

		glClearDepth(1);
		glClearColor(0,0,0,0);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		glEnable(GL_DEPTH_TEST);
		glDepthFunc(GL_LEQUAL);
		glDepthRange(0.05, 1);

		glEnable(GL_ALPHA_TEST);
		glAlphaFunc(GL_GREATER, 0.5f);

		glEnable(GL_CULL_FACE);
		glCullFace(GL_FRONT);

		glViewport(0, 0, width, height);

		glColor4f(1,1,1,1);

		glMatrixMode(GL_PROJECTION);
		glLoadIdentity();

		Project.gluPerspective((float) (70 * scale), (float) width / height, 0.05F, 512 * SQRT_2);

		glMatrixMode(GL_MODELVIEW);
		glLoadIdentity();

		glRotated(Math.toDegrees(MathUtils.interpolate(prevPitch, pitch, partialTick)), 1, 0, 0);
		glRotated(Math.toDegrees(MathUtils.interpolate(prevYaw, yaw, partialTick)), 0, 1, 0);
		glTranslatef(0, 0, 0.05F);

		glPushMatrix();

		checkGLError("Render Sky Before");
		renderSky();
		checkGLError("Render Sky After");

		glPopMatrix();

		glEnable(GL_TEXTURE_2D);

		glTranslated(
			-MathUtils.interpolate(prevX, x, partialTick),
			-MathUtils.interpolate(prevY, y, partialTick),
			-MathUtils.interpolate(prevZ, z, partialTick));

		glPushMatrix();
		fr.setFontSize(0.4f);
		checkGLError("Render 3D Before");
		render3D();
		checkGLError("Render 3D After");
		glPopMatrix();

		glDisable(GL_CULL_FACE);

		glMatrixMode(GL_PROJECTION);
		glLoadIdentity();
		glOrtho(0, width, height, 0, -1000, 1000);

		glMatrixMode(GL_MODELVIEW);
		glLoadIdentity();
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glColor4f(1,1,1,1);

		glDepthRange(0, 1);

		glClearDepth(1);
		glClear(GL_DEPTH_BUFFER_BIT);

		fr.setFontSize(22);

		checkGLError("Render 2D Before");
		renderUI();
		checkGLError("Render 2D After");

		glPopAttrib();

		Display.update();

		if (fps >= 0) Display.sync(fps);
	}

	private void grabMouse() {
		grabbed = true;
		Mouse.setGrabbed(true);
		Mouse.setCursorPosition(baseX = width / 2, baseY = height / 2);
	}

	public void openGUI() {
		guiOpen = true;
		if (grabbed) {
			Mouse.setGrabbed(false);
			grabbed = false;
		}
	}
	public void closeGUI() {
		guiOpen = false;
		if (!grabbed) {
			grabMouse();
		}
	}

	private final ArrayList<String> glErrors = new ArrayList<>();
	private int glErrorTimer;

	protected void checkGLError(String msg) {
		int id = glGetError();
		if (id != 0) {
			StackTraceElement[] es = TraceUtil.getTraces(new Throwable());
			String name = GLU.gluErrorString(id);
			String err = msg + ": " + name + ": " + es[es.length - 2];
			if (!glErrors.contains(err)) {
				glErrors.add(err);
			}
			System.out.println("########## GL ERROR ##########");
			System.out.println("@" + msg);
			System.out.println(id + ": " + name);
		}
	}

	protected abstract void init();

	protected void renderSky() {}
	protected void renderUI() {
		fr.renderStringWithShadow("FPS: " + (int)(1000 / avgFrameTime) + '/' + fps, 0, 0);
		fr.renderStringWithShadow("\u00a7aX\u00a7bY\u00a7cZ \u00a7a" + TextUtil.toFixed(x, 3) + " \u00a7b" + TextUtil.toFixed(y, 3) + " \u00a7c" + TextUtil.toFixed(z, 3), 0, 24);
		if (!glErrors.isEmpty()) {
			fr.renderStringWithShadow("OpenGL Errors: ", 0, 48);
			int off = 68;
			for (int i = glErrors.size() - 1; i >= 0; i--) {
				fr.renderStringWithShadow(glErrors.get(i), 0, off);
				off += 18;
			}
			if (glErrorTimer++ == 600 || glErrors.size() > 50) glErrors.remove(0);
		} else {
			glErrorTimer = 0;
		}
		fr.renderStringWithShadow("\u00a74Powered by Roj234", width-fr.getStringWidth("\u00a74Powered by Roj234"), height-20);
	}
	protected abstract void render3D();
}
