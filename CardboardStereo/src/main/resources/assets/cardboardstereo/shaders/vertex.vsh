#version 120

// Deliberately uses the fixed-function built-ins (gl_Vertex, gl_MultiTexCoord0,
// ftransform()) instead of custom `attribute` locations, so this shader works
// fine when the composite quad is submitted with plain immediate-mode
// glBegin/glVertex2f/glTexCoord2f calls (see StereoRenderer#drawEyeQuad).

varying vec2 v_texcoord;

void main() {
    gl_Position = ftransform();
    v_texcoord = gl_MultiTexCoord0.xy;
}
