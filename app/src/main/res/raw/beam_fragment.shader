precision mediump float;
varying vec4 v_Color;
varying float v_TXCoord;
uniform float u_maxDepth;

void main() {
    float depth = gl_FragCoord.z / gl_FragCoord.w; // Calculate world-space distance.
    if (depth>u_maxDepth)
        discard;
    if (depth<u_maxDepth-10.0)
        discard;
    if (v_TXCoord < -.25)
        gl_FragColor = vec4(v_Color.r, 0.0, 0.0, (1.0-(-v_TXCoord-.25)*4.0/3.0)*.75);
    else if (v_TXCoord > .25)
        gl_FragColor = vec4(v_Color.r, 0.0, 0.0, (1.0-(v_TXCoord-.25)*4.0/3.0)*.75);
    else
        gl_FragColor = v_Color;
}
