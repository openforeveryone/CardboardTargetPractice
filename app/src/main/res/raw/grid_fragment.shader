precision mediump float;
varying vec2 v_TexCoord;

void main() {
    float depth = gl_FragCoord.z / gl_FragCoord.w; // Calculate world-space distance.

    if ((mod(abs(v_TexCoord.x), 1.0/3.0) < 0.02) || (mod(abs(v_TexCoord.y), 1.0/3.0) < 0.02)) {
        gl_FragColor = max(0.0, (90.0-depth) / 90.0) * vec4(1.0, 1.0, 0.0, 1.0)
                + min(1.0, depth / 90.0) * vec4(0.0, 0.0, 0.0, 1.0);
    } else {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
    }
}
