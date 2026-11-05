#version 120
// Block matching em 2 niveis (grade grossa + refino +-1) sobre luma.
// Saida: RG = vetor d codificado, B = erro SAD medio (x4), A = 1.
// Convencao: bloco em p no frame ATUAL casa com p+d no frame ANTERIOR.
uniform sampler2D uCurLow;
uniform sampler2D uPrevLow;
uniform vec2  uInvLow;
uniform float uBlock;    // tamanho do bloco em px de analise
uniform float uRadius;   // raio da busca grossa (0..4)
uniform float uStep;     // passo da busca grossa (px)
uniform float uReach;    // alcance maximo = radius*step+1 (codificacao)
uniform float uLambda;   // penalidade por |d| (reduz ruido)

float cur[16];

float sad(vec2 d) {
    float s = 0.0;
    for (int i = 0; i < 16; i++) {
        float gx = mod(float(i), 4.0);
        float gy = floor(float(i) / 4.0);
        vec2 sp = (floor(gl_FragCoord.xy) * uBlock + (vec2(gx, gy) + 0.5) * (uBlock * 0.25));
        s += abs(cur[i] - texture2D(uPrevLow, (sp + d) * uInvLow).r);
    }
    return s * (1.0 / 16.0);
}

void main() {
    for (int i = 0; i < 16; i++) {
        float gx = mod(float(i), 4.0);
        float gy = floor(float(i) / 4.0);
        vec2 sp = (floor(gl_FragCoord.xy) * uBlock + (vec2(gx, gy) + 0.5) * (uBlock * 0.25));
        cur[i] = texture2D(uCurLow, sp * uInvLow).r;
    }
    float best = sad(vec2(0.0));
    vec2 bd = vec2(0.0);
    for (int a = -4; a <= 4; a++) {
        for (int b = -4; b <= 4; b++) {
            if (abs(float(a)) > uRadius || abs(float(b)) > uRadius) continue;
            if (a == 0 && b == 0) continue;
            vec2 d = vec2(float(a), float(b)) * uStep;
            float e = sad(d) + uLambda * length(d);
            if (e < best) { best = e; bd = d; }
        }
    }
    vec2 c0 = bd;
    for (int a = -1; a <= 1; a++) {
        for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) continue;
            vec2 d = c0 + vec2(float(a), float(b));
            float e = sad(d) + uLambda * length(d);
            if (e < best) { best = e; bd = d; }
        }
    }
    float err = sad(bd);
    gl_FragColor = vec4(clamp(bd / uReach, -1.0, 1.0) * 0.5 + 0.5, clamp(err * 4.0, 0.0, 1.0), 1.0);
}
