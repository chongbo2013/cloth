package ro.cornholio.wallpaper.cloth;

import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import net.rbgrn.android.glwallpaperservice.GLWallpaperService.Renderer;
import ro.cornholio.wallpaper.cloth.R;
import ro.cornholio.wallpaper.cloth.physics.VerletSystem;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;
import android.test.PerformanceTestCase;
import android.util.Log;

public class MyRenderer implements Renderer {

	VerletSystem system;
	Context context;
	int frame;
	Random random;
	
	public MyRenderer(Context context) {
		this.context = context;
		this.random = new Random();
	}
	
	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		
		//GLU.gluPerspective(gl, 0f, 0f, 0f, 10f);
		gl.glDisable(GL10.GL_DITHER);
		// Set the background color to black ( rgba ).
		gl.glClearColor(0.0f, 0.0f, 0.0f, 0.5f); // OpenGL docs.
		// Enable Smooth Shading, default not really needed.
		//gl.glShadeModel(GL10.GL_SMOOTH);// OpenGL docs.
		// Depth buffer setup.
		gl.glClearDepthf(1.0f);// OpenGL docs.
		// Enables depth testing.
		gl.glEnable(GL10.GL_DEPTH_TEST);// OpenGL docs.
		// The type of depth testing to do.
		gl.glDepthFunc(GL10.GL_LEQUAL);// OpenGL docs.
		// Really nice perspective calculations.
		gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, // OpenGL docs.
				GL10.GL_FASTEST);
		gl.glEnable(GL10.GL_TEXTURE_2D); // Enable Texture Mapping ( NEW )

	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		boolean fixedBottom = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("preference_fixedBottom", true);
		int zoom = PreferenceManager.getDefaultSharedPreferences(context).getInt("preference_zoom", 1) * 2 + 1;
		Log.d("zoom", ""+zoom);
		this.system = new VerletSystem(width,height, fixedBottom, zoom);
		String background = PreferenceManager.getDefaultSharedPreferences(context).getString("preference_bkg", "cloth_logo");
		//Log.d("ClothRenderer", background);
		Bitmap bitmap = null;
		if(!"cloth_logo".equalsIgnoreCase(background)){
			bitmap = HttpUtils.loadImage(background, false);
		}
		if(bitmap == null) {
			bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.cloth_logo);
		}
		if(bitmap != null){
			system.loadGLTexture(gl, bitmap);
		}
		// Sets the current view port to the new size.
		gl.glViewport(0, 0, width, height);
		// Select the projection matrix
		gl.glMatrixMode(GL10.GL_PROJECTION);
		//GLU.gluOrtho2D(gl, 0, width, height, 0);
		// Reset the projection matrix
		gl.glLoadIdentity();// OpenGL docs.
		// Calculate the aspect ratio of the window
		// Select the modelview matrix
		gl.glMatrixMode(GL10.GL_MODELVIEW);// OpenGL docs.
		// Reset the modelview matrix
		gl.glLoadIdentity();// OpenGL docs.

	}

	@Override
	public void onDrawFrame(GL10 gl) {
		// Clears the screen and depth buffer.
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | // OpenGL docs.
				GL10.GL_DEPTH_BUFFER_BIT);
		gl.glLoadIdentity();
		gl.glTranslatef(-1.0f, 1.0f, 0.0f);
		gl.glScalef(0.2f, 0.2f, 0.2f);
		system.draw(gl);
		system.timeStep();
			
	}

	
	public void release() {

	}
}
