precision mediump float;
uniform sampler2D s_texture;
uniform float u_Trans;
varying vec2 v_TexCoord;

void main() {
    vec4 Colour = texture2D(s_texture, v_TexCoord);
    if (Colour.a<0.01)
        discard;
    gl_FragColor = vec4(Colour.rgb, Colour.a*u_Trans);
//    gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0*u_trans);
}
