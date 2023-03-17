/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ProjectUtil.java
 */
package ilib.gui.util;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;
import roj.math.Mat4f;
import roj.math.Rect2i;
import roj.math.Vec3d;
import roj.math.Vec4f;

import net.minecraft.client.renderer.GLAllocation;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class ProjectUtil {
	private static final IntBuffer viewport = GLAllocation.createDirectIntBuffer(16);

	private static final FloatBuffer modelview = GLAllocation.createDirectFloatBuffer(16);

	private static final FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);

	private static final FloatBuffer coords = GLAllocation.createDirectFloatBuffer(3);

	public static void updateMatrices() {
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
		GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
	}

	public static Vec3d unproject(float winX, float winY, float winZ) {
		Project.gluUnProject(winX, winY, winZ, modelview, projection, viewport, coords);

		float objectX = coords.get(0);
		float objectY = coords.get(1);
		float objectZ = coords.get(2);

		return new Vec3d(objectX, objectY, objectZ);
	}

	public static Vec3d project(float objX, float objY, float objZ) {
		Project.gluProject(objX, objY, objZ, modelview, projection, viewport, coords);

		float winX = coords.get(0);
		float winY = coords.get(1);
		float winZ = coords.get(2);

		return new Vec3d(winX, winY, winZ);
	}

	private static boolean gluUnProject(Vec4f win, Mat4f model, Mat4f projection, Rect2i viewport) {
		win.x = (win.x - viewport.xmin) / (viewport.xmax - viewport.xmin);
		win.y = (win.y - viewport.ymin) / (viewport.ymax - viewport.ymin);

		// convert projected vector from [0, 1] to [-1, 1]
		win.x = win.x * 2 - 1;
		win.y = win.y * 2 - 1;
		win.z = win.z * 2 - 1;

		win.w = 1;
		//win.x *= win.w;
		//win.y *= win.w;
		//win.z *= win.w;

		// scale matrix
        /*
        Mat4f scaleMat = new Mat4f(
            0.5, 0, 0, obj.w,
            0, 0.5, 0, obj.w,
            0, 0, 0.5, obj.w,
            1,  1,  1,     1
        );

        scaleMat.inverse().mul(win, win);
        */

		// map project -> model
		projection.invert().mul(win, win);
		// map model -> object
		Mat4f.invert(model).mul(win, win);
		return true;
	}

	private static boolean gluProject(Vec4f obj, Mat4f model, Mat4f projection, Rect2i viewport) {
		// map object -> model
		model.mul(obj, obj);
		// map model -> project
		projection.mul(obj, obj);

		// scale matrix
        /*
        Mat4f scaleMat = new Mat4f(
            0.5, 0, 0, obj.w,
            0, 0.5, 0, obj.w,
            0, 0, 0.5, obj.w,
            1,  1,  1,     1
        );
        scaleMat.mul(obj, obj);
        */

		/* normalized value */
		if (obj.w == 0) return false;

		obj.x /= obj.w;
		obj.y /= obj.w;
		obj.z /= obj.w;

		// convert projected vector from [-1, 1] to [0, 1]
		obj.x = (obj.x + 1) / 2;
		obj.y = (obj.y + 1) / 2;
		obj.z = (obj.z + 1) / 2;

		obj.x = viewport.xmin + obj.x * (viewport.xmax - viewport.xmin);
		obj.y = viewport.ymin + obj.y * (viewport.ymax - viewport.ymin);
		return true;
	}
}