# Rosto da personagem

O rosto vem de três vetores da artista, guardados em `design/rosto/original/`:

| Arquivo | Quadro | Uso |
|---|---|---|
| `Asset-1.svg` | neutro | rosto parado e ícone |
| `Asset-2.svg` | boca aberta | alterna com o neutro enquanto a 2B responde |
| `Asset-3.svg` | olhos fechados | piscada, a cada 2,5 a 6 s |

Pensando, o rosto pulsa. Em alerta, fica vermelho.

Para trocar a arte, substitua os SVG (só `path` e `polygon`, sem curvas, com a mesma caixa) e rode, na raiz do projeto:

```
python design/rosto/gerar_icones.py
python design/rosto/gerar.py
```

O primeiro refaz `design/rosto/quadros.json`, as opções em `design/icones/` e os SVG de cada quadro. O segundo refaz
`ui/.../FaceArt.kt`, o ícone do app (`ic_launcher_foreground`, `ic_launcher_mono`, fundo em `launcher_bg`) e o ícone de
notificação (`ic_2b`). O ícone do app é a opção 7 (`design/icones/opcao-07.svg`). A cor do rosto no app fica em `Palette.Face`.

O rosto e o nome da personagem não fazem parte da licença MIT do código.
