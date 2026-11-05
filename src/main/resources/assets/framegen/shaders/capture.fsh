#version 120
// Reduz o frame colorido para luma em resolucao de analise (4 taps bilineares).
uniform sampler2D uColor;
uniform vec2 uInvLow;   // 1 / tamanho da textura de analise
varying vec2 vUv;
void main() {
    vec2 o = uInvLow * 0.25;
    vec3 c = texture2D(uColor, vUv + vec2(-o.x, -o.y)).rgb
           + texture2D(uColor, vUv + vec2( o.x, -o.y)).rgb
           + texture2D(uColor, vUv + vec2(-o.x,  o.y)).rgb
           + texture2D(uColor, vUv + vec2( o.x,  o.y)).rgb;
    float l = dot(c * 0.25, vec3(0.299, 0.587, 0.114));
    gl_FragColor = vec4(l, l, l, 1.0);
}
