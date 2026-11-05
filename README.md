# FrameGen 1.8.9 — protótipo de validação (Fase 0)

> **STATUS HONESTO: código escrito, NÃO compilado e NÃO testado.**
> O ambiente onde foi escrito não tinha JDK 8, Forge 1.8.9, MCP, LWJGL 2 nem GPU/Minecraft.
> Nenhum item de teste (etapas A–G) foi executado. Não há JAR. Tudo abaixo é design + código a validar.

## O que está implementado (no código)
- Captura do mundo **antes do HUD** (`RenderGameOverlayEvent.Pre/ALL`) via `glCopyTexSubImage2D` do FBO do Minecraft, sem readback CPU.
- Redução para luma em resolução de análise (shader `capture.fsh`), texturas/FBOs reutilizados, realocação em mudança de resolução / perda de contexto (`glIsTexture`).
- **Block matching real** em GLSL 1.20 (`motion_estimation.fsh`): busca grossa + refino ±1, SAD sobre luma, penalidade por |d|.
- **Interpolação compensada em movimento** (`interpolation.fsh`) com fallback de oclusão (usa o frame temporalmente mais próximo). *Não* é optical flow denso; é um campo de vetores por bloco.
- 2x/3x/4x: gera N-1 frames e apresenta cada um com `Display.update(false)` (swap real).
- HUD: re-renderiza `ingameGUI.renderGameOverlay` sobre cada frame gerado (sem interpolar texto).
- Métricas medidas (Real/Generated/Display FPS, stutters), config persistente, menu (F7), toggle (F8), ciclo de modo (F6), detecção de GL, fallback após 3 falhas, corte de cena (teleporte, dimensão, respawn, giro >60°).

## Limitações de arquitetura (importantes)
1. **Render síncrono ⇒ ganho limitado e cadência irregular.** O loop do Minecraft renderiza o frame N inteiro antes de qualquer apresentação. Apresentar N-1→N intermediários dentro do evento *não* sobrepõe render e display. Com gap = R/m, o ganho teórico é m/(2−1/m): ~1,33x (2x), ~1,8x (3x), ~2,3x (4x), e o ritmo é em rajadas. Ganho pleno exige desacoplar o loop (renderizar o mundo só a cada m-ésima iteração, p.ex. via `skipRenderWorld`/ASM + extrapolação, como o FrameGen 1.21 de referência faz com reprojeção por profundidade). **Isso é a Fase 1 e precisa ser validado em hardware real.**
2. **+1 frame de latência** (interpolação precisa do frame futuro).
3. Premissas do Forge 1.8.9 a verificar em runtime: `RenderTickEvent.END` dispara com o FBO do MC ainda vinculado; `Pre(ALL)` dispara todo frame (com `hideGUI` não dispara ⇒ geração pausa); re-chamar `renderGameOverlay` não causa efeitos colaterais em mods de HUD.
4. Entidades/partículas sem vetores próprios; só movimento aparente em tela.
5. OptiFine (shaders/Fast Render), mods que trocam o FBO: não suportados/não testados; só detecção heurística.
6. Exige GL 3.0 (FBO core no LWJGL2). A GT 610 (Fermi) tem GL 4.x via driver, mas **não foi medida**.

## Estrutura vs. sugestão do prompt
`BlockMatching`, `MotionEstimator`, `FrameInterpolator`, `FrameCapture`, `FrameBufferManager`, `FrameRenderer`, `DisplayManager` foram **fundidos** em `core/FrameGenEngine` + shaders (as classes separadas seriam vazias). `final_composition.fsh` não existe: a interpolação grava direto no backbuffer. `OpticalFlow.java` não existe — não há optical flow implementado.

## Build no GitHub
Suba o conteúdo do zip para a raiz de um repositório; o workflow `.github/workflows/build.yml` compila (JDK 8 + Gradle 2.14.1) e publica o JAR em Actions → Artifacts. Se falhar, copie o log do passo "Build".

## Build local
JDK 8, ForgeGradle 2.1 + Gradle 2.14 (wrapper não incluído: gere com um template 1.8.9 atual). `jcenter()` foi desativado; o `build.gradle` usa mavenCentral + maven.minecraftforge.net e pode precisar de ajuste.
```
gradlew setupDecompWorkspace
gradlew build      # JAR em build/libs → .minecraft/mods/
```
Possíveis erros de compilação: nomes MCP (`renderGameOverlay`, `setupOverlayRendering`, `getFramebuffer`, `bus()`); me envie o log que eu corrijo.

## Uso
F8 liga/desliga (padrão: desligado) · F7 menu · F6 2x/3x/4x. Config em `config/framegen.cfg`.

## Plano de testes (a executar por você)
A: inicia e loga `GL … usable=true` · B: ligar e ver se não há tela preta · C: 2x, overlay Generated FPS > 0 · D: 3x/4x · E: HUD, giro rápido, teleporte, multiplayer · F: medir FPS/latência/VRAM · G: outros mods. Anote resultados e envie.
