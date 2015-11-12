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
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
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

  private int cubeProgram;
  private int floorProgram;
  private int beamProgram;
  private int txProgram;

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

  private float[] modelCube;
  private float[] camera;
  private float[] view;
  private float[] headView;
  private float[] modelViewProjection;
  private float[] modelView;
  private float[] modelFloor;
  private float[] modelProjectile;
  private float[] modelBeam;

  private float[] projectilePos = {1,0,0,1};
  private float[] projectileVelocity = {1,1,0,0};
  private float[] cubePos = {0,0,0,0};


  private float[] forwardVector = {0,0,0};
  
  private int score = 0;
  private int projectiles = 10;
  private int rays = 10;
  private float objectDistance = 3.5f;
  private float floorDepth = 1.5f;

  private boolean out = true;

  private Vibrator vibrator;
  private CardboardOverlayView overlayView;

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
   * Sets the view to our CardboardView and initializes the transformation matrices we will use
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
    view = new float[16];
    modelViewProjection = new float[16];
    modelView = new float[16];
    modelFloor = new float[16];
    headView = new float[16];
    modelProjectile = new float[16];
    modelBeam = new float[16];
    vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);


    overlayView = (CardboardOverlayView) findViewById(R.id.overlay);
    overlayView.show3DToast("Pull the magnet when you find an object.", 5000);

    Log.i(TAG, "onCreate");
    if (Looper.myLooper() == Looper.getMainLooper())
      Log.i(TAG, "In UI thread");
    else
      Log.i(TAG, "Not in UI thread");

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

    ByteBuffer bbrVertices = ByteBuffer.allocateDirect(WorldLayoutData.RECT_COORDS.length * 4);
    bbrVertices.order(ByteOrder.nativeOrder());
    rectVertices = bbrVertices.asFloatBuffer();
    rectVertices.put(WorldLayoutData.RECT_COORDS);
    rectVertices.position(0);

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

    int beamvertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.beam_vertex);
    int gridvertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.grid_vertex);
    int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
    int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);
    int passthroughShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);
    int textureFragShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.texture_fragment);

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
    GLES20.glAttachShader(beamProgram, beamvertexShader);
    GLES20.glAttachShader(beamProgram, passthroughShader);
    GLES20.glLinkProgram(beamProgram);
    GLES20.glUseProgram(beamProgram);
    checkGLError("Beam program");

    beamModelViewProjectionParam = GLES20.glGetUniformLocation(beamProgram, "u_MVP");
    beamPositionParam = GLES20.glGetAttribLocation(beamProgram, "a_Position");
    //beamCoordParam = GLES20.glGetAttribLocation(floorProgram, "a_Coord");
    GLES20.glEnableVertexAttribArray(beamPositionParam);
    checkGLError("Beam program params");


    txProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(txProgram, vertexShader);
    GLES20.glAttachShader(txProgram, textureFragShader);
    GLES20.glLinkProgram(txProgram);
    GLES20.glUseProgram(txProgram);
    checkGLError("Beam program");

    beamModelViewProjectionParam = GLES20.glGetUniformLocation(txProgram, "u_MVP");
    beamPositionParam = GLES20.glGetAttribLocation(txProgram, "a_Position");
    //beamCoordParam = GLES20.glGetAttribLocation(floorProgram, "a_Coord");
    GLES20.glEnableVertexAttribArray(beamPositionParam);
    checkGLError("Beam program params");

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

  /**
   * Prepares OpenGL ES before we draw a frame.
   *
   * @param headTransform The head transformation in the new frame.
   */
  @Override
  public void onNewFrame(HeadTransform headTransform) {
    // Build the Model part of the ModelView matrix.
    Matrix.rotateM(modelCube, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);

    // Build the camera matrix and apply it to the ModelView.
    Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

    headTransform.getHeadView(headView, 0);
    headTransform.getForwardVector(forwardVector, 0);

    for(int i=0; i<3; i++)
      projectilePos[i]+=projectileVelocity[i]/60.0f;
    projectileVelocity[1]-=9.81/60.0f;

    boolean hit = true;
    for(int i=0; i<3; i++)
      if (Math.abs(projectilePos[i]-cubePos[i])>0.2f) hit = false;
    if (hit) {
      score+=2;
      Log.i(TAG, "Object Hit. Score: " + score);
      if (projectiles>0)
        show3DToast("You hit it.\nScore = " + score + "\n" + projectiles + " left", 4000);
      else
        show3DToast("You hit it.\nScore = " + score + "\n Now fire streight at it.", 6000);
      hideObject();
      //Setting out here prevents loosing point when this poj hits a wall.
      out=true;
    }
    if (!out) {
      if (Math.abs(projectilePos[0]) > 4.0f) out = true;
      if (projectilePos[1] < -1.5f) out = true;
      if (Math.abs(projectilePos[2]) > 4.0f) out = true;
      if (out) {
        score--;
        if (projectiles>0)
          show3DToast("You missed it.\nScore = " + score + "\n" + projectiles + " left", 4000);
        else
          show3DToast("You missed it.\nScore = " + score + "\n Now fire streight at it.", 6000);
        Log.i(TAG, "Object Missed. Score: " + score);
      }
    }

    if (textimagelock.tryLock()) {
      if (textRenderFinished) {
        UpdateTextTexture();
        textRenderFinished=false;
      }
      textimagelock.unlock();
    }
    checkGLError("onReadyToDraw");
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
    Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);

    // Set the position of the light
    Matrix.multiplyMV(lightPosInEyeSpace, 0, view, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

    // Build the ModelView and ModelViewProjection matrices
    // for calculating cube position and light.
    float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);
    Matrix.multiplyMM(modelView, 0, view, 0, modelCube, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
    drawCube();

    Matrix.setIdentityM(modelProjectile, 0);
    Matrix.translateM(modelProjectile, 0, projectilePos[0], projectilePos[1], projectilePos[2]);
    Matrix.multiplyMM(modelView, 0, view, 0, modelProjectile, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
    drawProjectile();

//     Set modelView for the floor, so we draw floor in the correct location
    Matrix.multiplyMM(modelView, 0, view, 0, modelFloor, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0,
            modelView, 0);
    drawFloor();

    Matrix.setIdentityM(modelBeam, 0);
//    Matrix.rotateM(modelBeam, 0, 45, 0, 1, 0);
    float invHeadView[] = new float[16];
    Matrix.invertM(invHeadView, 0, headView, 0);
    Matrix.multiplyMM(modelBeam, 0, invHeadView, 0, modelBeam, 0);
    Matrix.multiplyMM(modelView, 0, view, 0, modelBeam, 0);
    Matrix.multiplyMM(modelViewProjection, 0, perspective, 0,
            modelView, 0);
    drawBeam();
//    drawRect();
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
    GLES20.glUniformMatrix4fv(cubeModelViewParam, 1, false, modelView, 0);

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
    GLES20.glUniformMatrix4fv(cubeModelViewParam, 1, false, modelView, 0);

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
    GLES20.glUseProgram(txProgram);

    // Set the position of the beam
    GLES20.glVertexAttribPointer(beamPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
            false, 0, beamVertices);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(beamModelViewProjectionParam, 1, false, modelViewProjection, 0);

    GLES20.glLineWidth(2);
    GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);
    checkGLError("Drawing Beam");
  }

  public void drawRect() {
    GLES20.glUseProgram(beamProgram);

    // Set the position of the beam
    GLES20.glVertexAttribPointer(beamPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
            false, 0, beamVertices);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(beamModelViewProjectionParam, 1, false, modelViewProjection, 0);

    GLES20.glLineWidth(2);
    GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);
    checkGLError("Drawing REct");
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
    GLES20.glUniformMatrix4fv(floorModelViewParam, 1, false, modelView, 0);
    GLES20.glUniformMatrix4fv(floorModelViewProjectionParam, 1, false,
        modelViewProjection, 0);
    GLES20.glVertexAttribPointer(floorPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
        false, 0, floorVertices);
    GLES20.glVertexAttribPointer(floorCoordParam, 3, GLES20.GL_FLOAT, false, 0,
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
    if (out && projectiles > 0) {
      Log.i(TAG, "Throwing");
      projectilePos = new float[]{0, -.75f, 0, 1};
      projectileVelocity = new float[]{-forwardVector[0] * 5, (1 - forwardVector[1]) * 5, forwardVector[2] * 5, 0};
      Log.i(TAG, "Forward Vect: " + forwardVector[0] + " " + forwardVector[1] + " " + forwardVector[2]);
      out = false;
      projectiles--;
    }
    if (out && projectiles == 0 && rays > 0)
    {
      Log.i(TAG, "Firing");
      rays--;
      if (isLookingAtObject()) {
        score+=2;
        if (rays>0)
          show3DToast("You hit it.\nScore = " + score + "\n" + rays + " left", 4000);
        else
          show3DToast("You hit it.\nScore = " + score, 6000);
        hideObject();
      } else {
        if (rays>0)
          show3DToast("You missed it.\nScore = " + score + "\n" + rays + " left", 4000);
        else
          show3DToast("You missed it.\nScore = " + score, 6000);
      }
    }
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
    Matrix.setIdentityM(modelCube, 0);
    Matrix.translateM(modelCube, 0, posVec[0], newY, posVec[2]);
    cubePos = new float[] {posVec[0], newY, posVec[2]};
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
    Matrix.multiplyMM(modelView, 0, headView, 0, modelCube, 0);
    Matrix.multiplyMV(objPositionVec, 0, modelView, 0, initVec, 0);

    float pitch = (float) Math.atan2(objPositionVec[1], -objPositionVec[2]);
    float yaw = (float) Math.atan2(objPositionVec[0], -objPositionVec[2]);

    return Math.abs(pitch) < PITCH_LIMIT && Math.abs(yaw) < YAW_LIMIT;
  }

  //Text Rendering:
  TextViewUpdater textViewUpdater = new TextViewUpdater();
  Handler textUpdaterHandler = new Handler(Looper.getMainLooper());
  private final ReentrantLock textimagelock = new ReentrantLock();

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
      overlayView.show3DToast(txt, time);

      textimagelock.lock();
      // Create an empty, mutable textBitmap
      textBitmap = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_4444);
      textBitmap.eraseColor(Color.WHITE);
      // get a canvas to paint over the textBitmap
      Canvas canvas = new Canvas(textBitmap);
      textBitmap.eraseColor(0);

      // get a background image from resources
// note the image format must match the textBitmap format
//    Drawable background = overlayView.getResources().getDrawable(R.drawable.background);
//    background.setBounds(0, 0, 256, 256);
//    background.draw(canvas); // draw the background to our textBitmap
      // Draw the text
      Paint textPaint = new Paint();
      textPaint.setTextSize(32);
      textPaint.setAntiAlias(true);
      textPaint.setARGB(0xff, 0x00, 0x00, 0x00);
// draw the text centered
      canvas.drawText("Hello World", 16, 112, textPaint);
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

    public void UpdateTextTexture() {
      Log.i(TAG, "TextViewUpdaterFinished");
      if (Looper.myLooper() == Looper.getMainLooper())
        Log.e(TAG, "In UI thread");

      int[] textures = new int[1];
//Generate one texture pointer...
      GLES20.glGenTextures(1, textures, 0);
//...and bind it to our array
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

//Create Nearest Filtered Texture
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

//Different possible texture parameters, e.g. GL10.GL_CLAMP_TO_EDGE
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
      GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

//Use the Android GLUtils to specify a two-dimensional texture image from our textBitmap
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textBitmap, 0);
      checkGLError("UpdateTextTextureFinished");
      Log.i(TAG, "Texture created " + textures[0]);
    }

  private void show3DToast(String message) {
    show3DToast(message, 5000);
  }

  private void show3DToast(String message, int time) {
    textViewUpdater.setText(message);
    textViewUpdater.setTime(time);
    textUpdaterHandler.post(textViewUpdater);
  }

}