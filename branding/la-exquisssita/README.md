# La Exquisssita — recursos de identidad

Producción del 29 de octubre de 2026 a partir de las dos referencias aprobadas. Los originales se conservan en `references/`. Se realizó segmentación de color, eliminación del fondo exterior conectado al borde y vectorización por curvas Bézier con VTracer; se revisaron los renders comparándolos con las referencias. No se regeneraron las mascotas con IA. Los SVG contienen trazados editables, no mapas de bits incrustados. Son trazados de una ilustración: no equivalen a una reconstrucción manual con pocos nodos.

## Archivos

| Ubicación | Uso |
|---|---|
| `masters/medio_cuerpo.svg` | Maestro de bienvenida con cesta y bolso completos, lienzo 1254 × 1254 unidades |
| `masters/busto.svg` | Maestro del busto, lienzo 1254 × 1254 unidades |
| `masters/*_monocromo.svg` | Trazos oscuros aislados, con huecos transparentes; no una silueta rellena |
| `exports/*_2048.png` | PNG RGBA transparentes, 2048 × 2048, perfil sRGB, exportados desde SVG |
| `exports/google_play_512.png` | PNG RGBA de 512 × 512, sRGB, fondo vainilla opaco, sin esquinas ni sombra exterior |
| `masters/launcher_foreground.svg` | Primer plano transparente de 108 × 108; figura completa dentro del círculo seguro de 66 |
| `masters/launcher_monochrome.svg` | Variante temática con los mismos márgenes |
| `masters/splash_sistema.svg` | Lienzo 288 × 288, figura dentro del círculo de 192: modalidad sin fondo de icono |
| `previews/` | Comparación original/vector, reducciones, transparencia, máscaras y splash |
| `reports/` | Validaciones estructurales, auditoría y resultados de compilación |

## Paleta exacta

- Vainilla `#FAF3E0`: canasta, pétalos, cuello, puños y cinta. El exterior es transparente en los maestros.
- Café rojizo `#7E392F`: vestido, sombrero, bolso y boca.
- Chocolate `#3D2723`: trazos, ojos y pezuñas.
- Albaricoque `#E5C2A4`: piel y brazos.
- Dorado `#B8860B`: centro de la flor.
- Ocre `#A07324`: hojas de la cesta. El busto no necesita este color.

No hay degradados, efectos, sombras ni texturas. Los contornos conservan el grosor y modulación de las referencias; no se añadieron halos. La separación de brazo/cesta/hojas depende de sus rellenos distintos y del orden de superposición.

## Recursos Android preparados, sin activar

En `app/src/main/res/`:

- `drawable/exquisssita_medio_cuerpo.xml` y `exquisssita_busto.xml`: VectorDrawable con los trazados del maestro.
- `drawable-nodpi/exquisssita_bienvenida_1120.png`: PNG transparente 1120 × 1120; alternativa recomendada para la ilustración detallada de bienvenida.
- `drawable/exquisssita_launcher_foreground.xml`, `exquisssita_launcher_background.xml` y `exquisssita_launcher_monochrome.xml`: capas vectoriales; no hacen falta PNG por densidad para estas capas.
- `mipmap-anydpi-v26/exquisssita_launcher.xml` y `exquisssita_launcher_round.xml`: iconos adaptativos.
- `mipmap-anydpi-v33/`: mismas definiciones con la capa monocromática para iconos temáticos.
- `mipmap-mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`: PNG tradicionales de 48, 72, 96, 144 y 192 px. Las variantes `_round` llevan recorte circular; las normales tienen fondo cuadrado.
- `drawable/exquisssita_splash_sistema.xml`: busto vectorial para splash del sistema, 288dp, sin fondo propio.

No se modificaron las referencias activas del manifiesto, los iconos existentes, temas, dependencias, pantallas ni navegación.

## Integración posterior

Cuando se decida activar el material, apuntar `android:icon` a `@mipmap/exquisssita_launcher` y `android:roundIcon` a `@mipmap/exquisssita_launcher_round`.

Para el splash de Android 12+, utilizar `exquisssita_splash_sistema` como `windowSplashScreenAnimatedIcon`, aunque sea un VectorDrawable estático; fondo de ventana vainilla. Este recurso está preparado para la modalidad **sin** `windowSplashScreenIconBackgroundColor`: 288dp con zona circular de 192dp. Si posteriormente se elige fondo propio del icono, hay que adaptar a 240dp/160dp, no reutilizar el mismo encuadre sin comprobarlo. Para compatibilidad anterior, valorar `androidx.core:core-splashscreen` al implementar. No añadir esperas artificiales.

La bienvenida dentro de Compose puede mostrar `R.drawable.exquisssita_bienvenida_1120` mediante `Image`, en un área cuadrada adaptable de 240–280dp con `ContentScale.Fit`. Reducir ese área en ventanas estrechas y respetar insets; no recortar la cesta ni el bolso. El PNG admite 280dp a densidad 4×. La pantalla de bienvenida aún no se ha creado.

## Revisión visual y límites

Se revisaron originales y renders vectoriales grandes, busto a 32, 48, 64 y 192px (también 16px como prueba de límite), y medio cuerpo a 256px. Las láminas contienen los PNG a su tamaño de píxel real, aunque el visor puede escalar la lámina completa.

La mirada y la flor se conservan mejor desde 48–64px. A 32px se reconoce la mascota pero pierde detalle facial; a 16px esta composición no es recomendable. No se simplificó el rostro aprobado para ocultar esa limitación. Los contornos oscuros pierden contraste sobre fondos muy oscuros: preferir fondo vainilla para la presentación de marca. Las variantes monocromáticas conservan huecos y expresión, pero sus líneas finas también requieren revisión en el launcher real.

Se comprobaron máscaras circular y cuadrada redondeada mediante renders; no son pruebas en dispositivos. La auditoría de logo-design señala complejidad y cantidad de colores: son consecuencias de conservar la ilustración y la paleta solicitadas. No se promete optimización para bordado, favicon de 16px ni impresión a una tinta diminuta.

## Reproducción

`tools/trace.py` prepara los SVG desde las copias originales; `package.py` crea las variantes y XML; `export.cjs` renderiza PNG con Sharp; `validate.py` genera comparaciones y verifica dimensiones, perfil, transparencia y zona segura. Las dependencias locales de Python viven en `tools/vendor/` (ignoradas); herramientas: Pillow, NumPy, SciPy, VTracer. Sharp usa el runtime local de Codex. Las rutas de los runtimes deberán ajustarse para otra máquina.

## Especificaciones consultadas

- [Android: iconos adaptativos](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
- [Android: splash del sistema](https://developer.android.com/develop/ui/views/launch/splash-screen)
- [Google Play: especificaciones del icono](https://developer.android.com/distribute/google-play/resources/icon-design-specifications)
