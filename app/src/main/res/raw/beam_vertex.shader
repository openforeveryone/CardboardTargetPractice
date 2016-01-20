uniform mat4 u_MVP;

attribute vec4 a_Position;
attribute float a_TXCoord;

varying vec4 v_Color;
varying float v_TXCoord;

void main() {
   v_TXCoord = a_TXCoord;
   v_Color = vec4(1.0, 0.0, 0.0, 1.0);
   gl_Position = u_MVP * a_Position;
}
