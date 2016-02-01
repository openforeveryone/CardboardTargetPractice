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

package com.ofemobile.targetvr;

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
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.File;
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
  private int stripProgram;

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

  private int stripModelViewProjectionParam;
  private int stripPositionParam;
  private int stripCoordParam;

  private float[] modelCube;
  private float[] camera;
  private float[] viewMatrix;
  private float[] headView;
  private float[] invHeadView;
  private float[] modelViewProjection;
  private float[] modelViewMatrix;
  private float[] modelFloor;
  private float[] modelProjectile;
  private float[] projectileRotation;
  private float[] modelBeam;
  private float[] modelFlare;
  private float[] modelReticle;
  private float[] modelMatrix;

  private float[] projectilePos = {1,0,0,1};
  private float[] projectileVelocity = {1,1,0,0};
  private float[] cubePos = {0,0,0,0};
  private float[] cubeVel = {0,0,0,0};
  private float[] cubeAccel = {0,0,0,0};

  private float[] perspective;


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

  //EGL state for renderer:
  EGLDisplay mScreenEglDisplay;
  EGLSurface mScreenEglDrawSurface;
  EGLSurface mScreenEglReadSurface;
  EGLContext mScreenEglContext;

  private VideoEncoder mVideoEncoder;

  float[] lookup = new float[16];
  float[] lookdown = new float[16];

  int stripFramebuffer;
  int stripDepthRenderbuffer;
  int stripTexture;


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
    invHeadView = new float[16];
    modelProjectile = new float[16];
    modelBeam = new float[16];
    modelMatrix = new float[16];
    modelFlare = new float[16];
    modelReticle = new float[16];
    projectileRotation = new float[16];
    Matrix.setIdentityM(projectileRotation, 0);
    vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);


    overlayView = (CardboardOverlayView) findViewById(R.id.overlay);
    show3DToast("Pull the magnet when you find a target.", 5000);

    Log.i(TAG, "onCreate");
    if (Looper.myLooper() == Looper.getMainLooper())
      Log.i(TAG, "In UI thread");
    else
      Log.i(TAG, "Not in UI thread");

    reset();

    getCardboardView().getCardboardDeviceParams();
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
    int stripFragShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.strip_fragment);

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

    //For vr video rendering:
    stripProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(stripProgram, gridvertexShader);
    GLES20.glAttachShader(stripProgram, stripFragShader);
    GLES20.glLinkProgram(stripProgram);
    GLES20.glUseProgram(stripProgram);
    checkGLError("Strip program");

    stripModelViewProjectionParam = GLES20.glGetUniformLocation(stripProgram, "u_MVP");
    stripPositionParam = GLES20.glGetAttribLocation(stripProgram, "a_Position");
    stripCoordParam = GLES20.glGetAttribLocation(stripProgram, "a_Coord");
    checkGLError("Tx program params");

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

    show3DToast("Find the target cube then pull the magnet", 10000);

    checkGLError("onSurfaceCreated");

    //Backup the context
    mScreenEglDisplay = EGL14.eglGetCurrentDisplay();
    mScreenEglDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
    mScreenEglReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
    mScreenEglContext = EGL14.eglGetCurrentContext();

    //Setup the video surface and encoder
    mVideoEncoder = new VideoEncoder();
    //Full HD:
    mVideoEncoder.prepare(1920, 1080, 16000000, mScreenEglContext);
    //4K:
//    mVideoEncoder.prepare(3840, 2160, 40000000, mScreenEglContext);

    //Setup render to texture
    int[] stripFramebufferArray = new int[1];
    int[] stripTextureArray = new int[1];
    int[] stripDepthRenderbufferArray = new int[1];
    GLES20.glGenFramebuffers(1, stripFramebufferArray, 0);
    stripFramebuffer=stripFramebufferArray[0];
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, stripFramebuffer);
    GLES20.glGenTextures(1, stripTextureArray, 0);
    stripTexture=stripTextureArray[0];
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stripTexture);

    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 2, mVideoEncoder.height()/2, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

    GLES20.glGenRenderbuffers(1, stripDepthRenderbufferArray, 0);
    stripDepthRenderbuffer=stripDepthRenderbufferArray[0];
    GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, stripDepthRenderbuffer);
    GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, 2, mVideoEncoder.height()/2);

    GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, stripDepthRenderbuffer);
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, stripTexture, 0);


    if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
      Log.e(TAG, "glCheckFramebufferStatus != GL_FRAMEBUFFER_COMPLETE");
      finish();
      return;
    }

    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

    Matrix.setIdentityM(lookup, 0);
    Matrix.setIdentityM(lookdown, 0);
    Matrix.rotateM(lookup, 0, -45, 1, 0, 0);
    Matrix.rotateM(lookdown, 0, 45, 1, 0, 0);

    //Restore the rendering context
    EGL14.eglMakeCurrent(mScreenEglDisplay, mScreenEglDrawSurface,
            mScreenEglReadSurface, mScreenEglContext);
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
    shots=10;
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

    checkGLError("onNewFrame");

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
    Matrix.rotateM(projectileRotation, 0, 3*TIME_DELTA, 0.5f, 0.5f, 1.0f);

    // Build the camera matrix and apply it to the ModelView.
    Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

    headTransform.getHeadView(headView, 0);
    Matrix.invertM(invHeadView, 0, headView, 0);
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

    if (!beamFiring) {
      Matrix.setIdentityM(modelBeam, 0);
      Matrix.multiplyMM(modelBeam, 0, invHeadView, 0, modelBeam, 0);
    }

    if (mode > 1) {
//      Log.i(TAG, "Checking to see if the ray has hit a target");
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
      for (float roughInteroplateFactor = 0; roughInteroplateFactor < 1; roughInteroplateFactor += 0.01) {
//          Log.i(TAG, "interoplateFactor " + interoplateFactor + " beamDist " + beamDist);
        boolean hit = true;
        for (int i = 0; i < 3; i++) {
          intPositionVec[i] = roughInteroplateFactor * (positionVec2[i] - positionVec1[i]) + positionVec1[i];
          if (Math.abs(intPositionVec[i] - cubePos[i]) > 0.12f)
            hit = false;
        }
        if (hit)
        {
//          Log.i(TAG, "Rough search made hit at: int:"+roughInteroplateFactor+" X: " + intPositionVec[0] + "  Y: " + intPositionVec[1] + "  Z: " + intPositionVec[2]);
          for (float interoplateFactor = roughInteroplateFactor-0.005f; interoplateFactor < roughInteroplateFactor+0.005f; interoplateFactor += 0.001f)
          {
            hit = true;
            for (int i = 0; i < 3; i++) {
              intPositionVec[i] = interoplateFactor * (positionVec2[i] - positionVec1[i]) + positionVec1[i];
              if (Math.abs(intPositionVec[i] - cubePos[i]) > 0.12f)
                hit = false;
            }
            if (hit) {
//              Log.i(TAG, "Precise search made hit at: int:"+interoplateFactor+" X: " + intPositionVec[0] + "  Y: " + intPositionVec[1] + "  Z: " + intPositionVec[2]);
              break;
            }
          }
          if (!hit)
            Log.e(TAG, "Error: Precise search failed");
        }
        boolean wallhit = false;
          if (Math.abs(intPositionVec[0]) >= 4f)
            wallhit = true;
          if (Math.abs(intPositionVec[2]) >= 4f)
            wallhit = true;
          if (intPositionVec[1] <= -1.5f)
            wallhit = true;
          if (intPositionVec[1] >= 4.0f-1.5f)
            wallhit = true;
//          if (beamDist < 0.5)
//            Log.i(TAG, "Int: " + interoplateFactor + " test point:"
//                    + intPositionVec[0] + "  " + intPositionVec[1] + "  " + intPositionVec[2] + " Diff: "
//                    + Math.abs(intPositionVec[0] - cubePos[0]) + "  "
//                    + Math.abs(intPositionVec[1] - cubePos[1]) + "  "
//                    + Math.abs(intPositionVec[2] - cubePos[2]));
          if (beamFiring && hit && roughInteroplateFactor*10.0 < beamDist && roughInteroplateFactor*10.0 > (beamDist-10)) {
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
            Matrix.scaleM(modelFlare, 0, .5f, .5f, .5f);
            flareStartFrame=frameNo;
            hideObject();
          }
          if (hit || wallhit)
          {
//            Log.i(TAG, "The ray has hit a target " + intPositionVec[0] + " " + intPositionVec[1] + " " +  intPositionVec[2]);
            float[] billboardt = new float[16];
            float[] billboardr = new float[16];
            float[] billboardir = new float[16];
            Matrix.setIdentityM(billboardt, 0);
            Matrix.setLookAtM(billboardr, 0, 0, 0, 0, intPositionVec[0], intPositionVec[1], intPositionVec[2], 0, 1, 0);
            Matrix.invertM(billboardir, 0, billboardr, 0);
            Matrix.translateM(billboardt, 0, intPositionVec[0], intPositionVec[1], intPositionVec[2]);
            if (hit)
              Matrix.translateM(billboardt, 0, 0.0f, 0.0f, 0.0f);
            else
              Matrix.translateM(billboardt, 0, 0.0f, 0.0f, 0.01f);
            Matrix.multiplyMM(modelReticle, 0, billboardt, 0, billboardir, 0);
//            Matrix.multiplyMM(modelFlare, 0, billboardt, 0, invHeadView, 0);
            Matrix.scaleM(modelReticle, 0, .25f/2f, .25f/2f, .25f/2f);
            break;
          }
        }
    }else
    {
//      Log.i(TAG, "Set the Reticle in fixed pos");
      //We are on level one (or game over screen) Reticle in fixed pos
      Matrix.setIdentityM(modelReticle, 0);
      Matrix.translateM(modelReticle, 0, 0, 0, -1.5f);
      Matrix.scaleM(modelReticle, 0, .05f, .05f, .05f);
      Matrix.multiplyMM(modelReticle, 0, invHeadView, 0, modelReticle, 0);
    }

    checkGLError("onNewFrame");

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

    //Autofire
    if(frameNo==2)
    {
      projectilePos = new float[]{0, -.75f, 0, 1};
      projectileVelocity = new float[]{0, 4, -8, 1};
      Log.i(TAG, "Autofire projectileVelocity Vect: " + projectileVelocity[0] + " " + projectileVelocity[1] + " " + projectileVelocity[2]);
      out = false;
      shots--;
    }
    checkGLError("onReadyToDraw");

    //Backup the screen context
    mScreenEglDisplay = EGL14.eglGetCurrentDisplay();
    mScreenEglDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
    mScreenEglReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
    mScreenEglContext = EGL14.eglGetCurrentContext();

    int maxVideoFrames = 1;
    if (frameNo<=maxVideoFrames) {
      //Switch to the recording context
      mVideoEncoder.inputSurface().makeCurrent();
      mVideoEncoder.drain(false);
      Log.i(TAG, "Generating frame " + frameNo);
      generateVideoFrame(mVideoEncoder.width(), mVideoEncoder.height());
      mVideoEncoder.inputSurface().setPresentationTime((long)(frameNo*(1000000000f/30f)));
      mVideoEncoder.inputSurface().swapBuffers();
      //Restore the screen context
      EGL14.eglMakeCurrent(mScreenEglDisplay, mScreenEglDrawSurface,
              mScreenEglReadSurface, mScreenEglContext);
    }
    if (frameNo==maxVideoFrames)
    {
      mVideoEncoder.drain(true);
      mVideoEncoder.release();
      Log.i(TAG, "Recording Finished");
      //Restore the screen context
      EGL14.eglMakeCurrent(mScreenEglDisplay, mScreenEglDrawSurface,
              mScreenEglReadSurface, mScreenEglContext);
    }

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

    private void generateVideoFrame(int width, int height) {

      float ipd_2 = 0.06f/2f;

      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
      GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f);

      perspective = new float[16];
      Matrix.perspectiveM(perspective, 0, 90, (float)1/(float)height,0.5f,10);

      // Apply the eye transformation to the camera.
      float[] eyePos = new float[16];
      float[] eye = new float[16];
      Matrix.setIdentityM(eye, 0);
      float[] halfEye = new float[16];
      Matrix.setIdentityM(halfEye, 0);
      float[] stripPaint = new float[16];
      Matrix.setIdentityM(stripPaint, 0);
      Matrix.scaleM(stripPaint, 0, 1f/(float)width, -1f/4f, 1);
      Matrix.translateM(stripPaint, 0, 0, -3f, 0);
      Matrix.translateM(stripPaint, 0, -((float)width-1f), 0, 0);

      Matrix.setIdentityM(viewMatrix, 0);
      float[] rotation = new float[16];

      //Pixel angular width:
      float apwidth = 360f/(float)width;
//      int whichEye = 0;
//      int upDown = 1;
      for (int whichEye = 0; whichEye<2; whichEye++) {
        for (int upDown = 0; upDown<2; upDown++) {
          if (upDown==0)
            Matrix.multiplyMM(halfEye, 0, lookup, 0, eye, 0);
          else
            Matrix.multiplyMM(halfEye, 0, lookdown, 0, eye, 0);
          for (int i = 0; i < width; i++) {
            float angleDeg = apwidth * (float) i - 180;
            float angleRad = -angleDeg / 360f * (2f * (float) Math.PI);
            Matrix.setRotateM(rotation, 0, angleDeg, 0, 1, 0);
            Matrix.setIdentityM(eyePos, 0);
            if (whichEye==1)
              Matrix.translateM(eyePos, 0, (float) -Math.cos(angleRad) * ipd_2, 0, (float) Math.sin(angleRad) * ipd_2);
            else
              Matrix.translateM(eyePos, 0, (float) -Math.cos(angleRad+Math.PI) * ipd_2, 0, (float) Math.sin(angleRad+Math.PI) * ipd_2);

            Matrix.multiplyMM(viewMatrix, 0, eyePos, 0, camera, 0);
            Matrix.multiplyMM(viewMatrix, 0, rotation, 0, viewMatrix, 0);
            Matrix.multiplyMM(viewMatrix, 0, halfEye, 0, viewMatrix, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, stripFramebuffer);
            GLES20.glViewport(0, 0, 2, height/2);

//        GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            renderScene();
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glViewport(0, 0, width, height);

            GLES20.glUseProgram(stripProgram);
            GLES20.glEnableVertexAttribArray(stripPositionParam);
            GLES20.glEnableVertexAttribArray(stripCoordParam);
            GLES20.glVertexAttribPointer(stripPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                    false, 0, rectVertices);
            GLES20.glVertexAttribPointer(stripCoordParam, 2, GLES20.GL_FLOAT, false, 0,
                    rectTXCoords);

            GLES20.glUniformMatrix4fv(stripModelViewProjectionParam, 1, false, stripPaint, 0);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stripTexture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
            checkGLError("Drawing");

//      Move right 1px
            Matrix.translateM(stripPaint, 0, 2, 0, 0);
          }
          Matrix.translateM(stripPaint, 0, -((float)width*2.0f), 0, 0);
          Matrix.translateM(stripPaint, 0, 0, 2f, 0);
        }
      }
  }

    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
  @Override
  public void onDrawEye(Eye eye) {

    // Apply the eye transformation to the camera.
    Matrix.multiplyMM(viewMatrix, 0, eye.getEyeView(), 0, camera, 0);

    // Build the ModelView and ModelViewProjection matrices
    // for calculating cube position and light.
    perspective = eye.getPerspective(Z_NEAR, Z_FAR);

    renderScene();
  }

  private void renderScene()
  {
    GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    checkGLError("colorParam");
    // Set the position of the light
    Matrix.multiplyMV(lightPosInEyeSpace, 0, viewMatrix, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

    if (mode>0) {
      Matrix.setIdentityM(modelCube, 0);
      Matrix.translateM(modelCube, 0, cubePos[0], cubePos[1], cubePos[2]);
      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelCube, 0);
      Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelViewMatrix, 0);
      drawCube();
    }

    float[] modelProjectilei = new float[16];
    Matrix.setIdentityM(modelProjectilei, 0);
    Matrix.translateM(modelProjectilei, 0, projectilePos[0], projectilePos[1], projectilePos[2]);
    Matrix.multiplyMM(modelProjectile, 0, modelProjectilei, 0, projectileRotation, 0);
    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelProjectile, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelViewMatrix, 0);
    drawProjectile();

//     Set modelViewMatrix for the floor, so we draw floor in the correct location
    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelFloor, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelViewMatrix, 0);
    drawFloor();

    Matrix.setIdentityM(modelMatrix, 0);
    Matrix.translateM(modelMatrix, 0, 2, -floorDepth+0.1f, -2);
    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelViewMatrix, 0);
    drawAxis();

    GLES20.glEnable(GLES20.GL_BLEND);
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

    float trans = 1f;
    if (frameNo > signFadeFrame)
      trans = 1 - (((float) frameNo - (float) signFadeFrame) / 100f);
    if (signTextureReady && frameNo < (signFadeFrame + 100)) {
      for (int i = 0; i < 4; i++) {
        //Draw Sign:
        Matrix.setIdentityM(modelMatrix, 0);
//    Matrix.rotateM(modelBeam, 0, 45, 0, 1, 0);
        Matrix.rotateM(modelMatrix, 0, 90 * i, 0, 1, 0);
        Matrix.translateM(modelMatrix, 0, 0.1f, -0.05f, -3.5f);
        Matrix.scaleM(modelMatrix, 0, .75f, .75f, .75f);
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0,
                modelViewMatrix, 0);
//      Log.v(TAG, "Trans" +trans + " frameNo " + frameNo + " signFadeFrame " + signFadeFrame);
        drawRect(signTexture, trans);
      }
    }

    if (beamFiring) {
      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelBeam, 0);
      Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelViewMatrix, 0);
      drawBeam();
    }

    GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    if (frameNo-flareStartFrame > 0 && frameNo-flareStartFrame < 51) {
      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelFlare, 0);
      Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelViewMatrix, 0);
      drawFlare();
    }

      //Draw the Reticle (this must be done last due to transparency)
    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelReticle, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelViewMatrix, 0);
    drawRect(reticleTexture, 1);
    GLES20.glEnable(GLES20.GL_DEPTH_TEST);
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

    GLES20.glEnableVertexAttribArray(cubeNormalParam);
    GLES20.glEnableVertexAttribArray(cubeColorParam);
    GLES20.glEnableVertexAttribArray(cubePositionParam);

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
    GLES20.glUniform1f(flareRadiusParam, ((float) (frameNo - flareStartFrame)) /50f);

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
      float[] projectileVelocityVS = new float[]{0, 4, -8, 1};
      Matrix.multiplyMV(projectileVelocity, 0, invHeadView, 0, projectileVelocityVS, 0);

      Log.i(TAG, "projectileVelocity Vect: " + projectileVelocity[0] + " " + projectileVelocity[1] + " " + projectileVelocity[2]);
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
    cubePos = new float[3];
    cubePos[0] = (float)Math.random() * 7.0f - 3.5f;
    cubePos[1] = (float)Math.random() * 7.0f - 3.5f;
    cubePos[2] = -((float)Math.random() * 3.0f + 0.5f);

    cubePos[0] = (float)Math.random() * 1.0f - 0.5f;
    cubePos[1] = 0f;
    cubePos[2] = -(2f);

    for (int i=0; i<3; i++) {
      if (mode > 2)
        cubeVel[i] = (float) Math.random() * 2.0f - 1.0f;
      else
        cubeVel[i] = 0;
      if (mode == 4)
        cubeAccel[i] = (float) Math.random() * 0.4f - 0.2f;
      else
        cubeAccel[i] = 0;
    }
    Log.i(TAG, "cubePos:  X: " + cubePos[0] + "  Y: " + cubePos[1] + "  Z: " + cubePos[2]);
    Log.i(TAG, "cubeVel:  X: " + cubeVel[0] + "  Y: " + cubeVel[1] + "  Z: " + cubeVel[2]);

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

      checkGLError("UpdateTextTexture");
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
      checkGLError("UpdateTextTexture1");
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
      int textSize = 32;
      reticleBitmap = Bitmap.createBitmap(textSize, textSize, Bitmap.Config.ARGB_4444);

      reticleBitmap.eraseColor(Color.TRANSPARENT); //White

      // get a canvas to paint over the textBitmap
      Canvas canvas = new Canvas(reticleBitmap);
      Paint paint=new Paint();
      paint.setAntiAlias(true);
      paint.setStrokeWidth(2f/(float)textSize);
      paint.setColor(Color.DKGRAY);

      canvas.translate(textSize/2, textSize/2);
      canvas.scale((float)textSize/2f,(float)textSize/2f);
      float rectFact = 7f/8f;
      canvas.drawLine(-rectFact, -rectFact, rectFact, rectFact, paint);
      canvas.drawLine(rectFact, -rectFact, -rectFact, rectFact, paint);

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


  private static class VideoEncoder {

    // encoder / muxer state
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private int mTrackIndex;
    private boolean mMuxerStarted;
    private CodecInputSurface mInputSurface;
    private int mWidth = -1;
    private int mHeight = -1;
    private int mBitRate = -1;
    // parameters for the encoder
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30;               // 15fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames

    private static final File OUTPUT_DIR = Environment.getExternalStorageDirectory();

    // allocate one of these up front so we don't need to do it every time
    private MediaCodec.BufferInfo mBufferInfo;

    private void prepare(int width, int height, int bitRate, EGLContext shareContext) {
      mWidth=width;
      mHeight=height;
      mBitRate=bitRate;
      mBufferInfo = new MediaCodec.BufferInfo();
      EGLContext recordContext;

      MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);

      // Set some properties.  Failing to specify some of these can cause the MediaCodec
      // configure() call to throw an unhelpful exception.
      format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
              MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
      format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
      format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
      format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
//      if (VERBOSE) Log.d(TAG, "format: " + format);

      // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
      // we can use for input and wrap it with a class that handles the EGL work.
      //
      // If you want to have two EGL contexts -- one for display, one for recording --
      // you will likely want to defer instantiation of CodecInputSurface until after the
      // "display" EGL context is created, then modify the eglCreateContext call to
      // take eglGetCurrentContext() as the share_context argument.
      try {
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
      } catch (IOException e) {
        throw new RuntimeException("MediaCodec createEncoderByType failed", e);
      }

//            Log.i(TAG, "onSurfaceCreated context:" + shareContext);

      mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      mInputSurface = new CodecInputSurface(mEncoder.createInputSurface(), shareContext);
      mEncoder.start();

      recordContext = mInputSurface.mEGLContext;
      Log.i(TAG, "recordContext created:" + recordContext);

      // Output filename.  Ideally this would use Context.getFilesDir() rather than a
      // hard-coded output directory.
      String outputPath = new File(OUTPUT_DIR,
              "test." + mWidth + "x" + mHeight + ".mp4").toString();
      Log.d(TAG, "output file is " + outputPath);


      // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
      // because our MediaFormat doesn't have the Magic Goodies.  These can only be
      // obtained from the encoder after it has started processing data.
      //
      // We're not actually interested in multiplexing audio.  We just want to convert
      // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
      try {
        mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      } catch (IOException ioe) {
        throw new RuntimeException("MediaMuxer creation failed", ioe);
      }

      mTrackIndex = -1;
      mMuxerStarted = false;
    }

    /**
     * Extracts all pending data from the encoder.
     * <p>
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     */
    private void drain(boolean endOfStream) {
      final int TIMEOUT_USEC = 10000;
//      if (VERBOSE) Log.d(TAG, "drainEncoder(" + endOfStream + ")");

      if (endOfStream) {
//        if (VERBOSE) Log.d(TAG, "sending EOS to encoder");
        mEncoder.signalEndOfInputStream();
      }

      ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
      while (true) {
        int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
          // no output available yet
          if (!endOfStream) {
            break;      // out of while
          } else {
//            if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS");
          }
        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
          // not expected for an encoder
          encoderOutputBuffers = mEncoder.getOutputBuffers();
        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          // should happen before receiving buffers, and should only happen once
          if (mMuxerStarted) {
            throw new RuntimeException("format changed twice");
          }
          MediaFormat newFormat = mEncoder.getOutputFormat();
          Log.d(TAG, "encoder output format changed: " + newFormat);

          // now that we have the Magic Goodies, start the muxer
          mTrackIndex = mMuxer.addTrack(newFormat);
          mMuxer.start();
          mMuxerStarted = true;
        } else if (encoderStatus < 0) {
          Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                  encoderStatus);
          // let's ignore it
        } else {
          ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
          if (encodedData == null) {
            throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                    " was null");
          }

          if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
//            if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
            mBufferInfo.size = 0;
          }

          if (mBufferInfo.size != 0) {
            if (!mMuxerStarted) {
              throw new RuntimeException("muxer hasn't started");
            }

            // adjust the ByteBuffer values to match BufferInfo (not needed?)
            encodedData.position(mBufferInfo.offset);
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

            mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
//            if (VERBOSE) Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer");
          }

          mEncoder.releaseOutputBuffer(encoderStatus, false);

          if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            if (!endOfStream) {
              Log.w(TAG, "reached end of stream unexpectedly");
            } else {
//              if (VERBOSE) Log.d(TAG, "end of stream reached");
            }
            break;      // out of while
          }
        }
      }
    }

    /**
     * Releases encoder resources.  May be called after partial / failed initialization.
     */
    private void release() {
//      if (VERBOSE) Log.d(TAG, "releasing encoder objects");
      if (mEncoder != null) {
        mEncoder.stop();
        mEncoder.release();
        mEncoder = null;
      }
      if (mInputSurface != null) {
        mInputSurface.release();
        mInputSurface = null;
      }
      if (mMuxer != null) {
        mMuxer.stop();
        mMuxer.release();
        mMuxer = null;
      }
    }

    public void setCodecInputSurface (CodecInputSurface inputSurface) {
      mInputSurface=inputSurface;
    }

    public int width() {
      return mWidth;
    }

    public int height() {
      return mHeight;
    }
    public CodecInputSurface inputSurface() {
      return mInputSurface;
    }
  }
  /**
   * Holds state associated with a Surface used for MediaCodec encoder input.
   * <p>
   * The constructor takes a Surface obtained from MediaCodec.createInputSurface(), and uses that
   * to create an EGL window surface.  Calls to eglSwapBuffers() cause a frame of data to be sent
   * to the video encoder.
   * <p>
   * This object owns the Surface -- releasing this will release the Surface too.
   */
  private static class CodecInputSurface {
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;

    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    public EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;

    private Surface mSurface;

    /**
     * Creates a CodecInputSurface from a Surface.
     */
    public CodecInputSurface(Surface surface, EGLContext displayContext) {
      if (surface == null) {
        throw new NullPointerException();
      }
      mSurface = surface;

      eglSetup(displayContext);
    }

    /**
     * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
     */
    private void eglSetup(EGLContext displayContext) {
      mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
      if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
        throw new RuntimeException("unable to get EGL14 display");
      }
      int[] version = new int[2];
      if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
        throw new RuntimeException("unable to initialize EGL14");
      }

      // Configure EGL for recording and OpenGL ES 2.0.
      int[] attribList = {
              EGL14.EGL_RED_SIZE, 8,
              EGL14.EGL_GREEN_SIZE, 8,
              EGL14.EGL_BLUE_SIZE, 8,
              EGL14.EGL_ALPHA_SIZE, 8,
              EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
              EGL_RECORDABLE_ANDROID, 1,
              EGL14.EGL_DEPTH_SIZE, 8,
              EGL14.EGL_NONE
      };
      android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
      int[] numConfigs = new int[1];
      EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
              numConfigs, 0);
      checkEglError("eglCreateContext RGB888+recordable ES2");

      // Configure context for OpenGL ES 2.0.
      int[] attrib_list = {
              EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
              EGL14.EGL_NONE
      };
      mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], displayContext,
              attrib_list, 0);
      checkEglError("eglCreateContext");

      // Create a window surface, and attach it to the Surface we received.
      int[] surfaceAttribs = {
              EGL14.EGL_NONE
      };
      mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], mSurface,
              surfaceAttribs, 0);
      checkEglError("eglCreateWindowSurface");
    }

    /**
     * Discards all resources held by this class, notably the EGL context.  Also releases the
     * Surface that was passed to our constructor.
     */
    public void release() {
      if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
        EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
        EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
        EGL14.eglReleaseThread();
        EGL14.eglTerminate(mEGLDisplay);
      }

      mSurface.release();

      mEGLDisplay = EGL14.EGL_NO_DISPLAY;
      mEGLContext = EGL14.EGL_NO_CONTEXT;
      mEGLSurface = EGL14.EGL_NO_SURFACE;

      mSurface = null;
    }

    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent() {
      EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);
      checkEglError("eglMakeCurrent");
    }

    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     */
    public boolean swapBuffers() {
      boolean result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
      checkEglError("eglSwapBuffers");
      return result;
    }

    /**
     * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
     */
    public void setPresentationTime(long nsecs) {
      EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
      checkEglError("eglPresentationTimeANDROID");
    }

    /**
     * Checks for EGL errors.  Throws an exception if one is found.
     */
    private void checkEglError(String msg) {
      int error;
      if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
        throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
      }
    }
  }

}