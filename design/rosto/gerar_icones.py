"""Gera o rosto da 2B (3 quadros) e as 10 opções de ícone a partir dos SVG da artista."""
import os, re, json

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'original')
REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(REPO, 'design')


def shapes(name):
    """[(classe, path d)] com polígonos convertidos em path."""
    s = open(os.path.join(SRC, name), encoding='utf-8').read()
    out = []
    for tag in re.findall(r'<(?:path|polygon)[^>]*/>', s):
        cls = re.search(r'class="([^"]+)"', tag).group(1)
        d = re.search(r' d="([^"]+)"', tag)
        if d:
            d = d.group(1)
        else:
            pts = re.search(r'points="([^"]+)"', tag).group(1).split()
            d = 'M' + ' L'.join(p.replace(',', ' ') for p in pts) + ' Z'
        out.append((cls, re.sub(r'\s+', ' ', d.strip())))
    return out


FRAMES = {'neutro': shapes('Asset-1.svg'), 'fala': shapes('Asset-2.svg'), 'pisca': shapes('Asset-3.svg')}
W, H = 1163.755, 942.118  # caixa comum: a boca aberta desce até 942
FACE_H = 826.14           # altura do rosto neutro, usada para centralizar o ícone

GREEN, PINK, BLACK = '#00FF00', '#FF1FCE', '#0B0B0B'


def face(frame, color, accent=None, dx=0.0, dy=0.0):
    return ''.join(f'<path fill="{accent if (accent and c == "cls-2") else color}" fill-rule="evenodd" '
                   f'transform="translate({dx} {dy})" d="{d}"/>' for c, d in FRAMES[frame])


def icon(bg, color, accent=None, ring=None, glitch=None, scale=0.62):
    """Ícone 1024x1024 com o rosto neutro centrado. bg: None, ('sq', cor), ('round', cor), ('circle', cor)."""
    S = 1024
    k = S * scale / W
    tx, ty = (S - W * k) / 2, (S - FACE_H * k) / 2
    parts = []
    if bg:
        kind, fill = bg
        if kind == 'sq':
            parts.append(f'<rect width="{S}" height="{S}" fill="{fill}"/>')
        elif kind == 'round':
            parts.append(f'<rect width="{S}" height="{S}" rx="230" fill="{fill}"/>')
        else:
            parts.append(f'<circle cx="{S/2}" cy="{S/2}" r="{S/2}" fill="{fill}"/>')
    if ring:
        kind, col = ring
        if kind == 'circle':
            parts.append(f'<circle cx="{S/2}" cy="{S/2}" r="{S/2-30}" fill="none" stroke="{col}" stroke-width="22"/>')
        else:
            parts.append(f'<rect x="28" y="28" width="{S-56}" height="{S-56}" rx="205" fill="none" stroke="{col}" stroke-width="22"/>')
    g = f'<g transform="translate({tx:.2f} {ty:.2f}) scale({k:.5f})">'
    if glitch:
        parts.append(g + face('neutro', glitch, dx=-26, dy=14) + '</g>')
    parts.append(g + face('neutro', color, accent) + '</g>')
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {S} {S}">' + ''.join(parts) + '</svg>'


OPTIONS = [
    ('Verde, sem fundo', icon(None, GREEN)),
    ('Rosa, sem fundo', icon(None, PINK)),
    ('Verde no quadrado preto', icon(('sq', BLACK), GREEN)),
    ('Rosa no quadrado preto', icon(('sq', BLACK), PINK)),
    ('Verde, roxo escuro e contorno', icon(('round', '#16071F'), GREEN, ring=('round', GREEN), scale=0.56)),
    ('Rosa no círculo preto com anel', icon(('circle', BLACK), PINK, ring=('circle', PINK), scale=0.56)),
    ('Preto no quadrado verde', icon(('round', GREEN), BLACK)),
    ('Preto no círculo rosa', icon(('circle', PINK), BLACK, scale=0.58)),
    ('Verde com sombra rosa (glitch)', icon(('round', BLACK), GREEN, glitch=PINK)),
    ('Rosa com losangos verdes', icon(('round', '#120018'), PINK, accent=GREEN)),
]

os.makedirs(os.path.join(OUT, 'icones'), exist_ok=True)
for i, (name, svg) in enumerate(OPTIONS, 1):
    open(os.path.join(OUT, 'icones', f'opcao-{i:02d}.svg'), 'w', encoding='utf-8').write(svg)

# Os três quadros do rosto, na mesma caixa, para o app.
os.makedirs(os.path.join(OUT, 'rosto'), exist_ok=True)
for f in FRAMES:
    open(os.path.join(OUT, 'rosto', f'{f}.svg'), 'w', encoding='utf-8').write(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">{face(f, GREEN)}</svg>')
json.dump({k: [[c, d] for c, d in v] for k, v in FRAMES.items()}, open(os.path.join(OUT, 'rosto', 'quadros.json'), 'w'), indent=1)

