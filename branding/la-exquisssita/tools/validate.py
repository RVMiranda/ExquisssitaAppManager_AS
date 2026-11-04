from pathlib import Path
import sys,json,hashlib,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools/vendor'))
from PIL import Image,ImageDraw,ImageFont,ImageOps
import numpy as np
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',20)
def label(im,xy,t,color='#3D2723'):ImageDraw.Draw(im).text(xy,t,font=font,fill=color)
def tile(im,path,xy,size=None):
    a=Image.open(path).convert('RGBA')
    if size:a=a.resize((size,size),Image.Resampling.LANCZOS)
    im.paste(a,xy,a)
board=Image.new('RGB',(1200,1320),'#FAF3E0')
for row,name in enumerate(['medio_cuerpo','busto']):
    y=row*660
    label(board,(20,y+10),f'{name}: referencia aprobada');label(board,(620,y+10),'SVG con paleta plana')
    tile(board,ROOT/'references'/f'{name}_aprobado.png',(0,y+50),600)
    tile(board,ROOT/'exports'/f'{name}_2048.png',(600,y+50),600)
board.save(ROOT/'previews/comparacion_original_vector.png')
board=Image.new('RGB',(1050,760),'#FAF3E0')
label(board,(20,15),'Reducciones reales en píxeles (sin ampliar en esta lámina)')
x=25
for size in [16,32,48,64,192]:
    label(board,(x,65),f'{size} px');tile(board,ROOT/'previews'/f'busto_{size}.png',(x,100));x+=size+40
label(board,(20,330),'Medio cuerpo: 256 px');tile(board,ROOT/'previews/medio_cuerpo_256.png',(20,370))
for x,bg in [(330,'#FFFFFF'),(680,'#252525')]:
    ImageDraw.Draw(board).rectangle((x,370,x+320,690),fill=bg)
    tile(board,ROOT/'exports/busto_2048.png',(x,370),320)
label(board,(330,710),'Transparencia sobre blanco');label(board,(680,710),'Transparencia sobre oscuro')
board.save(ROOT/'previews/reducciones_transparencia.png')
board=Image.new('RGB',(1000,760),'#EEEEEE')
fg=Image.open(ROOT/'previews/launcher_foreground.png').convert('RGBA')
mono=Image.open(ROOT/'previews/launcher_monochrome.png').convert('RGBA')
# Launcher masks apply to the central 72dp visible viewport, not full 108dp layer.
for i,shape in enumerate(['circle','rounded','mono']):
    a=Image.new('RGBA',(432,432),'#FAF3E0');a.alpha_composite(mono if shape=='mono' else fg)
    a=a.crop((72,72,360,360));mask=Image.new('L',(288,288));d=ImageDraw.Draw(mask)
    if shape=='circle':d.ellipse((0,0,287,287),fill=255)
    else:d.rounded_rectangle((0,0,287,287),radius=65,fill=255)
    a.putalpha(mask);board.paste(a,(20+i*325,65),a);label(board,(20+i*325,20),shape)
label(board,(20,390),'Capas completas 108dp y círculo seguro de 66dp')
a=Image.new('RGBA',(324,324),'#FAF3E0');a.alpha_composite(fg.resize((324,324)))
ImageDraw.Draw(a).ellipse((63,63,261,261),outline='#008080',width=2);board.paste(a,(20,425))
label(board,(390,420),'Splash 288dp, contenido dentro de 192dp')
tile(board,ROOT/'previews/splash_sistema_288.png',(400,455))
board.save(ROOT/'previews/mascaras_monocromo_splash.png')
# Round legacy variants with antialiased circular edge.
res=ROOT.parents[1]/'app/src/main/res'
for p in res.glob('mipmap-*/exquisssita_launcher_round.png'):
    a=Image.open(p).convert('RGBA');n=a.width;m=Image.new('L',(n*4,n*4));ImageDraw.Draw(m).ellipse((0,0,n*4-1,n*4-1),fill=255)
    a.putalpha(m.resize((n,n),Image.Resampling.LANCZOS));a.save(p)
report={'masters':{},'exports':{},'safe_radius_dp':None}
for name in ['medio_cuerpo','busto']:
    root=ET.parse(ROOT/'masters'/f'{name}.svg').getroot();tags=[p.tag.split('}')[-1] for p in root.iter()]
    assert 'image' not in tags and 'text' not in tags and 'filter' not in tags
    report['masters'][name]={'paths':tags.count('path'),'bytes':(ROOT/'masters'/f'{name}.svg').stat().st_size}
for p in (ROOT/'exports').glob('*.png'):
    a=Image.open(p);report['exports'][p.name]={'size':a.size,'mode':a.mode,'icc_profile':bool(a.info.get('icc_profile')),'bytes':p.stat().st_size}
play=Image.open(ROOT/'exports/google_play_512.png');assert play.size==(512,512) and play.mode=='RGBA' and play.getextrema()[3]==(255,255)
assert (ROOT/'exports/google_play_512.png').stat().st_size<1024*1024
arr=np.asarray(fg);ys,xs=np.where(arr[:,:,3]>127);r=np.sqrt((xs-215.5)**2+(ys-215.5)**2).max()/4
report['safe_radius_dp']=float(r);assert r<33
for size in [32,48,64,192]:assert Image.open(ROOT/'previews'/f'busto_{size}.png').size==(size,size)
(ROOT/'reports/validation.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
