precision mediump float;
uniform sampler2D s_texture;
uniform float u_Trans;
uniform int u_reverse;
varying vec2 v_TexCoord;

#define M_PI 3.1415926535897932384626433832795
void main() {
    //float phy = v_TexCoord.y * M_PI / 2.0 - M_PI / 4.0;
    float y=v_TexCoord.y;
    if (u_reverse==1) y = 1.0-y;
    float phy = asin(y) - M_PI / 4.0;
    float perspective_y = (tan(phy) * 0.5 + 0.5);
    if (u_reverse==1) perspective_y = 1.0-perspective_y;
    vec4 Colour = texture2D(s_texture, vec2(v_TexCoord.x, perspective_y));
    gl_FragColor = vec4(Colour.rgb, Colour.a*u_Trans);
}