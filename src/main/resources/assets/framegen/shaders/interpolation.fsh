#version 120
// Interpolacao compensada em movimento com tratamento simples de oclusao.
// out(q,t) = mix(prev(q + d*t), cur(q - d*(1-t)), w)
uniform sampler2D uPrev;
uniform sampler2D uCur;
uniform sampler2D uMotion;
uniform vec2  uInvLow;
uniform float uReach;
uniform float uT;        // 0 = anterior, 1 = atual
varying vec2 vUv;
void main() {
    vec4 m = texture2D(uMotion, vUv);
    vec2 d = (m.rg - 0.5) * 2.0 * uReach * uInvLow;
    float err = m.b;
    d *= 1.0 - smoothstep(0.10, 0.35, err); // baixa confianca => vetor encolhe para 0
    vec3 a = texture2D(uPrev, vUv + d * uT).rgb;
    vec3 b = texture2D(uCur,  vUv - d * (1.0 - uT)).rgb;
    float diff = length(a - b);
    // erro de bloco alto ou amostras discordantes => oclusao/sem correspondencia:
    // usa o frame temporalmente mais proximo em vez de misturar (evita ghosting).
    float occl = smoothstep(0.06, 0.20, diff + err * 0.5);
    float w = mix(uT, step(0.5, uT), occl);
    gl_FragColor = vec4(mix(a, b, w), 1.0);
}
