# Próximos passos

## 0.1, fechar o Android
- [x] IA real com Qwen3 0.6B no Windows (`MODEL_DIR=... ./gradlew :desktopApp:test --tests "*RealModelTest*"`)
- [x] IA real com Gemma 4 E2B no Windows, com imagem
- [ ] Reduzir a espera pela primeira palavra (i5-12600K, só CPU: 4 a 7 s em texto, 12 a 16 s com imagem)
- [ ] Testar em celular: bolha, captura, assistente, pânico, rotação, pouca memória
- [ ] Medir velocidade, RAM e bateria com Gemma 4 E2B e Qwen3 0.6B
- [ ] Verificar o Tor com captura de rede
- [ ] Revisar os guias com quem entende de direito e segurança digital
- [ ] Arte e ícone da personagem
- [x] Primeiro APK de teste no GitHub
- [ ] Assinatura de release

## 0.2
- [x] Personalidade ativista hacker e papo do dia a dia
- [x] Web, leitura de tela e limpeza como opções fixas
- [x] Notícia, mensagem do dia, atos, checklist e ferramentas do site
- [x] Avisos por notificação
- [ ] Testar em celular: avisos com o app fechado e leitura de tela contínua
- [ ] Avisos na hora (hoje podem atrasar; exigiria um servidor)

## 0.3
- [ ] Busca por significado (embeddings locais)
- [ ] OCR local, para ler imagens com modelo só de texto
- [ ] Animações da personagem
- [ ] PIN e atalho de pânico
- [ ] Voz local (ouvir e falar)

## 0.4, Windows
- [ ] Testar a janela flutuante e a captura em vários monitores
- [ ] Instalador e assinatura
- [ ] Modelo com placa de vídeo
