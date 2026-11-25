# Privacidade e segurança

## O que sai do aparelho

| Quando | O quê | Para quem |
|---|---|---|
| Você toca em ATUALIZAR ou pede notícia, mensagem ou atos | pedido dos arquivos públicos do site | GitHub Pages |
| Avisos ligados, cerca de uma vez por hora | pedido dos mesmos arquivos; os estados que você escolheu não vão | GitHub Pages |
| Você baixa um modelo | pedido do arquivo | Hugging Face |
| WEB ligada, a cada pergunta (menos papo e atalhos do site) | só o texto da pergunta | Wikipédia ou o seu SearXNG |
| Você toca numa fonte | o link abre no navegador | o site da fonte |

Tela, memórias e histórico nunca entram na busca. O que o app pediu à rede aparece no Inspetor. Sem Tor, esses serviços
veem seu IP.

## O que fica guardado

Ajustes, memórias, cópia do conteúdo do site, os últimos 20 avisos e, se você ligar, conversas. Cifrados com AES-256-GCM: chave no Keystore
(Android) ou protegida pela conta do Windows. Capturas ficam só na memória. Backup do sistema desligado no Android.

## Limites, sem promessa

- Avisos: GitHub vê que o seu IP consulta o site de hora em hora. O texto aparece na tela bloqueada se o Android mostrar
  conteúdo de notificações; escondido, ele diz só que há um aviso novo.
- "Ler a tela sozinha": o Android mantém a autorização aberta e mostra o aviso dele. Entre uma leitura e outra, a 2B não
  recebe imagem. Pare pela notificação ou nos Ajustes.
- Quem pega o aparelho **desbloqueado** lê o que o app lê.
- O pânico apaga a chave e os arquivos. A memória do aparelho pode reter restos, mas cifrados. O modelo não é cifrado.
- O modelo erra. Por isso há fontes, avisos por regras e remoção de links que não vieram das fontes.
- Texto malicioso na tela ou na web pode tentar enganar a IA. Um filtro tira as ordens mais comuns de textos, não de
  imagens, e não pega tudo. Ela não tem poder de agir, só de responder.
- Como assistente padrão, o Android entrega a tela a cada chamada. Desligue nos ajustes do sistema se não quiser.
- O suporte a Tor ainda não foi verificado num celular com captura de rede. Não dependa dele em situação de risco.
