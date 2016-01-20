precision mediump float;
uniform float u_Radius;
varying vec2 v_TexCoord;

void main() {
    float trans=0.0;
    vec2 coord=(v_TexCoord-0.5)*2.0;
    float thisRad = sqrt(pow(coord.x,2.0)+pow(coord.y,2.0));
        float raddiff = abs(thisRad-u_Radius);
    if ( raddiff < 0.2)
        trans=(1.0-u_Radius)*(1.0-raddiff*5.0);
    gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0*trans);
//    gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);
}
