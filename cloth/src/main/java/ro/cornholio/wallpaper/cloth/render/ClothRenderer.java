package ro.cornholio.wallpaper.cloth.render;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import ro.cornholio.wallpaper.cloth.R;
import ro.cornholio.wallpaper.cloth.physics.VerletSystem;
import ro.cornholio.wallpaper.cloth.util.RawResourceReader;
import ro.cornholio.wallpaper.cloth.util.ShaderHelper;
import ro.cornholio.wallpaper.cloth.util.TextureHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.squareup.picasso.Picasso;

/**
 * This class implements our custom renderer. Note that the GL10 parameter passed in is unused for OpenGL ES 2.0
 * renderers -- the static class GLES20 is used instead.
 */
public class ClothRenderer implements GLSurfaceView.Renderer 
{	
	/** Used for debug logs. */
	private static final String TAG = ClothRenderer.class.getName();
	
	private final Context mActivityContext;
	
	/**
	 * Store the model matrix. This matrix is used to move models from object space (where each model can be thought
	 * of being located at the center of the universe) to world space.
	 */
	private float[] mModelMatrix = new float[16];

	/**
	 * Store the view matrix. This can be thought of as our camera. This matrix transforms world space to eye space;
	 * it positions things relative to our eye.
	 */
	private float[] mViewMatrix = new float[16];

	/** Store the projection matrix. This is used to project the scene onto a 2D viewport. */
	private float[] mProjectionMatrix = new float[16];
	
	/** Allocate storage for the final combined matrix. This will be passed into the shader program. */
	private float[] mMVPMatrix = new float[16];
	
	/** 
	 * Stores a copy of the model matrix specifically for the light position.
	 */
	private float[] mLightModelMatrix = new float[16];
	private float[] mLightModelMatrix2 = new float[16];
	
	/** Store our model data in a float buffer. */
	private FloatBuffer mMeshPositions;
	private FloatBuffer mMeshNormals;
	private FloatBuffer mMeshColors;
	private FloatBuffer mMeshTextureCoordinates;
	
	/** This will be used to pass in the transformation matrix. */
	private int mMVPMatrixHandle;
	
	/** This will be used to pass in the modelview matrix. */
	private int mMVMatrixHandle;
	
	/** This will be used to pass in the light position. */
	private int mLightPosHandle;
	private int mLightPosHandle2;
	
	/** This will be used to pass in the texture. */
	private int mTextureUniformHandle;
	
	/** This will be used to pass in model position information. */
	private int mPositionHandle;
	
	/** This will be used to pass in model color information. */
	private int mColorHandle;
	
	/** This will be used to pass in model normal information. */
	private int mNormalHandle;
	
	/** This will be used to pass in model texture coordinate information. */
	private int mTextureCoordinateHandle;

	/** How many bytes per float. */
	private final int mBytesPerFloat = 4;	
	
	/** Size of the position data in elements. */
	private final int mPositionDataSize = 3;	
	
	/** Size of the color data in elements. */
	private final int mColorDataSize = 4;	
	
	/** Size of the normal data in elements. */
	private final int mNormalDataSize = 3;
	
	/** Size of the texture coordinate data in elements. */
	private final int mTextureCoordinateDataSize = 2;
	
	/** Used to hold a light centered on the origin in model space. We need a 4th coordinate so we can get translations to work when
	 *  we multiply this by our transformation matrices. */
	private final float[] mLightPosInModelSpace = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	private final float[] mLightPosInModelSpace2 = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	
	/** Used to hold the current position of the light in world space (after transformation via model matrix). */
	private final float[] mLightPosInWorldSpace = new float[4];
	private final float[] mLightPosInWorldSpace2 = new float[4];
	
	/** Used to hold the transformed position of the light in eye space (after transformation via modelview matrix) */
	private final float[] mLightPosInEyeSpace = new float[4];
	private final float[] mLightPosInEyeSpace2 = new float[4];
	
	/** This is a handle to our cube shading program. */
	private int mProgramHandle;
		
	/** This is a handle to our light point program. */
	//private int mPointProgramHandle;
	//private int mPointProgramHandle2;
	
	/** This is a handle to our texture data. */
	private int mTextureDataHandle;
	
	final int N = VerletSystem.N;
	int M;
	
	//float[] meshPositionData;
	
	float[] meshTextureCoordinateData;
	
	VerletSystem verlet;

	public ClothRenderer(final Context activityContext)
	{	
		mActivityContext = activityContext;
	}

	protected String getVertexShader()
	{
		return RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.per_pixel_vertex_shader);
	}
	
	protected String getFragmentShader()
	{
		return RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.per_pixel_fragment_shader);
	}
	
	@Override
	public void onSurfaceCreated(GL10 glUnused, EGLConfig config) 
	{
		// Set the background clear color to black.
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		
		// Use culling to remove back faces.
		GLES20.glEnable(GLES20.GL_CULL_FACE);
		
		// Enable depth testing
		GLES20.glEnable(GLES20.GL_DEPTH_TEST);
		
		// The below glEnable() call is a holdover from OpenGL ES 1, and is not needed in OpenGL ES 2.
		// Enable texture mapping
		// GLES20.glEnable(GLES20.GL_TEXTURE_2D);
			
		// Position the eye in front of the origin.
		final float eyeX = 0.0f;
		final float eyeY = 0.0f;
		final float eyeZ = -0.5f;

		// We are looking toward the distance
		final float lookX = 0.0f;
		final float lookY = 0.0f;
		final float lookZ = -5.0f;

		// Set our up vector. This is where our head would be pointing were we holding the camera.
		final float upX = 0.0f;
		final float upY = 1.0f;
		final float upZ = 0.0f;

		// Set the view matrix. This matrix can be said to represent the camera position.
		// NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
		// view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
		Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);		

		final String vertexShader = getVertexShader();   		
 		final String fragmentShader = getFragmentShader();			
		
		final int vertexShaderHandle = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);		
		final int fragmentShaderHandle = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);		
		
		mProgramHandle = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle, 
				new String[] {"a_Position",  "a_Color", "a_Normal", "a_TexCoordinate"});								                                							       
        
        // Define a simple shader program for our point.
        //final String pointVertexShader = RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.point_vertex_shader);
        //final String pointFragmentShader = RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.point_fragment_shader);
        
        //final int pointVertexShaderHandle = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, pointVertexShader);
        //final int pointFragmentShaderHandle = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, pointFragmentShader);
        //mPointProgramHandle = ShaderHelper.createAndLinkProgram(pointVertexShaderHandle, pointFragmentShaderHandle,
        //		new String[] {"a_Position"});
        
        // Load the texture
            mTextureDataHandle = TextureHelper.loadTexture(mActivityContext, R.drawable.cloth_logo);
	}

    public void updateTexture(Bitmap pattern) {
        mTextureDataHandle = TextureHelper.loadTexture(mActivityContext, pattern);
    }
		
	@Override
	public void onSurfaceChanged(GL10 glUnused, int width, int height) 
	{
        //obtain texture
        Bitmap bitmap = null;

        try {
            bitmap = BitmapFactory.decodeStream(mActivityContext.openFileInput("pattern"));

        } catch (FileNotFoundException e) {
            Log.e(TAG, "could not find cached pattern", e);
        }

        if (bitmap != null) {
            mTextureDataHandle = TextureHelper.loadTexture(mActivityContext, bitmap);
        }else {
            mTextureDataHandle = TextureHelper.loadTexture(mActivityContext, R.drawable.cloth_logo);
        }

		// Set the OpenGL viewport to the same size as the surface.
		GLES20.glViewport(0, 0, width, height);

		// Create a new perspective projection matrix. The height will stay the same
		// while the width will vary as per aspect ratio.
		final float ratio = (float) width / height;
		final float left = -ratio;
		final float right = ratio;
		final float bottom = -1.0f;
		final float top = 1.0f;
		final float near = 1.0f;
		final float far = 10.0f;
		
		Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
		
		M = (int) Math.ceil(ratio * N) + 1;
		final float[] meshColorData = new float[4*6*(M-1)*(N-1)];
		for(int i=0; i<meshColorData.length; i+=4){
			//Log.d(TAG, "wtf: " + (float)i/meshColorData.length);
			float color = (float) Math.random();
			meshColorData[i] = 1f;
			meshColorData[i+1] = 1f;
			meshColorData[i+2] = 1f;
			meshColorData[i+3] = 1.0f;
		}
		
		meshTextureCoordinateData = new float[2*6*(M-1)*(N-1)]; 
		
		// Initialize the buffers.
		mMeshPositions = ByteBuffer.allocateDirect(3*6*(M-1)*(N-1) * mBytesPerFloat)
		        .order(ByteOrder.nativeOrder()).asFloatBuffer();							
		
		mMeshColors = ByteBuffer.allocateDirect(meshColorData.length * mBytesPerFloat)
		        .order(ByteOrder.nativeOrder()).asFloatBuffer();							
		mMeshColors.put(meshColorData).position(0);
				
		mMeshNormals = ByteBuffer.allocateDirect(3*6*(M-1)*(N-1) * mBytesPerFloat)
		        .order(ByteOrder.nativeOrder()).asFloatBuffer();							
		
		mMeshTextureCoordinates = ByteBuffer.allocateDirect(meshTextureCoordinateData.length * mBytesPerFloat)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		mMeshTextureCoordinates.put(meshTextureCoordinateData).position(0);
	
		int zoom = 8;
		verlet = new VerletSystem(width,height, true, zoom);
		
		float dx = zoom;//(float)zoom/(M-1);
		float dy = zoom;//(float)zoom/(N-1);
		
		int current = 0;
		for(float i=0; i<N-1; i++) {
			for(float j=0; j<M-1; j++) {
				
				meshTextureCoordinateData[current++] = (float)j/(M-1)*dx;
				meshTextureCoordinateData[current++] = (float)i/(N-1)*dy;
				
				meshTextureCoordinateData[current++] = (float)j/(M-1)*dx;
				meshTextureCoordinateData[current++] = (float)(i+1)/(N-1)*dy;
				
				meshTextureCoordinateData[current++] = (float)(j+1)/(M-1)*dx;
				meshTextureCoordinateData[current++] = (float)(i+1)/(N-1)*dy;
				
				meshTextureCoordinateData[current++] = (float)j/(M-1)*dx;
				meshTextureCoordinateData[current++] = (float)i/(N-1)*dy;
				
				meshTextureCoordinateData[current++] = (float)(j+1)/(M-1)*dx;
				meshTextureCoordinateData[current++] = (float)(i+1)/(N-1)*dy;
				
				meshTextureCoordinateData[current++] = (float)(j+1)/(M-1)*dx;
				meshTextureCoordinateData[current++] = (float)i/(N-1)*dy;
				
			}
		}
	}	

	@Override
	public void onDrawFrame(GL10 glUnused) 
	{
		long start = System.currentTimeMillis();
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);			        
                
        // Do a complete rotation every 10 seconds.
        long time = SystemClock.uptimeMillis() % 10000L;        
        float angleInDegrees = (360.0f / 10000.0f) * ((int) time);                
        
        // Set our per-vertex lighting program.
        GLES20.glUseProgram(mProgramHandle);
        
        // Set program handles for cube drawing.
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVPMatrix");
        mMVMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVMatrix"); 
        mLightPosHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_LightPos");
        mLightPosHandle2 = GLES20.glGetUniformLocation(mProgramHandle, "u_LightPos2");
        mTextureUniformHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_Texture");
        mPositionHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Position");
        mColorHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Color");
        mNormalHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Normal"); 
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_TexCoordinate");
        
        // Set the active texture unit to texture unit 0.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        
        // Bind the texture to this unit.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDataHandle);
        
        // Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 0);        
        
        // Calculate position of the light. Rotate and then push into the distance.
        Matrix.setIdentityM(mLightModelMatrix, 0);
        Matrix.translateM(mLightModelMatrix, 0, 0.0f, 9.0f, -4.0f);      
        //Matrix.rotateM(mLightModelMatrix, 0, angleInDegrees, 1.0f, 0.0f, 0.0f);
        //Matrix.translateM(mLightModelMatrix, 0, 0.0f, 0.0f, 2.0f);
               
        Matrix.multiplyMV(mLightPosInWorldSpace, 0, mLightModelMatrix, 0, mLightPosInModelSpace, 0);
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, mViewMatrix, 0, mLightPosInWorldSpace, 0);                        
        
        Matrix.setIdentityM(mLightModelMatrix2, 0);
        Matrix.translateM(mLightModelMatrix2, 0, 0.0f, -9.0f, -1.0f);      
               
        Matrix.multiplyMV(mLightPosInWorldSpace2, 0, mLightModelMatrix2, 0, mLightPosInModelSpace2, 0);
        Matrix.multiplyMV(mLightPosInEyeSpace2, 0, mViewMatrix, 0, mLightPosInWorldSpace2, 0); 
        
        //Draw mesh
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 0.0f, 6.0f, -5.0f);
        //Matrix.rotateM(mModelMatrix, 0, angleInDegrees, 1.0f, 0.0f, 0.0f);
        if((int)(Math.random()*20) == 5){
        	verlet.touch((int)(Math.random()*500), (int)(Math.random()*500));
        }
        verlet.timeStep();
        drawMesh();
        
        /*
        // Draw some cubes.
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 4.0f, 0.0f, -7.0f);
        Matrix.rotateM(mModelMatrix, 0, angleInDegrees, 1.0f, 0.0f, 0.0f);        
        drawCube();
                        
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, -4.0f, 0.0f, -7.0f);
        Matrix.rotateM(mModelMatrix, 0, angleInDegrees, 0.0f, 1.0f, 0.0f);        
        drawCube();
        
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 0.0f, 4.0f, -7.0f);
        Matrix.rotateM(mModelMatrix, 0, angleInDegrees, 0.0f, 0.0f, 1.0f);        
        drawCube();
        
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 0.0f, -4.0f, -7.0f);
        drawCube();
        
        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 0.0f, 0.0f, -5.0f);
        Matrix.rotateM(mModelMatrix, 0, angleInDegrees, 1.0f, 1.0f, 0.0f);        
        //drawCube();  */    
        
        // Draw a point to indicate the light.
        //GLES20.glUseProgram(mPointProgramHandle);        
        //drawLight();
        //long end = System.currentTimeMillis();
        //Log.d(TAG,  "rendered frame in : " + (end - start));
	}				
	
	/**
	 * Draws a mesh.
	 */			
	private void drawMesh()
	{		
		// Pass in the position information
		verlet.updatePositions(mMeshPositions);
				
        GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize, GLES20.GL_FLOAT, false,
        		0, mMeshPositions);        
                
        GLES20.glEnableVertexAttribArray(mPositionHandle);        
        
        // Pass in the color information
        mMeshColors.position(0);
        GLES20.glVertexAttribPointer(mColorHandle, mColorDataSize, GLES20.GL_FLOAT, false,
        		0, mMeshColors);        
        
        GLES20.glEnableVertexAttribArray(mColorHandle);
        
        // Pass in the normal information
        verlet.updateNormals(mMeshNormals);
        
        GLES20.glVertexAttribPointer(mNormalHandle, mNormalDataSize, GLES20.GL_FLOAT, false, 
        		0, mMeshNormals);
        
        GLES20.glEnableVertexAttribArray(mNormalHandle);
        
        // Pass in the texture coordinate information
        mMeshTextureCoordinates.put(meshTextureCoordinateData);
        mMeshTextureCoordinates.position(0);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, mTextureCoordinateDataSize, GLES20.GL_FLOAT, false, 
        		0, mMeshTextureCoordinates);
        
        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);
        
		// This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
        // (which currently contains model * view).
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);   
        
        // Pass in the modelview matrix.
        GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mMVPMatrix, 0);                
        
        // This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        
        // Pass in the light position in eye space.        
        GLES20.glUniform3f(mLightPosHandle, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1], mLightPosInEyeSpace[2]);
        GLES20.glUniform3f(mLightPosHandle2, mLightPosInEyeSpace2[0], mLightPosInEyeSpace2[1], mLightPosInEyeSpace2[2]);
        // Draw the mesh.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6*(N-1)*(M-1));                               
	}	

	/**
	 * Draws a point representing the position of the light.
	 */
	/*private void drawLight()
	{
		final int pointMVPMatrixHandle = GLES20.glGetUniformLocation(mPointProgramHandle, "u_MVPMatrix");
        final int pointPositionHandle = GLES20.glGetAttribLocation(mPointProgramHandle, "a_Position");
        
		// Pass in the position.
		GLES20.glVertexAttrib3f(pointPositionHandle, mLightPosInModelSpace[0], mLightPosInModelSpace[1], mLightPosInModelSpace[2]);

		// Since we are not using a buffer object, disable vertex arrays for this attribute.
        GLES20.glDisableVertexAttribArray(pointPositionHandle);  
		
		// Pass in the transformation matrix.
		Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mLightModelMatrix, 0);
		Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
		GLES20.glUniformMatrix4fv(pointMVPMatrixHandle, 1, false, mMVPMatrix, 0);
		
		// Draw the point.
		GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);
	}*/
}