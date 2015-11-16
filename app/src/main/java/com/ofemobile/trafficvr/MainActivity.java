/*
 * Copyright 2014 Google Inc. All Rights Reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ofemobile.trafficvr;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.locks.ReentrantLock;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * A Cardboard sample application.
 */
public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer {

  private static final String TAG = "MainActivity";

  private static final float Z_NEAR = 0.1f;
  private static final float Z_FAR = 100.0f;

  private static final float CAMERA_Z = 0.01f;
  private static final float TIME_DELTA = 0.3f;

  private static final float YAW_LIMIT = 0.12f;
  private static final float PITCH_LIMIT = 0.12f;

  private static final int COORDS_PER_VERTEX = 3;

  // We keep the light always position just above the user.
  private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[] { 0.0f, 2.0f, 0.0f, 1.0f };

  private final float[] lightPosInEyeSpace = new float[4];

  private FloatBuffer floorVertices;
  private FloatBuffer floorColors;
  private FloatBuffer floorNormals;

  private FloatBuffer cubeVertices;
  private FloatBuffer cubeColors;
  private FloatBuffer cubeFoundColors;
  private FloatBuffer cubeNormals;

  private FloatBuffer beamVertices;
  private FloatBuffer beamTXCoords;

  private FloatBuffer rectVertices;
  private FloatBuffer rectTXCoords;

  private FloatBuffer axisVertices;
  private FloatBuffer axisColors;

  private int cubeProgram;
  private int floorProgram;
  private int beamProgram;
  private int txProgram;
  private int plainProgram;
  private int flareProgram;

  private int cubePositionParam;
  private int cubeNormalParam;
  private int cubeColorParam;
  private int cubeModelParam;
  private int cubeModelViewParam;
  private int cubeModelViewProjectionParam;
  private int cubeLightPosParam;

  private int floorPositionParam;
  private int floorCoordParam;
  private int floorColorParam;
  private int floorModelParam;
  private int floorModelViewParam;
  private int floorModelViewProjectionParam;
  private int floorLightPosParam;

  private int beamModelViewProjectionParam;
  private int beamPositionParam;
  private int beamCoordParam;
  private int beamMaxDepthParam;

  private int txModelViewProjectionParam;
  private int txPositionParam;
  private int txCoordParam;
  private int txTransParam;

  private int plainModelViewProjectionParam;
  private int plainPositionParam;
  private int plainColorParam;

  private int flareModelViewProjectionParam;
  private int flareRadiusParam;
  private int flarePositionParam;
  private int flareCoordParam;

  private float[] modelCube;
  private float[] camera;
  private float[] viewMatrix;
  private float[] headView;
  private float[] modelViewProjection;
  private float[] modelViewMatrix;
  private float[] modelFloor;
  private float[] modelProjectile;
  private float[] modelBeam;
  private float[] modelFlare;
  private float[] modelMatrix;

  private float[] projectilePos = {1,0,0,1};
  private float[] projectileVelocity = {1,1,0,0};
  private float[] cubePos = {0,0,0,0};
  private float[] cubeVel = {0,0,0,0};
  private float[] cubeAccel = {0,0,0,0};



  private float[] forwardVector = {0,0,0};
  
  private int score = 0;
  private int shots = 10;
  private int mode = 1;
  private float objectDistance = 3.5f;
  private float floorDepth = 1.5f;

  private boolean out = true;

  private Vibrator vibrator;
  private CardboardOverlayView overlayView;

  private int frameNo = 0;
  private int signFadeFrame = -200;

  private boolean beamFiring = false;
  private float beamDist = 0;
  boolean beamHit = false;
  int flareStartFrame = -51;


  /**
   * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
   *
   * @param type The type of shader we will be creating.
   * @param resId The resource ID of the raw text file about to be turned into a shader.
   * @return The shader object handler.
   */
  private int loadGLShader(int type, int resId) {
    String code = readRawTextFile(resId);
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, code);
    GLES20.glCompileShader(shader);

    // Get the compilation status.
    final int[] compileStatus = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

    // If the compilation failed, delete the shader.
    if (compileStatus[0] == 0) {
      Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
      GLES20.glDeleteShader(shader);
      shader = 0;
    }

    if (shader == 0) {
      throw new RuntimeException("Error creating shader.");
    }

    return shader;
  }

  /**
   * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
   *
   * @param label Label to report in case of error.
   */
  private static void checkGLError(String label) {
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, label + ": glError " + error);
      throw new RuntimeException(label + ": glError " + error);
    }
  }

  /**
   * Sets the viewMatrix to our CardboardView and initializes the transformation matrices we will use
   * to render our scene.
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.common_ui);
    CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
    cardboardView.setRestoreGLStateEnabled(false);
    cardboardView.setRenderer(this);
    setCardboardView(cardboardView);

    modelCube = new float[16];
    camera = new float[16];
    viewMatrix = new float[16];
    modelViewProjection = new float[16];
    modelViewMatrix = new float[16];
    modelFloor = new float[16];
    headView = new float[16];
    modelProjectile = new float[16];
    modelBeam = new float[16];
    modelMatrix = new float[16];
    modelFlare = new float[16];
    vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);


    overlayView = (CardboardOverlayView) findViewById(R.id.overlay);
    show3DToast("Pull the magnet when you find a target.", 5000);

    Log.i(TAG, "onCreate");
    if (Looper.myLooper() == Looper.getMainLooper())
      Log.i(TAG, "In UI thread");
    else
      Log.i(TAG, "Not in UI thread");

    reset();
  }

  @Override
  public void onRendererShutdown() {
    Log.i(TAG, "onRendererShutdown");
  }

  @Override
  public void onSurfaceChanged(int width, int height) {
    Log.i(TAG, "onSurfaceChanged");
  }

  /**
   * Creates the buffers we use to store information about the 3D world.
   *
   * <p>OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
   * Hence we use ByteBuffers.
   *
   * @param config The EGL configuration used when creating the surface.
   */
  @Override
  public void onSurfaceCreated(EGLConfig config) {
    Log.i(TAG, "onSurfaceCreated");

    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well.

    ByteBuffer bbBeamVertices = ByteBuffer.allocateDirect(WorldLayoutData.BEAM_VERTS.length * 4);
    bbBeamVertices.order(ByteOrder.nativeOrder());
    beamVertices = bbBeamVertices.asFloatBuffer();
    beamVertices.put(WorldLayoutData.BEAM_VERTS);
    beamVertices.position(0);

    ByteBuffer bbBeamTXCoords = ByteBuffer.allocateDirect(WorldLayoutData.BEAM_TCCOORDS.length * 4);
    bbBeamTXCoords.order(ByteOrder.nativeOrder());
    beamTXCoords = bbBeamTXCoords.asFloatBuffer();
    beamTXCoords.put(WorldLayoutData.BEAM_TCCOORDS);
    beamTXCoords.position(0);

    ByteBuffer bbrVertices = ByteBuffer.allocateDirect(WorldLayoutData.RECT_COORDS.length * 4);
    bbrVertices.order(ByteOrder.nativeOrder());
    rectVertices = bbrVertices.asFloatBuffer();
    rectVertices.put(WorldLayoutData.RECT_COORDS);
    rectVertices.position(0);

    ByteBuffer bbrTxCords = ByteBuffer.allocateDirect(WorldLayoutData.RECT_COORDS.length * 4);
    bbrTxCords.order(ByteOrder.nativeOrder());
    rectTXCoords = bbrTxCords.asFloatBuffer();
    rectTXCoords.put(WorldLayoutData.RECT_TXCOORDS);
    rectTXCoords.position(0);

    ByteBuffer bbVertices = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COORDS.length * 4);
    bbVertices.order(ByteOrder.nativeOrder());
    cubeVertices = bbVertices.asFloatBuffer();
    cubeVertices.put(WorldLayoutData.CUBE_COORDS);
    cubeVertices.position(0);

    ByteBuffer bbColors = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COLORS.length * 4);
    bbColors.order(ByteOrder.nativeOrder());
    cubeColors = bbColors.asFloatBuffer();
    cubeColors.put(WorldLayoutData.CUBE_COLORS);
    cubeColors.position(0);

    ByteBuffer bbFoundColors = ByteBuffer.allocateDirect(
        WorldLayoutData.CUBE_FOUND_COLORS.length * 4);
    bbFoundColors.order(ByteOrder.nativeOrder());
    cubeFoundColors = bbFoundColors.asFloatBuffer();
    cubeFoundColors.put(WorldLayoutData.CUBE_FOUND_COLORS);
    cubeFoundColors.position(0);

    ByteBuffer bbNormals = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_NORMALS.length * 4);
    bbNormals.order(ByteOrder.nativeOrder());
    cubeNormals = bbNormals.asFloatBuffer();
    cubeNormals.put(WorldLayoutData.CUBE_NORMALS);
    cubeNormals.position(0);

    // make a floor
    ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COORDS.length * 4);
    bbFloorVertices.order(ByteOrder.nativeOrder());
    floorVertices = bbFloorVertices.asFloatBuffer();
    floorVertices.put(WorldLayoutData.FLOOR_COORDS);
    floorVertices.position(0);

    ByteBuffer bbFloorCoords = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COORDSS.length * 4);
    bbFloorCoords.order(ByteOrder.nativeOrder());
    floorNormals = bbFloorCoords.asFloatBuffer();
    floorNormals.put(WorldLayoutData.FLOOR_COORDSS);
    floorNormals.position(0);

    ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COLORS.length * 4);
    bbFloorColors.order(ByteOrder.nativeOrder());
    floorColors = bbFloorColors.asFloatBuffer();
    floorColors.put(WorldLayoutData.FLOOR_COLORS);
    floorColors.position(0);

    ByteBuffer bbaxisVertices = ByteBuffer.allocateDirect(WorldLayoutData.AXIS_VERTS.length * 4);
    bbaxisVertices.order(ByteOrder.nativeOrder());
    axisVertices = bbaxisVertices.asFloatBuffer();
    axisVertices.put(WorldLayoutData.AXIS_VERTS);
    axisVertices.position(0);

    ByteBuffer bbaxisColors = ByteBuffer.allocateDirect(WorldLayoutData.AXIS_COLORS.length * 4);
    bbaxisColors.order(ByteOrder.nativeOrder());
    axisColors = bbaxisColors.asFloatBuffer();
    axisColors.put(WorldLayoutData.AXIS_COLORS);
    axisColors.position(0);

    int beamVertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.beam_vertex);
    int beamFragShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.beam_fragment);
    int gridvertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.grid_vertex);
    int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
    int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);
    int passthroughShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);
    int textureFragShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.texture_fragment);
    int plainvertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.plain_vertex);
    int flareFragShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.flare_fragment);

    cubeProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(cubeProgram, vertexShader);
    GLES20.glAttachShader(cubeProgram, passthroughShader);
    GLES20.glLinkProgram(cubeProgram);
    GLES20.glUseProgram(cubeProgram);

    checkGLError("Cube program");

    cubePositionParam = GLES20.glGetAttribLocation(cubeProgram, "a_Position");
    cubeNormalParam = GLES20.glGetAttribLocation(cubeProgram, "a_Normal");
    cubeColorParam = GLES20.glGetAttribLocation(cubeProgram, "a_Color");

    cubeModelParam = GLES20.glGetUniformLocation(cubeProgram, "u_Model");
    cubeModelViewParam = GLES20.glGetUniformLocation(cubeProgram, "u_MVMatrix");
    cubeModelViewProjectionParam = GLES20.glGetUniformLocation(cubeProgram, "u_MVP");
    cubeLightPosParam = GLES20.glGetUniformLocation(cubeProgram, "u_LightPos");

    GLES20.glEnableVertexAttribArray(cubePositionParam);
    GLES20.glEnableVertexAttribArray(cubeNormalParam);
    GLES20.glEnableVertexAttribArray(cubeColorParam);

    checkGLError("Cube program params");

    floorProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(floorProgram, gridvertexShader);
    GLES20.glAttachShader(floorProgram, gridShader);
    GLES20.glLinkProgram(floorProgram);
    GLES20.glUseProgram(floorProgram);

    checkGLError("Floor program");


    floorModelParam = GLES20.glGetUniformLocation(floorProgram, "u_Model");
//    floorModelViewParam = GLES20.glGetUniformLocation(floorProgram, "u_MVMatrix");
    floorModelViewProjectionParam = GLES20.glGetUniformLocation(floorProgram, "u_MVP");
    floorLightPosParam = GLES20.glGetUniformLocation(floorProgram, "u_LightPos");

    floorPositionParam = GLES20.glGetAttribLocation(floorProgram, "a_Position");
    floorCoordParam = GLES20.glGetAttribLocation(floorProgram, "a_Coord");
//    floorColorParam = GLES20.glGetAttribLocation(floorProgram, "a_Color");

    GLES20.glEnableVertexAttribArray(floorPositionParam);
    GLES20.glEnableVertexAttribArray(floorCoordParam);
//    GLES20.glEnableVertexAttribArray(floorColorParam);

    checkGLError("Floor program params");

    beamProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(beamProgram, beamVertexShader);
    GLES20.glAttachShader(beamProgram, beamFragShader);
    GLES20.glLinkProgram(beamProgram);
    GLES20.glUseProgram(beamProgram);
    checkGLError("Beam program");

    beamModelViewProjectionParam = GLES20.glGetUniformLocation(beamProgram, "u_MVP");
    beamPositionParam = GLES20.glGetAttribLocation(beamProgram, "a_Position");
    beamCoordParam = GLES20.glGetAttribLocation(beamProgram, "a_TXCoord");
    beamMaxDepthParam = GLES20.glGetUniformLocation(beamProgram, "u_maxDepth");
    GLES20.glEnableVertexAttribArray(beamPositionParam);
    GLES20.glEnableVertexAttribArray(beamCoordParam);
    checkGLError("Beam program params");

    txProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(txProgram, gridvertexShader);
    GLES20.glAttachShader(txProgram, textureFragShader);
    GLES20.glLinkProgram(txProgram);
    GLES20.glUseProgram(txProgram);
    checkGLError("Tx program");

    txModelViewProjectionParam = GLES20.glGetUniformLocation(txProgram, "u_MVP");
    txTransParam = GLES20.glGetUniformLocation(txProgram, "u_Trans");
    txPositionParam = GLES20.glGetAttribLocation(txProgram, "a_Position");
    txCoordParam = GLES20.glGetAttribLocation(txProgram, "a_Coord");
    GLES20.glEnableVertexAttribArray(txPositionParam);
    GLES20.glEnableVertexAttribArray(txCoordParam);
    checkGLError("Tx program params");


    plainProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(plainProgram, plainvertexShader);
    GLES20.glAttachShader(plainProgram, passthroughShader);
    GLES20.glLinkProgram(plainProgram);
    GLES20.glUseProgram(plainProgram);
    checkGLError("Plain program");

    plainModelViewProjectionParam = GLES20.glGetUniformLocation(plainProgram, "u_MVP");
    plainPositionParam = GLES20.glGetAttribLocation(plainProgram, "a_Position");
    plainColorParam = GLES20.glGetAttribLocation(plainProgram, "a_Color");
    GLES20.glEnableVertexAttribArray(plainPositionParam);
    GLES20.glEnableVertexAttribArray(plainColorParam);
    checkGLError("Plain program params");

    flareProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(flareProgram, gridvertexShader);
    GLES20.glAttachShader(flareProgram, flareFragShader);
    GLES20.glLinkProgram(flareProgram);
    GLES20.glUseProgram(flareProgram);
    checkGLError("Flare program");

    flareModelViewProjectionParam = GLES20.glGetUniformLocation(flareProgram, "u_MVP");
    flareRadiusParam = GLES20.glGetUniformLocation(flareProgram, "u_Radius");
    flarePositionParam = GLES20.glGetAttribLocation(flareProgram, "a_Position");
    flareCoordParam = GLES20.glGetAttribLocation(flareProgram, "a_Coord");
    GLES20.glEnableVertexAttribArray(flarePositionParam);
    GLES20.glEnableVertexAttribArray(flareCoordParam);
    checkGLError("Flare program params");

    //Create the textures:
    int[] textures = new int[2];
//Generate one signTexture pointer...
    GLES20.glGenTextures(2, textures, 0);

    for (int i =0; i<2; i++) {
//...and bind it to our array
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);

//Create Nearest Filtered Texture
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

//Different possible signTexture parameters, e.g. GL10.GL_CLAMP_TO_EDGE
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
      Log.i(TAG, "Texture created " + textures[i]);
    }
    signTexture = textures[0];
    reticleTexture = textures[1];

    updateReticle(1);

    // Object first appears directly in front of user.
    Matrix.setIdentityM(modelCube, 0);
    Matrix.translateM(modelCube, 0, 0, 0, -objectDistance);
    //No it does not.
    hideObject();

    Matrix.setIdentityM(modelFloor, 0);
    Matrix.translateM(modelFloor, 0, 0, -floorDepth, 0); // Floor appears below user.

    checkGLError("onSurfaceCreated");
  }

  /**
   * Converts a raw text file into a string.
   *
   * @param resId The resource ID of the raw text file about to be turned into a shader.
   * @return The context of the text file, or null in case of error.
   */
  private String readRawTextFile(int resId) {
    InputStream inputStream = getResources().openRawResource(resId);
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      reader.close();
      return sb.toString();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public void reset() {
    shots=2;
    mode=1;
    score=0;
  }

  /**
   * Prepares OpenGL ES before we draw a frame.
   *
   * @param headTransform The head transformation in the new frame.
   */
  @Override
  public void onNewFrame(HeadTransform headTransform) {
    frameNo++;

    if (beamFiring) {
      beamDist += 0.4;
      if (beamDist>15) {
        beamFiring = false;
        beamDist=0;
        if (!beamHit) {
          shotFinished(-2);
        }
      }
    }

    for (int i=0; i<3; i++)
      cubePos[i]=cubePos[i]+cubeVel[i]/60f;
    for (int i=0; i<3; i++)
      cubeVel[i]=cubeVel[i]+cubeAccel[i]/60f;
    boolean cubeOut = false;
    if (Math.abs(cubePos[0]) > 4.0f) cubeOut = true;
    if (cubePos[1] < -1.5f) cubeOut = true;
    if (cubePos[1] > 4f-1.5f) cubeOut = true;
    if (Math.abs(cubePos[2]) > 4.0f) cubeOut = true;
    if (cubeOut)
      hideObject();

    // Build the Model part of the ModelView matrix.
    Matrix.rotateM(modelCube, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);

    // Build the camera matrix and apply it to the ModelView.
    Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

    headTransform.getHeadView(headView, 0);
    headTransform.getForwardVector(forwardVector, 0);

    for(int i=0; i<3; i++)
      projectilePos[i]+=projectileVelocity[i]/60.0f;
    projectileVelocity[1]-=9.81/60.0f;

    //Check to see if a projectile has hit a cube:
    {
      boolean hit = true;
      for (int i = 0; i < 3; i++)
        if (Math.abs(projectilePos[i] - cubePos[i]) > 0.2f) hit = false;
      if (hit) {
        shotFinished(2);
        Log.i(TAG, "Object Hit. Score: " + score);
        hideObject();
        //Setting out here prevents loosing point when this poj hits a wall.
        out = true;
      }
      if (!out) {
        if (Math.abs(projectilePos[0]) > 4.0f) out = true;
        if (projectilePos[1] < -1.5f) out = true;
        if (Math.abs(projectilePos[2]) > 4.0f) out = true;
        if (out) {
          shotFinished(-1);
          score--;

          Log.i(TAG, "Object Missed. Score: " + score);
        }
      }
    }

    if (beamFiring) {
      //Check to see if the ray has hit a target
      //We what to interpollate between these:
      //            0.2f, -0.75f, 0f,
      //            0, 0, -10f,

      float[] startVec1 = {0.2f, -0.75f, 0f, 1.0f};
      float[] startVec2 = {0, 0, -10f, 1.0f};
      float[] positionVec1 = new float[4];
      float[] positionVec2 = new float[4];
      float[] intPositionVec = new float[4];

      Matrix.multiplyMV(positionVec1, 0, modelBeam, 0, startVec1, 0);
      Matrix.multiplyMV(positionVec2, 0, modelBeam, 0, startVec2, 0);
      for (int i=0; i<3; i++)
        positionVec1[i]=positionVec1[i]/positionVec1[3];
      for (int i=0; i<3; i++)
        positionVec2[i]=positionVec2[i]/positionVec2[3];
//      if(beamDist<0.5) {
//        Log.i(TAG, "positionVec1 " + positionVec1[0] + "  " + positionVec1[1] + "  " + positionVec1[2]);
//        Log.i(TAG, "positionVec2 " + positionVec2[0] + "  " + positionVec2[1] + "  " + positionVec2[2]);
//      }
      for (float interoplateFactor = 0; interoplateFactor < 1; interoplateFactor += 0.01) {
        //Don't test points the beam has not reached
        if (interoplateFactor*10.0 > beamDist) {
//          Log.i(TAG, "interoplateFactor " + interoplateFactor + " beamDist " + beamDist);
          break;
        }
        //Don't test points the end of the beam has gone past
        if (interoplateFactor*10.0 > (beamDist-10)) {
          boolean hit = true;
          for (int i = 0; i < 3; i++) {
            intPositionVec[i] = interoplateFactor * (positionVec2[i] - positionVec1[i]) + positionVec1[i];
            if (Math.abs(intPositionVec[i] - cubePos[i]) > 0.2f)
              hit = false;
          }
//          if (beamDist < 0.5)
//            Log.i(TAG, "Int: " + interoplateFactor + " test point:"
//                    + intPositionVec[0] + "  " + intPositionVec[1] + "  " + intPositionVec[2] + " Diff: "
//                    + Math.abs(intPositionVec[0] - cubePos[0]) + "  "
//                    + Math.abs(intPositionVec[1] - cubePos[1]) + "  "
//                    + Math.abs(intPositionVec[2] - cubePos[2]));
          if (hit) {
            Log.i(TAG, "Object hit by beam");
            beamHit = true;
            shotFinished(2);
            //Should now create flare effect
            //This is cheating, will not work if beam coming from another point:
            float[] billboardt = new float[16];
            float[] billboardr = new float[16];
            float[] billboardir = new float[16];
//            float invHeadView[] = new float[16];
//            Matrix.invertM(invHeadView, 0, headView, 0);
            Matrix.setIdentityM(billboardt, 0);
            Matrix.setLookAtM(billboardr, 0, 0, 0, 0, intPositionVec[0], intPositionVec[1], intPositionVec[2], 0, 1, 0);
            Matrix.invertM(billboardir, 0, billboardr, 0);
            Matrix.translateM(billboardt, 0, intPositionVec[0], intPositionVec[1], intPositionVec[2]);
            Matrix.multiplyMM(modelFlare, 0, billboardt, 0, billboardir, 0);
//            Matrix.multiplyMM(modelFlare, 0, billboardt, 0, invHeadView, 0);
//            Matrix.scaleM(modelFlare, 0, .5f, .5f, .5f);
            flareStartFrame=frameNo;
            hideObject();
            break;
          }
        }
      }
//      if(beamDist<0.5)
//        Log.v(TAG, "Cube pos: " + cubePos[0] + "  " + cubePos[1] + "  "+ cubePos[2] + "  ");
    }

    if (textimagelock.tryLock()) {
      if (textRenderFinished) {
        signTextureReady=true;
        UpdateTexture(signTexture, textBitmap);
        textRenderFinished=false;
      }
      textimagelock.unlock();
    }

    if (reticleBitmaplock.tryLock()) {
      if (reticleRenderFinished) {
        UpdateTexture(reticleTexture, reticleBitmap);
        reticleRenderFinished=false;
      }
      reticleBitmaplock.unlock();
    }

    checkGLError("onReadyToDraw");
  }


  public void shotFinished(int scoreDelta) {
    score+=scoreDelta;
    String message;
    int messagetime = 1500;

    if (scoreDelta>0)
      message="You hit it.\n";
    else
      message="You missed it.\n";
      if (shots > 0)
        message=message+"Score: " + score + "\n" + shots + " Shots left";
      else {
        mode++;
        shots=10;
        if (mode == 5) {
          message=message+"Game Over\nScore: " + score;
          messagetime=10000;
          mode=0;
        } else
          message=message+"Level " + mode + "\nScore: " + score;
      }
    show3DToast(message, messagetime);
  }

  /**
   * Draws a frame for an eye.
   *
   * @param eye The eye to render. Includes all required transformations.
   */
  @Override
  public void onDrawEye(Eye eye) {
    GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    checkGLError("colorParam");

    // Apply the eye transformation to the camera.
    Matrix.multiplyMM(viewMatrix, 0, eye.getEyeView(), 0, camera, 0);

    // Set the position of the light
    Matrix.multiplyMV(lightPosInEyeSpace, 0, viewMatrix, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

    // Build the ModelView and ModelViewProjection matrices
    // for calculating cube position and light.
    float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);

    if (mode>0) {
      Matrix.setIdentityM(modelCube, 0);
      Matrix.translateM(modelCube, 0, cubePos[0], cubePos[1], cubePos[2]);
      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelCube, 0);
      Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelViewMatrix, 0);
      drawCube();
    }

    Matrix.setIdentityM(modelProjectile, 0);
    Matrix.translateM(modelProjectile, 0, projectilePos[0], projectilePos[1], projectilePos[2]);
    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelProjectile, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelViewMatrix, 0);
    drawProjectile();

//     Set modelViewMatrix for the floor, so we draw floor in the correct location
    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelFloor, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0,
            modelViewMatrix, 0);
    drawFloor();

    Matrix.setIdentityM(modelMatrix, 0);
//    Matrix.rotateM(modelBeam, 0, 45, 0, 1, 0);
    Matrix.translateM(modelMatrix, 0, 2, -1, -2);
    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0,
            modelViewMatrix, 0);
    drawAxis();

    GLES20.glEnable(GLES20.GL_BLEND);
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);


    float trans = 1f;
    if (frameNo>signFadeFrame)
      trans = 1-(((float) frameNo-(float) signFadeFrame) / 100f);
    if (signTextureReady && frameNo<(signFadeFrame+100)) {
      for (int i =0; i<4; i++) {
        //Draw Sign:
        Matrix.setIdentityM(modelMatrix, 0);
//    Matrix.rotateM(modelBeam, 0, 45, 0, 1, 0);
        Matrix.rotateM(modelMatrix, 0, 90 * i, 0, 1, 0);
        Matrix.translateM(modelMatrix, 0, 0.1f, -0.05f, -3.5f);
        Matrix.scaleM(modelMatrix, 0, .5f, .5f, .5f);
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0,
                modelViewMatrix, 0);
//      Log.v(TAG, "Trans" +trans + " frameNo " + frameNo + " signFadeFrame " + signFadeFrame);
        drawRect(signTexture, trans);
      }
    }


    float invHeadView[] = new float[16];
    Matrix.invertM(invHeadView, 0, headView, 0);
    if (beamFiring) {
//      Matrix.setIdentityM(modelBeam, 0);
//    Matrix.rotateM(modelBeam, 0, 45, 0, 1, 0);
//      Matrix.multiplyMM(modelBeam, 0, invHeadView, 0, modelBeam, 0);
      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelBeam, 0);
      Matrix.multiplyMM(modelViewProjection, 0, perspective, 0,
              modelViewMatrix, 0);
      drawBeam();
    }

    GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    if (frameNo-flareStartFrame > 0 && frameNo-flareStartFrame < 51) {
      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelFlare, 0);
      Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelViewMatrix, 0);
      drawFlare();
    }
//    drawAxis();
    GLES20.glEnable(GLES20.GL_DEPTH_TEST);

    //Draw the Reticle (this must be done last due to transparency)
    Matrix.setIdentityM(modelMatrix, 0);
    Matrix.translateM(modelMatrix, 0, 0, 0, -1.5f);
    Matrix.scaleM(modelMatrix, 0, .05f, .05f, .05f);
    Matrix.multiplyMM(modelMatrix, 0, invHeadView, 0, modelMatrix, 0);
    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0,
            modelViewMatrix, 0);
    drawRect(reticleTexture, 1);

    GLES20.glDisable(GLES20.GL_BLEND);

  }

  @Override
  public void onFinishFrame(Viewport viewport) {

  }


  /**
   * Draw the cube.
   *
   * <p>We've set all of our transformation matrices. Now we simply pass them into the shader.
   */
  public void drawCube() {
    GLES20.glUseProgram(cubeProgram);

    GLES20.glUniform3fv(cubeLightPosParam, 1, lightPosInEyeSpace, 0);

    // Set the Model in the shader, used to calculate lighting
    GLES20.glUniformMatrix4fv(cubeModelParam, 1, false, modelCube, 0);

    // Set the ModelView in the shader, used to calculate lighting
    GLES20.glUniformMatrix4fv(cubeModelViewParam, 1, false, modelViewMatrix, 0);

    // Set the position of the cube
    GLES20.glVertexAttribPointer(cubePositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
            false, 0, cubeVertices);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(cubeModelViewProjectionParam, 1, false, modelViewProjection, 0);

    // Set the normal positions of the cube, again for shading
    GLES20.glVertexAttribPointer(cubeNormalParam, 3, GLES20.GL_FLOAT, false, 0, cubeNormals);
    GLES20.glVertexAttribPointer(cubeColorParam, 4, GLES20.GL_FLOAT, false, 0, cubeColors);
//
    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
    checkGLError("Drawing cube");
  }

  public void drawProjectile() {

    GLES20.glUseProgram(cubeProgram);

    GLES20.glUniform3fv(cubeLightPosParam, 1, lightPosInEyeSpace, 0);

    // Set the Model in the shader, used to calculate lighting
    GLES20.glUniformMatrix4fv(cubeModelParam, 1, false, modelProjectile, 0);

    // Set the ModelView in the shader, used to calculate lighting
    GLES20.glUniformMatrix4fv(cubeModelViewParam, 1, false, modelViewMatrix, 0);

    // Set the position of the cube
    GLES20.glVertexAttribPointer(cubePositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
            false, 0, cubeVertices);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(cubeModelViewProjectionParam, 1, false, modelViewProjection, 0);

    // Set the normal positions of the cube, again for shading
    GLES20.glVertexAttribPointer(cubeNormalParam, 3, GLES20.GL_FLOAT, false, 0, cubeNormals);
    GLES20.glVertexAttribPointer(cubeColorParam, 4, GLES20.GL_FLOAT, false, 0, cubeFoundColors);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
    checkGLError("Drawing cube");
  }

  public void drawBeam() {
    GLES20.glUseProgram(beamProgram);

    // Set the position of the beam
    GLES20.glVertexAttribPointer(beamPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
            false, 0, beamVertices);
    GLES20.glVertexAttribPointer(beamCoordParam, 1, GLES20.GL_FLOAT,
            false, 0, beamTXCoords);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(beamModelViewProjectionParam, 1, false, modelViewProjection, 0);

    GLES20.glUniform1f(beamMaxDepthParam, beamDist);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    checkGLError("Drawing Beam");
  }

  public void drawRect(int texture, float trans) {
    GLES20.glUseProgram(txProgram);

    // Set the position of the beam
    GLES20.glVertexAttribPointer(txPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
            false, 0, rectVertices);
    GLES20.glVertexAttribPointer(txCoordParam, 2, GLES20.GL_FLOAT, false, 0,
            rectTXCoords);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(txModelViewProjectionParam, 1, false, modelViewProjection, 0);
    GLES20.glUniform1f(txTransParam, trans);

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    checkGLError("Drawing Rect");
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
    checkGLError("Drawing Rect");
    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    checkGLError("Drawing Rect");
  }

  public void drawFlare() {
    GLES20.glUseProgram(flareProgram);

    // Set the position of the beam
    GLES20.glVertexAttribPointer(flarePositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
            false, 0, rectVertices);
    GLES20.glVertexAttribPointer(flareCoordParam, 2, GLES20.GL_FLOAT, false, 0,
            rectTXCoords);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(flareModelViewProjectionParam, 1, false, modelViewProjection, 0);
    GLES20.glUniform1f(flareRadiusParam, ((float)(frameNo-flareStartFrame))/50f);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    checkGLError("Drawing Rect");
  }

  public void drawAxis() {
    GLES20.glUseProgram(plainProgram);

    // Set the position of the beam
    GLES20.glVertexAttribPointer(plainPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
            false, 0, axisVertices);
    GLES20.glVertexAttribPointer(plainColorParam, 3, GLES20.GL_FLOAT, false, 0,
            axisColors);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(plainModelViewProjectionParam, 1, false, modelViewProjection, 0);

    GLES20.glLineWidth(2);
    GLES20.glDrawArrays(GLES20.GL_LINES, 0, 18);
    checkGLError("Drawing Axis");
  }

  /**
   * Draw the floor.
   *
   * <p>This feeds in data for the floor into the shader. Note that this doesn't feed in data about
   * position of the light, so if we rewrite our code to draw the floor first, the lighting might
   * look strange.
   */
  public void drawFloor() {
    GLES20.glDisable(GLES20.GL_CULL_FACE);
    GLES20.glUseProgram(floorProgram);

    // Set ModelView, MVP, position, normals, and color.
    GLES20.glUniform3fv(floorLightPosParam, 1, lightPosInEyeSpace, 0);
    GLES20.glUniformMatrix4fv(floorModelParam, 1, false, modelFloor, 0);
    GLES20.glUniformMatrix4fv(floorModelViewParam, 1, false, modelViewMatrix, 0);
    GLES20.glUniformMatrix4fv(floorModelViewProjectionParam, 1, false,
            modelViewProjection, 0);
    GLES20.glVertexAttribPointer(floorPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
        false, 0, floorVertices);
    GLES20.glVertexAttribPointer(floorCoordParam, 2, GLES20.GL_FLOAT, false, 0,
        floorNormals);
//    GLES20.glVertexAttribPointer(floorColorParam, 4, GLES20.GL_FLOAT, false, 0, floorColors);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6 * 6);

    checkGLError("drawing floor");
  }

  /**
   * Called when the Cardboard trigger is pulled.
   */
  @Override
  public void onCardboardTrigger() {
    Log.i(TAG, "onCardboardTrigger");

//    vibrator.vibrate(50);
    if (mode == 1 && out && shots > 0) {
      Log.i(TAG, "Throwing");
      projectilePos = new float[]{0, -.75f, 0, 1};
      projectileVelocity = new float[]{-forwardVector[0] * 5, (1 - forwardVector[1]) * 5, forwardVector[2] * 5, 0};
      Log.i(TAG, "Forward Vect: " + forwardVector[0] + " " + forwardVector[1] + " " + forwardVector[2]);
      out = false;
      shots--;
    }
    if (mode > 1 && out && shots > 0)
    {
      Log.i(TAG, "Firing");
      shots--;
      if (!beamFiring) {
        beamFiring = true;
        beamHit = false;
        float invHeadView[] = new float[16];
        Matrix.invertM(invHeadView, 0, headView, 0);
        Matrix.setIdentityM(modelBeam, 0);
        Matrix.multiplyMM(modelBeam, 0, invHeadView, 0, modelBeam, 0);
      }
    }
    if (mode == 0)
      reset();
      // Always give user feedback.
      vibrator.vibrate(20);
  }

  /**
   * Find a new random position for the object.
   *
   * <p>We'll rotate it around the Y-axis so it's out of sight, and then up or down by a little bit.
   */
  private void hideObject() {
    float[] rotationMatrix = new float[16];
    float[] posVeca = {0,0,-1,1};
    float[] posVec = {0,0,0,1};

    // First rotate in XZ plane, between 90 and 270 deg away, and scale so that we vary
    // the object's distance from the user.
    float angleXZ = (float) Math.random() * 180 - 90;
    Matrix.setRotateM(rotationMatrix, 0, angleXZ, 0f, 1f, 0f);
//    float oldObjectDistance = objectDistance;
    objectDistance = (float) Math.random() * 3 + 1;

    float objectScalingFactor = objectDistance;// / oldObjectDistance;
    Matrix.scaleM(rotationMatrix, 0, objectScalingFactor, objectScalingFactor,
        objectScalingFactor);
    Matrix.multiplyMV(posVec, 0, rotationMatrix, 0, posVeca, 0);

    // Now get the up or down angle, between -20 and 20 degrees.
//    float angleY = (float) Math.random() * 80 - 40; // Angle in Y plane, between -40 and 40.
//    angleY = (float) Math.toRadians(angleY);
//    float newY = (float) Math.tan(angleY) * objectDistance;
    float newY = (float) Math.random() * (floorDepth -0.04f) * 2 - (floorDepth -0.04f);
    Log.i(TAG, "hideObject() Radi: XZ: " + angleXZ + "  R: " + objectDistance);
    Log.i(TAG, "hideObject() Cart:  X: " + posVec[0] + "  Y(Height): " + newY + "  Z: " + posVec[2]);
    cubePos = new float[] {posVec[0], newY, posVec[2]};

    for (int i=0; i<3; i++) {
      if (mode > 2)
        cubeVel[i] = (float) Math.random() * 2.0f - 1.0f;
      else
        cubeVel[i] = 0;
      if (mode == 4)
        cubeAccel[i] = (float) Math.random() * 0.4f - 0.8f;
      else
        cubeAccel[i] = 0;
    }
    Log.i(TAG, "cubeVel:  X: " + cubeVel[0] + "  Y: " + cubeVel[1] + "  Z: " + cubeVel[2]);

  }

  /**
   * Check if user is looking at object by calculating where the object is in eye-space.
   *
   * @return true if the user is looking at the object.
   */
  private boolean isLookingAtObject() {
    float[] initVec = { 0, 0, 0, 1.0f };
    float[] objPositionVec = new float[4];

    // Convert object space to camera space. Use the headView from onNewFrame.
    Matrix.multiplyMM(modelViewMatrix, 0, headView, 0, modelCube, 0);
    Matrix.multiplyMV(objPositionVec, 0, modelViewMatrix, 0, initVec, 0);

    float pitch = (float) Math.atan2(objPositionVec[1], -objPositionVec[2]);
    float yaw = (float) Math.atan2(objPositionVec[0], -objPositionVec[2]);

    return Math.abs(pitch) < PITCH_LIMIT && Math.abs(yaw) < YAW_LIMIT;
  }

  Handler mainLoopHandler = new Handler(Looper.getMainLooper());

  //Text Rendering:
  TextViewUpdater textViewUpdater = new TextViewUpdater();
  private final ReentrantLock textimagelock = new ReentrantLock();
  int signTexture = 0;
  boolean signTextureReady = false;

  //Protected by textimagelock:
  private Bitmap textBitmap;
  private boolean textRenderFinished=false;

  private class TextViewUpdater implements Runnable{
    private String txt;
    private int time;

    @Override
    public void run() {
      Log.i(TAG, "TextViewUpdater");
      if (Looper.myLooper() == Looper.getMainLooper())
        Log.i(TAG, "In UI thread");
      else
        Log.i(TAG, "Not in UI thread");
//      overlayView.show3DToast(txt, time);

      textimagelock.lock();
      // Create an empty, mutable textBitmap
      textBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_4444);

      textBitmap.eraseColor(Color.TRANSPARENT);

      // get a canvas to paint over the textBitmap
      Canvas canvas = new Canvas(textBitmap);

      Paint paint=new Paint();
      paint.setAntiAlias(true);
      paint.setStrokeWidth(0);
      paint.setColor(Color.WHITE);
      RectF rectF = new RectF(0f,64f,256f,64f+128f);
      canvas.drawRoundRect(rectF, 7, 7, paint);

      // get a background image from resources
// note the image format must match the textBitmap format
//    Drawable background = overlayView.getResources().getDrawable(R.drawable.background);
//    background.setBounds(0, 0, 256, 128);
//    background.draw(canvas); // draw the background to our textBitmap


      TextPaint mTextPaint=new TextPaint();
      mTextPaint.setTextSize(32);
      mTextPaint.setAntiAlias(true);
      mTextPaint.setARGB(0xff, 0x00, 0x00, 0x00);

      StaticLayout mTextLayout;
      mTextLayout = new StaticLayout(txt,
              mTextPaint,
              canvas.getWidth(),
              Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

      canvas.save();
// calculate x and y position where your text will be placed

      int textX = 0;
      int textY = (128-mTextLayout.getHeight())/2 + 64;

      canvas.translate(textX, textY);
      mTextLayout.draw(canvas);
      canvas.restore();

      textRenderFinished=true;
      textimagelock.unlock();

    }
    public void setText(String txt){
      this.txt = txt;
    }
    public void setTime(int time){
      this.time = time;
    }
  }

    public void UpdateTexture(int texture, Bitmap bitmap) {
      Log.i(TAG, "TextViewUpdaterFinished");
      if (Looper.myLooper() == Looper.getMainLooper())
        Log.e(TAG, "In UI thread");

//Use the Android GLUtils to specify a two-dimensional signTexture image from our textBitmap

      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
      checkGLError("UpdateTextTextureFinished");
    }

  private void show3DToast(String message) {
    show3DToast(message, 5000);
  }

  private void show3DToast(String message, int time) {
    textViewUpdater.setText(message);
    signFadeFrame=frameNo+(time/16);
    signTextureReady=false;
    mainLoopHandler.post(textViewUpdater);
  }

  //Reticle Rendering:
  ReticleUpdater reticleUpdater = new ReticleUpdater();
  private final ReentrantLock reticleBitmaplock = new ReentrantLock();
  int reticleTexture=0;

  //Protected by reticleimagelock:
  private Bitmap reticleBitmap;
  private boolean reticleRenderFinished=false;

  private class ReticleUpdater implements Runnable{
    private int type;
    private Color colour;

    @Override
    public void run() {
      Log.i(TAG, "TextViewUpdater");
      if (Looper.myLooper() == Looper.getMainLooper())
        Log.i(TAG, "In UI thread");
      else
        Log.i(TAG, "Not in UI thread");
//      overlayView.show3DToast(txt, time);

      reticleBitmaplock.lock();
      // Create an empty, mutable textBitmap
      reticleBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_4444);

      reticleBitmap.eraseColor(Color.TRANSPARENT); //White

      // get a canvas to paint over the textBitmap
      Canvas canvas = new Canvas(reticleBitmap);
      Paint paint=new Paint();
      paint.setAntiAlias(true);
      paint.setStrokeWidth(2);
      paint.setColor(Color.DKGRAY);

      canvas.translate(16, 16);
      canvas.drawLine(-14, -14, 14, 14, paint);
      canvas.drawLine(14, -14, -14, 14, paint);

      reticleRenderFinished=true;
      reticleBitmaplock.unlock();

    }
    public void setType(int type){
      this.type = type;
    }
    public void setColour(Color colour){
      this.colour = colour;
    }
  }

  private void updateReticle(int type) {
    reticleUpdater.setType(type);
    mainLoopHandler.post(reticleUpdater);
  }

}