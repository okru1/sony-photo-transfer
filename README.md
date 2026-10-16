# Sony Photo Transfer (Android)

App nativa para Android (Kotlin + Jetpack Compose) que se conecta por Wi-Fi a una **Sony α7 III (ILCE-7M3)**, muestra las fotos de la tarjeta SD y descarga los archivos originales al teléfono, sin cables, sin ordenador y sin sacar la tarjeta.

> App independiente, sin relación con Sony. Usa un protocolo que Sony **no documenta oficialmente** (ver abajo).

---

## 1. Qué permite realmente la α7 III (investigación previa)

| Pregunta | Respuesta |
|---|---|
| ¿Qué protocolo usa para enviar fotos por Wi-Fi? | En **«Enviar a smartphone»** la cámara crea su propia red Wi-Fi (`DIRECT-xxxx:ILCE-7M3`, cámara en `192.168.122.1`) y arranca un **servidor UPnP/DLNA (MediaServer)** con el servicio estándar `ContentDirectory:1`, normalmente en el puerto `64321` (`/DmsDescPush.xml` o `/dd.xml`). Es lo que usa por dentro Imaging Edge Mobile. |
| ¿Hay API o SDK oficial para Android? | **No.** La antigua *Camera Remote API* (JSON-RPC) está descatalogada y la α7 III no admite apps PlayMemories. El *Camera Remote SDK* actual es para PC (C++), sirve para control remoto, no para galería, y la ILCE-7M3 no aparece entre sus modelos compatibles. |
| ¿Se puede listar la tarjeta y bajar originales? | **Sí, para JPEG.** El servicio `Browse` devuelve carpetas y cada foto con varias URL: `TN_` (miniatura), `SM_`/`LRG_` (vistas previas generadas por la cámara) y `ORG_` (el **archivo original**, con tamaño y resolución). |
| ¿Hace falta un modo concreto? | Sí: MENU → Red → *Func. Enviar a smartphone* → *Enviar a smartphone* → **«Seleccionar en smartphone»** (acceso a todas las fotos). Con «Seleccionar en este dispositivo» solo se ven las elegidas en la cámara; la app también lo admite. |
| ¿Cámara y móvil en la misma red de casa? | No para esta función: la cámara **crea su propia red**. Su modo «Enviar a ordenador» (PTP/IP, por router) envía solo a un PC emparejado con PlayMemories Home y no permite explorar la tarjeta. |
| RAW / JPEG / RAW+JPEG | **Los RAW (.ARW) no se pueden descargar por Wi-Fi**: el servidor solo ofrece vistas previas JPEG de ellos y pedir el original devuelve error (HTTP 406). Los JPEG sí salen como archivo original. Si disparas en RAW+JPEG, el JPEG es lo único que se puede descargar como original. Los vídeos no se listan. |
| Alternativa oficial | Imaging Edge Mobile (mismas limitaciones). Para el RAW original: **USB-C** (MTP, `android.mtp.MtpDevice`) o un lector de tarjetas. |

**Fuentes:** [ImagingEdge4Linux](https://github.com/schorschii/ImagingEdge4Linux) (cliente que usa este mismo protocolo: SOAP sobre HTTP, puerto 64321, «RAW no admitido»), [SonnyLink](https://github.com/XiaoQAQ10086/SonnyLink) (cliente Android con capturas reales de las respuestas DLNA de Sony: `ORG_` solo en JPEG, 406 para ARW, sin vídeo), [Sony Camera Remote Toolkit](https://support.d-imaging.sony.co.jp/app/sdk/en/index.html) (modelos admitidos por el SDK), y las guías de Sony sobre Imaging Edge Mobile y la ILCE-7M3.

**Importante:** ninguno de esos proyectos publica una prueba concreta con una ILCE-7M3 (sí con a6000/a6300/a6400/a6600, α7S, RX100…). La α7 III usa la misma función «Enviar a smartphone» de esa generación, por lo que se espera que funcione igual, **pero no lo he podido comprobar con una cámara real**. Para eso está la pantalla **Ajustes → Diagnóstico**, que registra cada paso de la conexión y permite copiarlo.

---

## 2. Qué hace la app

1. **Inicio:** estado (desconectada / conectando / buscando cámara / conectada / error), modelo detectado, botón **Conectar cámara**, accesos a galería, transferencias y descargadas.
2. **Conexión Wi-Fi** con la API oficial `WifiNetworkSpecifier` (Android 10+): introduces SSID y contraseña que muestra la cámara y Android pide confirmar. Todo el tráfico a la cámara va por esa red (`Network.openConnection`), así que los datos móviles siguen funcionando. Alternativa: «Ya estoy conectado a la Wi-Fi de la cámara».
3. **Verificación:** descarga la descripción UPnP de la cámara (`192.168.122.1`, la puerta de enlace de la red, y si falla, descubrimiento SSDP) y comprueba que existe el servicio de contenidos.
4. **Galería:** cuadrícula con miniaturas que se va llenando mientras la cámara responde, filtros JPEG/RAW, selección individual (pulsación larga) y múltiple, «seleccionar todas las visibles», indicador de ya descargada.
5. **Visor:** vista previa grande (doble toque para ampliar, deslizar entre fotos) con nombre, formato, tamaño, resolución y fecha cuando la cámara los indica.
6. **Transferencias:** cola en un *foreground service* (sigue con la pantalla apagada), progreso por archivo y global (%, bytes, pendientes), cancelar una o todas, reintentar, omitir duplicadas.
7. **Verificación de cada archivo** antes de darlo por bueno: bytes recibidos = `Content-Length` (o tamaño anunciado), cabecera JPEG válida y tamaño final guardado = recibido. Si algo falla, se borra el archivo parcial y el error se muestra.
8. **Destino:** `Imágenes/SonyPhotoTransfer` vía MediaStore (aparece en la galería de Android) o una carpeta elegida con el selector del sistema. Nunca se sobrescribe: los nombres repetidos reciben sufijo.
9. **Ajustes:** carpeta de destino, formatos (JPEG / RAW / ambos), guardar o no la vista previa de los RAW (marcada como `_preview`, nunca como original), mantener pantalla encendida, explicación de permisos, diagnóstico.

Los archivos se copian en streaming (bloques de 256 KB) sin cargarlos en memoria ni recomprimirlos, conservando nombre, extensión y metadatos EXIF originales. Las miniaturas tienen su propio cargador, caché y límite de concurrencia, separados de las descargas.

### Permisos

| Permiso | Por qué |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Unirse a la red de la cámara y hablar con ella. **No se pide ubicación.** |
| `CHANGE_WIFI_MULTICAST_STATE` | Descubrimiento SSDP si la cámara no está en la dirección habitual. |
| `FOREGROUND_SERVICE(_DATA_SYNC)`, `WAKE_LOCK` | Que una descarga larga no se corte con la pantalla apagada. |
| `POST_NOTIFICATIONS` (opcional, se explica antes de pedirlo) | Ver y cancelar el progreso desde la notificación. |
| Almacenamiento | **Ninguno.** MediaStore / selector de carpetas. |

El tráfico HTTP sin cifrar está permitido porque la cámara solo habla HTTP en su propia red; la app no contacta con ningún otro servidor.

---

## 3. Arquitectura

```
camera-core/   Kotlin/JVM puro, sin Android
  CameraGateway        interfaz que usa la app (connect, photos, open, close)
  dlna/SonyDlnaCamera  implementación «Enviar a smartphone» (UPnP ContentDirectory)
  dlna/*               parseo de descripción UPnP, DIDL-Lite, SSDP
  http/HttpTransport   HTTP inyectable (en Android, enlazado a la red de la cámara)
app/
  wifi/CameraWifi            WifiNetworkSpecifier / red actual
  camera/CameraSession       estado de conexión + catálogo
  thumbnails/ThumbnailLoader miniaturas (independiente de las descargas)
  transfer/                  TransferManager (cola, verificación) + TransferService
  storage/                   PhotoSaver (MediaStore/SAF) + DownloadIndex (duplicados)
  ui/                        Compose + Material 3
```

Para añadir otro transporte (p. ej. USB/MTP para obtener RAW) basta con otra implementación de `CameraGateway`.

- `minSdk 29` (Android 10), `targetSdk 35`. Dependencias: solo AndroidX/Compose/Material 3 y coroutines; sin librerías de terceros.

## 4. Compilar y probar

```bash
./gradlew :camera-core:test      # tests del protocolo (JVM, con un servidor HTTP que imita la cámara)
./gradlew :app:assembleDebug     # APK de depuración (requiere Android SDK; o abrir en Android Studio)
```

Estado de verificación: los 17 tests de `camera-core` pasan. El módulo `app` **no se ha podido compilar en el entorno donde se escribió** (sin acceso al Android SDK ni al repositorio Maven de Google), así que la primera compilación en Android Studio puede requerir pequeños ajustes. Tampoco se ha probado con una α7 III real.

## 5. Siguientes pasos posibles

- Probar con la cámara y adjuntar el registro de Diagnóstico si algo falla.
- Transporte USB-C/MTP para descargar RAW originales (la única vía directa al ARW desde el teléfono).
- Leer el código QR que muestra la cámara para no teclear SSID y contraseña.
