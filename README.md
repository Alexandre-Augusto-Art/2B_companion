# 2B Companion

Companheira de IA do [0FF-GR1D](https://alexandre-augusto-art.github.io/0FF-GR1D_WEB/). Conversa, checa links e boatos,
orienta sobre segurança digital e manifestações. A IA roda no aparelho.

**Estado: 0.4, em desenvolvimento.** O Android compila, mas ainda não foi testado em celular. No Windows, a versão de teste abre e conversa. A IA foi testada no Windows com
Qwen3 0.6B e Gemma 4 E2B (texto e imagem).

## O que faz

- Tem rosto e história: pisca, mexe a boca ao responder e fica vermelha em alerta. Veja [a personagem](docs/PERSONAGEM.md).
- Conversa no dia a dia, com personalidade de ativista hacker, por cima de outros apps (bolha) ou como assistente do Android.
- Traz a notícia do W4K3 PR0T0C0L com o vídeo do dia, a mensagem do dia, os próximos atos por estado, o que levar a um protesto e as ferramentas do site.
- Avisa quando sai novidade, com notificação comum do Android e mensagem na conversa. Sem servidor: o app consulta o site.
  O aviso do W4K3 traz a capa e o vídeo. O de atos traz só convocações com data, no seu estado, que dá para ir pessoalmente.
- Recebe fotos e vídeos: a 2B vê a foto, ou 4 quadros do vídeo, sem som. No Windows, só fotos por enquanto.
- Lê a tela quando você pede ou, se você ligar, sempre que tocar na bolha. A leitura só é usada se você enviar.
- Analisa links sem abri-los, com as mesmas regras do Fact Check do site.
- Responde com o conteúdo do 0FF-GR1D e guias embutidos, mostrando as fontes.
- Busca na web só se você ligar. Fica ligada até você desligar e aparece no topo da conversa.
- Em urgências (golpe, conta invadida, prisão, gás) e direitos, mostra o guia conferido em vez da IA.
- Funciona sem modelo (guias e links) e offline depois de baixar um.
- Memória que você controla, inspetor do que a IA viu e do que saiu do aparelho, e botão de pânico.

## O que não faz

Não clica nem digita por você, não grava a tela, não ouve o microfone, não abre links recebidos e não tem telemetria própria.

## Compilar

Precisa de JDK 21 e Android SDK (plataforma 37.0). Defina `ANDROID_HOME`. Android 10 ou mais novo.

```
./gradlew :core:test                  # testes do núcleo
./gradlew :androidApp:assembleDebug   # APK em androidApp/build/outputs/apk/debug
./gradlew :desktopApp:run             # Windows
```

Se o Gradle falhar com `Unable to establish loopback connection` no Windows, defina
`JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/pasta-que-nao-existe` e tente de novo: isso faz o Java usar TCP.

## Organização

| Pasta | O que tem |
|---|---|
| `core` | cérebro: busca local, links, prompt, modelos, rede, dados cifrados. Sem Android nem Windows |
| `ui` | telas em Compose, iguais nos dois sistemas |
| `androidApp` | bolha, captura, assistente, Keystore |
| `desktopApp` | janela flutuante, captura, DPAPI |

Voz e regras da 2B: `core/.../chat/Persona.kt`. Guias: `core/src/main/resources/seed/guides.json`.

Windows sem instalar: `./gradlew :desktopApp:createDistributable` gera `desktopApp/build/compose/binaries/main/app/2B Companion/`.

Mais: [privacidade](docs/PRIVACIDADE.md) · [arte da personagem](docs/AVATAR.md) · [próximos passos](docs/ROADMAP.md)

## Licença

Código sob [MIT](LICENSE). A imagem da personagem e o nome não fazem parte dessa licença. O conteúdo baixado do site e
das agências pertence aos seus autores. Motor: [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) (Apache-2.0).
Modelos Gemma 4 E2B e Qwen3 0.6B (Apache-2.0) são baixados à parte.
