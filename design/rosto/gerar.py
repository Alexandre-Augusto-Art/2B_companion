"""Leva o rosto da 2B (design/rosto/quadros.json) para o código: Kotlin, ícone do app e ícone de notificação."""
import os, json, re

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
F = json.load(open(os.path.join(REPO, 'design', 'rosto', 'quadros.json')))
W, H, FACE_H = 1163.755, 942.118, 826.14
NAMES = {'neutro': 'NEUTRAL', 'fala': 'TALK', 'pisca': 'BLINK'}


def rings(d):
    out = []
    for sub in d.split('M')[1:]:
        nums = re.findall(r'-?[\d.]+', sub)
        out.append([float(n) for n in nums])
    return out


def fmt(x):
    s = ('%.3f' % x).rstrip('0').rstrip('.')
    return s + 'f'


kt = ['package br.offgr1d.companion.ui', '',
      '/**',
      ' * Rosto da 2B, da artista (design/rosto). Gerado por design/rosto/gerar.py: não edite à mão.',
      ' * Cada forma é uma lista de contornos (x, y alternados); contorno dentro de outro vira buraco.',
      ' */',
      'object FaceArt {',
      f'    const val WIDTH = {fmt(W)}',
      f'    const val HEIGHT = {fmt(H)}',
      '',
      '    /** @param accent os losangos da testa, que podem ter outra cor */',
      '    class Shape(val accent: Boolean, val rings: List<FloatArray>)', '']
for k, name in NAMES.items():
    kt.append(f'    val {name}: List<Shape> = listOf(')
    for cls, d in F[k]:
        rs = ', '.join('floatArrayOf(' + ', '.join(fmt(v) for v in r) + ')' for r in rings(d))
        kt.append(f'        Shape({str(cls == "cls-2").lower()}, listOf({rs})),')
    kt.append('    )')
kt.append('}')
open(os.path.join(REPO, 'ui', 'src', 'commonMain', 'kotlin', 'br', 'offgr1d', 'companion', 'ui', 'FaceArt.kt'), 'w', encoding='utf-8', newline='\n').write('\n'.join(kt) + '\n')


def vector(size, viewport, width_frac, color, name_comment):
    """Vector drawable com o rosto neutro centrado."""
    k = viewport * width_frac / W
    tx, ty = (viewport - W * k) / 2, (viewport - FACE_H * k) / 2
    paths = '\n'.join(f'        <path android:fillColor="{color}" android:fillType="evenOdd" android:pathData="{d}" />' for _, d in F['neutro'])
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {name_comment} Gerado por design/rosto/gerar.py. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{size}dp" android:height="{size}dp" '
            f'android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n'
            f'    <group android:translateX="{tx:.3f}" android:translateY="{ty:.3f}" android:scaleX="{k:.6f}" android:scaleY="{k:.6f}">\n'
            f'{paths}\n    </group>\n</vector>\n')


RES = os.path.join(REPO, 'androidApp', 'src', 'main', 'res')
# Ícone escolhido: opção 7, rosto preto no fundo verde. A área segura do ícone adaptável é o círculo de 66 em 108.
open(os.path.join(RES, 'drawable', 'ic_launcher_foreground.xml'), 'w', encoding='utf-8', newline='\n').write(
    vector(108, 108, 0.56, '#0B0B0B', 'Rosto da 2B no ícone do app (opção 7).'))
open(os.path.join(RES, 'drawable', 'ic_launcher_mono.xml'), 'w', encoding='utf-8', newline='\n').write(
    vector(108, 108, 0.56, '#FFFFFFFF', 'Ícone temático do Android 13+: só a silhueta.'))
open(os.path.join(RES, 'drawable', 'ic_launcher.xml'), 'w', encoding='utf-8', newline='\n').write(
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '    <background android:drawable="@color/launcher_bg" />\n'
    '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
    '    <monochrome android:drawable="@drawable/ic_launcher_mono" />\n'
    '</adaptive-icon>\n')
# Notificação: o Android pinta de uma cor só; a silhueta precisa ocupar quase toda a largura para ser lida.
open(os.path.join(RES, 'drawable', 'ic_2b.xml'), 'w', encoding='utf-8', newline='\n').write(
    vector(24, 24, 0.94, '#FFFFFFFF', 'Ícone de notificação.'))
print('ok')
