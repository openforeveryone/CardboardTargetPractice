precision mediump float;
uniform sampler2D s_texture;
uniform float u_Trans;
varying vec2 v_TexCoord;

#define M_PI 3.1415926535897932384626433832795

void main() {
    float phy = v_TexCoord.y * M_PI / 2.0 - M_PI / 4.0;
    float perspective_y = (tan(phy) * 0.5 + 0.5);
    gl_FragColor = texture2D(s_texture, vec2(v_TexCoord.x, perspective_y));
}
