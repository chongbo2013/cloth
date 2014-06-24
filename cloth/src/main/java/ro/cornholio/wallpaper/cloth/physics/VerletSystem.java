package ro.cornholio.wallpaper.cloth.physics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.opengl.GLUtils;
import android.util.Log;


public class VerletSystem {

	private static final float GRAVITY = -4f;
	float MASS = 0.01f;
	public float[] particles;
	private byte[] indices;
	private float[] oldParticles;
	private float[] masses;
	private float[] forces;
	private int[][] neighbours;
	private float[] colors;
	private boolean[] fixed;
	private float[] gravity;

	int ITERATIONS = 2;
	float timestep = 0.6f;
	private final float XMIN = -50;
	private final float XMAX = 50;
	private final float YMIN = -50;
	private final float YMAX = 50;
	private final float ZMIN = -50;
	private final float ZMAX = 50;
	float DISTMIN = 1.4f;
	float DISTMAX = 1.4f;

	private FloatBuffer mVertexBuffer;
	private FloatBuffer mColorBuffer;
	/** The buffer holding the texture coordinates */
	private FloatBuffer textureBuffer;
	
	private ByteBuffer indicesBuffer;
	public static final int N = 9;
	public int M;
	private int width;
	private int height;
	private final float D = 1.4f;
	/** Our texture pointer */
	private int[] textures = new int[1];
	float[] meshPositionData;
	float[] meshNormalData;
	Vector3D[] faceNormals;

	public VerletSystem(int width, int height, boolean fixedBottom, int zoom) {
		this.width = width;
		this.height = height;
		M = (int) Math.ceil(((float)width/height)*N) + 1;
		meshPositionData = new float[3*6*(M-1)*(N-1)];
		meshNormalData = new float[3*6*(M-1)*(N-1)];
		faceNormals = new Vector3D[2*(N-1)*(M-1)];
		
		particles = new float[3 * N * M];
		int current = 0;
		for(int i=0; i<N; i++) {
			for(int j=0; j<M; j++) {
				particles[3*current] = (j - (float)(M-1)/2)*D;
				particles[3*current + 1] = -i*D;
				particles[3*current + 2] = -1;
				current++;
			}
		}
		indices = new byte[(N-1)*(M-1)*6];
		current = 0;
		for(byte i=0; i<N; i++) {
			for(byte j=0; j<M; j++) {
				if(i<(N-1) && j<(M-1)) {
					indices[6*current] = (byte)(i*M + j);
					indices[6*current + 1] = (byte)((i+1)*M + j + 1);
					indices[6*current + 2] = (byte)((i+1)*M + j);
					indices[6*current + 3] = (byte)(i*M + j);
					indices[6*current + 4] = (byte)(i*M + j + 1);
					indices[6*current + 5] = (byte)((i+1)*M + j + 1);
					current++;
				}
				
			}
		}
		
		neighbours = new int[N*M*4][];
		current = 0;
		for(int i=0; i<N; i++) {
			for(int j=0; j<M;j++) {
				if(i>0) {
					neighbours[current] = new int[2];
					neighbours[current][0] = M*i + j;
					neighbours[current++][1] = M*(i-1) + j;
				}
				if(i<N-1) {
					neighbours[current] = new int[2];
					neighbours[current][0] = M*i + j;
					neighbours[current++][1] = M*(i+1) + j;
				}
				if(j>0) {
					neighbours[current] = new int[2];
					neighbours[current][0] = M*i + j;
					neighbours[current++][1] = M*i + j-1;
				}
				if(j<M-1) {
					neighbours[current] = new int[2];
					neighbours[current][0] = M*i + j;
					neighbours[current++][1] = M*i + j+1;
				}
			}
		}
		
		colors = new float[particles.length/3*4];
		for(int i=0;i<N*M;i++) {
			colors[4*i] = (float) Math.random();
			colors[4*i+1] = (float) Math.random();
			colors[4*i+2] = (float) Math.random();
			colors[4*i+3] = 1f;
		}
		float ratio = (float)width/height;
		Log.d("x","ratio" +ratio);
		float dx = 0;
		float dy = 0;
		if(ratio < 1) {
			dx = (1-ratio)*zoom;
			dy = ratio*zoom;
		}else {
			dx = (ratio - 1)*zoom;
			dy = (2 - ratio)*zoom;
		}
		float[] texture = new float[particles.length/3*2];
		current = 0;
		for(float i=0; i<N; i++) {
			for(float j=0; j<M; j++) {
				texture[current++] = j/(M-1)*dx;
				texture[current++] = i/(N-1)*dy;	
			}
			
		}
		
		float g = fixedBottom ? 0 : GRAVITY;
		gravity = new float[] {0, g, 0};
		forces = new float[particles.length];
		masses = new float[N*M];
		for(int i=0;i<N*M;i++) {
			masses[i] = MASS;
		}
		oldParticles = particles.clone();
		fixed = new boolean[N*M];
		for(int i=0; i<N*M;i++) {
			if(i < M || (fixedBottom && (i >= (N-1)*M))) {
				fixed[i] = true;
			}
			
			
		}
		ByteBuffer vbb = ByteBuffer.allocateDirect(particles.length * 4);
		vbb.order(ByteOrder.nativeOrder());
		mVertexBuffer = vbb.asFloatBuffer();
		
		ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length * 4);
		cbb.order(ByteOrder.nativeOrder());
		mColorBuffer = cbb.asFloatBuffer();
		
		indicesBuffer = ByteBuffer.allocateDirect(indices.length * 4);
				
		ByteBuffer byteBuf = ByteBuffer.allocateDirect(texture.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		textureBuffer = byteBuf.asFloatBuffer();
		textureBuffer.put(texture);
		textureBuffer.position(0);
		
	}

	public void timeStep() {

		accumulateForces();
		verlet();
		iterateConstraints();
		//print();
	}

	private void print() {
		Log.d("position", "R = " + particles[0] + "," + particles[1] + "," + particles[2]);
		Log.d("position", "G = " + particles[3] + "," + particles[4] + "," + particles[5]);
		Log.d("position", "B = " + particles[6] + "," + particles[7] + "," + particles[8]);
		Log.d("position", "LB = " + particles[9] + "," + particles[10] + "," + particles[11]);

	}

	protected void accumulateForces() {
		// gravity
		for (int i = 0; i < particles.length; i += 3) {
			forces[i] = (gravity[0] * masses[i / 3]); 
			forces[i + 1] = (gravity[1] * masses[i / 3]);
			forces[i + 2] = (gravity[2] * masses[i / 3]);
		}
	}

	private void verlet() {
		float tmpX;
		float tmpY;
		float tmpZ;
		for (int i = 0; i < particles.length; i += 3) {
			if (!fixed[i / 3]) {
				tmpX = particles[i];
				tmpY = particles[i + 1];
				tmpZ = particles[i + 2];
				
				particles[i] +=  particles[i] - oldParticles[i] + forces[i] * timestep * timestep;
				particles[i + 1] += particles[i + 1] - oldParticles[i + 1] + forces[i + 1] * timestep * timestep;
				particles[i + 2] += particles[i + 2] - oldParticles[i + 2] + forces[i + 2] * timestep * timestep;
				oldParticles[i] = tmpX;
				oldParticles[i+1] = tmpY;
				oldParticles[i+2] = tmpZ;
			}else {
				particles[i] = oldParticles[i];
				particles[i+1] = oldParticles[i+1];
				particles[i+2] = oldParticles[i+2];
			}
		}
	}

	private void iterateConstraints() {
		for (int j = 0; j < ITERATIONS; j++) {
			
			for(int i = 0; i < particles.length; i+=3) {
				particles[i] = Math.max(Math.min(particles[i], XMAX), XMIN);
				particles[i + 1] = Math.max(Math.min(particles[i + 1], YMAX), YMIN);
				particles[i + 2] = Math.max(Math.min(particles[i + 2], ZMAX), ZMIN);
			}
			
			for (int i = 0; i < neighbours.length; i++) {
				int n[] = neighbours[i];
				if(n != null) {
					float v1v2x = particles[3 * n[1]] - particles[3 * n[0]];
					float v1v2y = particles[3 * n[1] + 1] - particles[3 * n[0] + 1];
					float v1v2z = particles[3 * n[1] + 2] - particles[3 * n[0] + 2];
					float dist = (float) Math.sqrt(Math.pow(v1v2x, 2)+Math.pow(v1v2y, 2)+Math.pow(v1v2z, 2));
					
					float diff = 0;
					if (dist > DISTMAX) {
						diff = dist - DISTMAX;
					} else if (dist < DISTMIN) {
						diff = dist - DISTMIN;
					}
					if(diff != 0) {
						float len = 1f/dist;
						v1v2x *= len;
						v1v2y *= len;
						v1v2z *= len;
						
						if(!fixed[n[0]]) {
							particles[3 * n[0]] += v1v2x * diff * 0.5;
							particles[3 * n[0] + 1] += v1v2y * diff * 0.5;
							particles[3 * n[0] + 2] += v1v2z * diff * 0.5;
						}
						if(!fixed[n[1]]) {
							particles[3 * n[1]] -= v1v2x * diff * 0.5;
							particles[3 * n[1] + 1] -= v1v2y * diff * 0.5;
							particles[3 * n[1] + 2] -= v1v2z * diff * 0.5;
						}
						
					}
					
				}
				
			}
			
			
		}
	}

	public void draw(GL10 gl) {
		
		
		mVertexBuffer.put(particles);
		mVertexBuffer.position(0);

		
		mColorBuffer.put(colors);
		mColorBuffer.position(0);

		
		indicesBuffer.put(indices);
		indicesBuffer.position(0);
		
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
		//Point to our buffers
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

		//Set the face rotation
		gl.glFrontFace(GL10.GL_CCW);
		
		gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mVertexBuffer);
		gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, textureBuffer);
		gl.glDrawElements(GL10.GL_TRIANGLES, indices.length, GL10.GL_UNSIGNED_BYTE, indicesBuffer);
		
		//Disable the client state before leaving
		gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
	}

	public float[] getPoints() {
		return particles;
	}
	
	public void setGravity(float[] values) {
		gravity[0] = values[0];
		gravity[1] = values[1];
		gravity[2] = values[2];
	}

	/**
	 * Load the textures
	 * 
	 * @param gl - The GL Context
	 * @param bitmap - texture to load
	 */
	public void loadGLTexture(GL10 gl, Bitmap bitmap) {
		Matrix matrix = new Matrix();
		int newHeight, newWidth;
		// newHeight= newWidth = getNearestMultipleOf((int) (bitmap.getHeight()
		// * 0.75), 4);
		newHeight = newWidth = 128;
		float scaleW = (float) (1.0f * newWidth / bitmap.getWidth());
		float scaleH = (float) (1.0f * newHeight / bitmap.getHeight());
		matrix.postScale(scaleW, scaleH);
		//matrix.postRotate(90.0f);

		// bitmap = Bitmap.createScaledBitmap(bitmap, 1024, 2048, false);
		bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);			
		
		//BitmapDrawable tile = new BitmapDrawable(bitmap);
		//tile.setTileModeXY(TileMode.REPEAT, TileMode.REPEAT);
		//Rect bounds = new Rect(0, 0, 32, 32);
		//tile.setBounds(bounds);
		
		//bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), tile.getBitmap().getConfig());
        
		//Generate one texture pointer...
		gl.glGenTextures(1, textures, 0);
		//...and bind it to our array
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
		 
		//Create Nearest Filtered Texture
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);

		//Different possible texture parameters, e.g. GL10.GL_CLAMP_TO_EDGE
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_REPEAT);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_REPEAT);
		
		//Use the Android GLUtils to specify a two-dimensional texture image from our bitmap
		GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
		
		//Clean up
		bitmap.recycle();
	}
	
	private int getNearestMultipleOf(int source, int multipleOf) {
		int reminder = source % multipleOf;
		int result = source;
		for(int i=0; i<=reminder; i++) {
			if(result % multipleOf == 0) {
				break;
			}
			result ++;
		}
		return result;
	}
	
	public void touch(float x, float y) {
		if(width != 0 && height != 0) {
			int column = (int)(x * M / width);
			int row = (int)(y * N / height);
			//Log.d("VerletSystem", "touched: " + column + "," + row);
			int selected = row * M + column;
			// fixed[selected] = true;
			//Log.d("VerletSystem", "selected: " + selected);
			int zPos = 3 * selected + 2;
			if(zPos < particles.length) {
				particles[3 * selected + 2] -= 0.1f;
				//particles[3 * selected + 2] = 0f;
			}
			
		}
		
	}
	
	public void updatePositions(FloatBuffer meshPositions) {
		int current = 0;
		for(int i=0; i<N-1; i++) {
			for(int j=0; j<M-1; j++) {
				meshPositionData[current++] = particles[3*(i*M + j)];
				meshPositionData[current++] = particles[3*(i*M + j)+1];
				meshPositionData[current++] = particles[3*(i*M + j)+2];

				meshPositionData[current++] = particles[3*((i+1)*M + j)];
				meshPositionData[current++] = particles[3*((i+1)*M + j)+1];
				meshPositionData[current++] = particles[3*((i+1)*M + j)+2];

				meshPositionData[current++] = particles[3*((i+1)*M + j+1)];
				meshPositionData[current++] = particles[3*((i+1)*M + j+1)+1];
				meshPositionData[current++] = particles[3*((i+1)*M + j+1)+2];

				meshPositionData[current++] = particles[3*(i*M + j)];
				meshPositionData[current++] = particles[3*(i*M + j)+1];
				meshPositionData[current++] = particles[3*(i*M + j)+2];

				meshPositionData[current++] = particles[3*((i+1)*M + j+1)];
				meshPositionData[current++] = particles[3*((i+1)*M + j+1)+1];
				meshPositionData[current++] = particles[3*((i+1)*M + j+1)+2];

				meshPositionData[current++] = particles[3*(i*M + j+1)];
				meshPositionData[current++] = particles[3*(i*M + j+1)+1];
				meshPositionData[current++] = particles[3*(i*M + j+1)+2];

			}
		}
		meshPositions.put(meshPositionData);
		meshPositions.position(0);
	}
	
	public void updateNormals(FloatBuffer meshNormals) {
		int current = 0;
		
		for(int i=0; i<N-1; i++) {
			for(int j=0; j<M-1; j++) {
				Vector3D p1 = new Vector3D(particles[3*(i*M + j)], particles[3*(i*M + j)+1], particles[3*(i*M + j)+2]);
				Vector3D p2 = new Vector3D(particles[3*((i+1)*M + j)], particles[3*((i+1)*M + j)+1], particles[3*((i+1)*M + j)+2]);
				Vector3D p3 = new Vector3D(particles[3*((i+1)*M + j+1)], particles[3*((i+1)*M + j+1)+1], particles[3*((i+1)*M + j+1)+2]);
				Vector3D p4 = new Vector3D(particles[3*(i*M + j+1)], particles[3*(i*M + j+1)+1], particles[3*(i*M + j+1)+2]);
				
				Vector3D u = p3.subtract(p2);
				Vector3D v = p1.subtract(p2);
				Vector3D w = p1.subtract(p4);
				
				faceNormals[current++] =  u.crossProduct(v);
				faceNormals[current++] = w.crossProduct(v.negate());
				
			}
		}
		
		for(int i=0; i<N; i++) {
			for(int j=0; j<M; j++) {
				Vector3D normal = new Vector3D(new double[]{0,0,1});
				if(i > 0) {
					if(j > 0) {
						normal = normal.add(faceNormals[2*((i-1)*(M-1)+j-1)]);
						normal = normal.add(faceNormals[2*((i-1)*(M-1)+j-1)+1]);
					}
					if(j < M-1) {
						normal = normal.add(faceNormals[2*((i-1)*(M-1)+j)]);
					}
				}
				if(i < N-1) {
					if(j < M-1) {
						normal = normal.add(faceNormals[2*(i*(M-1)+j)]);
						normal = normal.add(faceNormals[2*(i*(M-1)+j)+1]);
					}
					if(j > 0) {
						normal = normal.add(faceNormals[2*(i*(M-1)+j-1)+1]);
					}
				}
				normal = normal.normalize();
				if(i > 0) {
					if(j > 0) {
						meshNormalData[6*3*((i-1)*(M-1)+j-1)+6] = (float) normal.getX();
						meshNormalData[6*3*((i-1)*(M-1)+j-1)+7] = (float) normal.getY();
						meshNormalData[6*3*((i-1)*(M-1)+j-1)+8] = (float) normal.getZ();
						
						meshNormalData[6*3*((i-1)*(M-1)+j-1)+12] = (float) normal.getX();
						meshNormalData[6*3*((i-1)*(M-1)+j-1)+13] = (float) normal.getY();
						meshNormalData[6*3*((i-1)*(M-1)+j-1)+14] = (float) normal.getZ();

					}
					if(j < M-1) {
						meshNormalData[6*3*((i-1)*(M-1)+j)+3] = (float) normal.getX();
						meshNormalData[6*3*((i-1)*(M-1)+j)+4] = (float) normal.getY();
						meshNormalData[6*3*((i-1)*(M-1)+j)+5] = (float) normal.getZ();
					}
				}
				if(i < N-1) {
					if(j < M-1) {
						meshNormalData[6*3*(i*(M-1)+j)] = (float) normal.getX();
						meshNormalData[6*3*(i*(M-1)+j)+1] = (float) normal.getY();
						meshNormalData[6*3*(i*(M-1)+j)+2] = (float) normal.getZ();
						
						meshNormalData[6*3*(i*(M-1)+j)+9] = (float) normal.getX();
						meshNormalData[6*3*(i*(M-1)+j)+10] = (float) normal.getY();
						meshNormalData[6*3*(i*(M-1)+j)+11] = (float) normal.getZ();
					}
					if(j > 0) {
						meshNormalData[6*3*(i*(M-1)+j-1)+15] = (float) normal.getX();
						meshNormalData[6*3*(i*(M-1)+j-1)+16] = (float) normal.getY();
						meshNormalData[6*3*(i*(M-1)+j-1)+17] = (float) normal.getZ();

					}
				}
			}
		}
		meshNormals.put(meshNormalData);
        meshNormals.position(0);
	} 
}