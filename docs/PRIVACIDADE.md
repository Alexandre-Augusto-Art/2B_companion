# Privacidade e segurança

## O que sai do aparelho

| Quando | O quê | Para quem |
|---|---|---|
| Você toca em ATUALIZAR ou pede notícia, mensagem ou atos | pedido dos arquivos públicos do site | GitHub Pages |
| Avisos ligados, cerca de uma vez por hora | pedido dos mesmos arquivos; os estados que você escolheu não vão | GitHub Pages |
| Aviso do W4K3 chega, ou você toca em ASSISTIR | a capa e o vídeo do dia; o vídeo não baixa sozinho em dados móveis | GitHub Pages |
| Você baixa um modelo | pedido do arquivo | Hugging Face |
| WEB ligada, a cada pergunta (menos papo e atalhos do site) | só o texto da pergunta | Wikipédia ou o seu SearXNG |
| Você toca numa fonte | o link abre no navegador | o site da fonte |

Tela, fotos, vídeos, memórias e histórico nunca entram na busca. O seletor do Android entrega só o arquivo escolhido, sem acesso à galeria. O que o app pediu à rede aparece no Inspetor. Sem Tor, esses serviços
veem seu IP.

## O que fica guardado

Ajustes, memórias, cópia do conteúdo do site, os últimos 20 avisos e, se você ligar, conversas. Cifrados com AES-256-GCM: chave no Keystore
(Android) ou protegida pela conta do Windows. Capturas, fotos e vídeos que você manda ficam só na memória: a conversa guardada leva só o texto. Os últimos 5 vídeos do W4K3 ficam no cache do app e somem no pânico. Backup do sistema desligado no Android.

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
