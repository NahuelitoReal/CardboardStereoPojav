#version 120

// Barrel distortion applied per-eye to pre-compensate for the pincushion
// distortion introduced by Google Cardboard's convex lenses.
//
// r' = r * (1 + strength * r^2)
//
// Sampling the SOURCE image at the distorted coordinate produces a
// pre-corrected (barrel-warped) image, which the convex lens then
// optically un-warps back to a rectilinear image for the eye.

uniform sampler2D u_texture;
uniform float u_strength;

varying vec2 v_texcoord;

void main() {
    vec2 center = vec2(0.5, 0.5);
    vec2 offset = v_texcoord - center;

    float r2 = dot(offset, offset);
    float distortionFactor = 1.0 + u_strength * r2;

    vec2 distortedCoord = offset * distortionFactor + center;

    if (distortedCoord.x < 0.0 || distortedCoord.x > 1.0 ||
        distortedCoord.y < 0.0 || distortedCoord.y > 1.0) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
    } else {
        gl_FragColor = texture2D(u_texture, distortedCoord);
    }
}
