uniform mat4 u_Model;
uniform mat4 u_MVP;
uniform mat4 u_MVMatrix;

attribute vec4 a_Position;
attribute vec2 a_Coord;

varying vec2 v_TexCoord;

void main() {
   v_TexCoord = a_Coord;

   gl_Position = u_MVP * a_Position;
}
