# Arte da personagem

Enquanto a arte não chega, o app usa um símbolo provisório. Para trocar, coloque PNGs transparentes e quadrados
(512 x 512, até 300 KB cada) com estes nomes:

| Arquivo | Quando aparece |
|---|---|
| `idle.png` | parada (obrigatório; serve de reserva para os outros) |
| `thinking.png` | lendo ou pensando |
| `talking.png` | respondendo |
| `alert.png` | link perigoso ou erro |

- Android: `androidApp/src/main/assets/avatar/`
- Windows: `desktopApp/src/main/resources/avatar/`

A bolha tem 64 dp: prefira rosto ou busto, contorno forte e boa leitura em fundo claro e escuro. Mantenha o mesmo
enquadramento nos quatro. Ícone do app: `androidApp/src/main/res/drawable/ic_launcher.xml`.
