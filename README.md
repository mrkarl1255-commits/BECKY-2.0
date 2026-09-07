# BECKY BRIDGE

Aplicación Android **nativa** (Kotlin + Android SDK + Gradle) cuya Fase 1
implementa un puente de comunicación Bluetooth/BLE entre un teléfono
Android y un reloj inteligente.

**Application ID**: `com.becky.bridge`
**Lenguaje**: Kotlin 100%
**minSdk**: 26 · **targetSdk / compileSdk**: 34 · **Java**: 17

## Estado real de esta entrega

Código Android nativo completo, **NO** una web envuelta en APK, **NO**
un prototipo visual, **NO** una app simulada. Compilado y verificado
de extremo a extremo, incluyendo la arquitectura de 4 capas de la
Fase 3.1 (`Voice -> Assistant/Becky -> Command/Capability -> BLE/Repository`):

- ✅ `./gradlew assembleDebug` → **BUILD SUCCESSFUL** (21m 11s en este
  sandbox). APK generado y verificado en `BECKY-BRIDGE-debug.apk`
  (sha256 `23849d02b903e929a729cc9ed09eb312af61dc7c2018609df22e994b8d509bb0`).
- ✅ `./gradlew assembleRelease` → **BUILD SUCCESSFUL** (26m 25s en este
  sandbox). APK firmado y verificado en `BECKY-BRIDGE-release.apk`
  (sha256 `6b3efd966170ccf987ea550b6e9edd4d676a33bfe52373b3f03674fd9aae57d5`).
- ✅ Verificación de integridad ZIP (`unzip -t`): sin errores en ambos APK.
- ✅ Verificación de firma (`apksigner verify --verbose`): `Verifies: true`,
  esquema v2 confirmado, 1 firmante, en ambos APK.
- ✅ Verificación de manifiesto (`aapt2 dump badging` + `dump xmltree`):
  package `com.becky.bridge` (release) / `com.becky.bridge.debug` (debug),
  versionCode 1, versionName 1.0.0, minSdk 26, targetSdk 34, todos los
  permisos declarados correctamente (incluyendo `RECORD_AUDIO`), las 6
  Activities registradas incluyendo `ui.voice.VoiceActivity`.
- ✅ `classes.dex` / `classes2.dex` presentes en ambos APK (multidex OK).
- ✅ Repositorio Git con historial de commits.

### Incidencias resueltas durante la compilación

1. **Namespace `xmlns:tools` mal ubicado** en `AndroidManifest.xml`
   (estaba en `<application>` pero se usaba antes, en
   `<uses-permission tools:targetApi="s">`). Solución: se movió la
   declaración a la etiqueta raíz `<manifest>`.
2. **Estilo base `BeckyText` inexistente**: `styles.xml` usaba
   herencia por notación de punto (`BeckyText.Title`, etc.) sin que
   existiera `BeckyText` como estilo base. Solución: se añadió
   `<style name="BeckyText" parent="android:Widget.TextView" />`.
3. **JDK incompleto en el entorno de compilación**: solo estaba
   instalado `openjdk-21-jre-headless` (sin `jlink`), necesario para la
   transformación `JdkImageTransform` del Android Gradle Plugin.
   Solución: instalar `openjdk-21-jdk-headless` completo.

Ninguno de estos 3 fixes afecta al código de la app en sí (arquitectura,
lógica BLE, UI); son ajustes de configuración/entorno de build.

## Firma de la APK release

Se generó un keystore de desarrollo/pruebas incluido en el repositorio
(`keystore/becky-bridge-release.keystore`) para que
`./gradlew assembleRelease` sea 100% reproducible sin pasos manuales.

- **Alias**: `becky-bridge`
- **Password keystore / key**: `beckybridge2026`
- **Validez**: 10.000 días (autofirmado, SHA384withRSA, RSA 2048)

⚠️ **Antes de publicar en Google Play o cualquier canal de producción**,
genera tu propio keystore y actualiza `signingConfigs.release` en
`app/build.gradle.kts`. No distribuyas una app de producción firmada con
esta clave de desarrollo.

```bash
keytool -genkeypair -v -keystore mi-keystore.jks -alias mi-alias \
  -keyalg RSA -keysize 2048 -validity 10000
```

## Mapa de la arquitectura (paquete `com.becky.bridge`)

```
bluetooth/
  PermissionsHelper.kt      Permisos runtime (BLUETOOTH_SCAN/CONNECT, location legacy)
  BluetoothStateHelper.kt   Estado del adaptador Bluetooth
  BleScanner.kt             Escáner BLE (StateFlow de dispositivos)
  GattManager.kt            Conexión GATT, descubrimiento de servicios, read/write/notify
  ReconnectionManager.kt    Reconexión con backoff exponencial, activable/desactivable
  BridgeRepository.kt       Fuente única de verdad que conecta todo lo anterior

communication/
  CommunicationTransport.kt Interfaz de transporte agnóstica (sección 9)
  BluetoothTransport.kt     Implementación BLE de CommunicationTransport
  WifiTransport.kt          Placeholder para Fase 2+ (Wi-Fi)
  BeckyCommandHandler.kt    Interfaz para que BECKY envíe comandos al reloj (sección 16)

model/
  BridgeMessage.kt          Protocolo de mensajes (PHONE_TO_WATCH, COMMAND, etc.)
  BleModels.kt              ScannedDevice, ConnectionState, BleServiceInfo, etc.

logging/
  LogEntry.kt, BeckyLogger.kt   Sistema de logs en memoria + exportación a texto

service/
  BridgeForegroundService.kt    Servicio en primer plano con notificación de estado

ui/
  main/MainActivity.kt          Pantalla principal (5 botones de navegación)
  devices/DevicesActivity.kt    Lista de dispositivos escaneados
  connection/ConnectionActivity.kt   Servicios/características GATT
  diagnostics/DiagnosticsActivity.kt Panel de diagnóstico (incluye pruebas manuales de comandos BECKY)
  logs/LogsActivity.kt          Visor de registros

voice/ (Fase 3 - capa de voz, desacoplada del BLE)
  VoiceModels.kt            VoiceState (estados mecánicos del pipeline de voz: idle/wake/listening/processing/speaking/error)
  WakeWordDetector.kt       Interfaz + PlaceholderWakeWordDetector ("Hey Becky", arquitectura lista)
  SpeechToTextEngine.kt     Interfaz + AndroidSpeechToTextEngine (android.speech.SpeechRecognizer nativo)
  TextToSpeechEngine.kt     Interfaz + AndroidTextToSpeechEngine (android.speech.tts.TextToSpeech nativo)
  VoiceEngine.kt            Interfaz + DefaultVoiceEngine (orquesta wake word -> STT -> BeckyAssistant -> TTS)
  VoicePermissions.kt       Permiso RECORD_AUDIO (separado de PermissionsHelper, específico de BLE)
  VoiceRepository.kt        Ensambla voz + assistant + capacidades; lee BridgeRepository.commandHandler sin modificarlo

assistant/ (Fase 3.1 - capa Assistant/Becky: lenguaje natural, no comandos fijos)
  BeckyAssistantModels.kt   InteractionSource (PHONE/WATCH), BeckyAssistantRequest, BeckyAssistantResponse
  IntentResolver.kt         Interfaz + KeywordIntentResolver (placeholder por palabras clave) — seam explícito para un futuro NLU/LLM
  ConversationalEngine.kt   Interfaz + NoOpConversationalEngine — seam explícito para un futuro motor conversacional/IA real
  BeckyResponseFormatter.kt Convierte CapabilityResult en frases habladas en español
  BeckyAssistant.kt         Interfaz + DefaultBeckyAssistant: "cerebro" de BECKY (resuelve intención -> ejecuta capacidad o conversa)

capability/ (Fase 3.1 - capa Command/Capability: comandos como capacidades, no límite de la conversación)
  Capability.kt             Interfaz Capability (id/description/execute) + CapabilityResult + CapabilityDescriptor
  CapabilityRegistry.kt     Interfaz + DefaultCapabilityRegistry: descubre y ejecuta capacidades por id
  WatchCapabilities.kt      Los 6 comandos BLE existentes envueltos como Capability (GetTimeCapability, etc.)
```

## Arquitectura de 4 capas (Fase 3.1)

A partir de esta fase, BECKY **no está limitada a comandos predefinidos**.
La separación completa es:

```
Voice -> Assistant/Becky -> Command/Capability -> BLE/Repository
```

- **Voice** (`voice/`): captura/produce audio (wake word, STT, TTS). No
  sabe nada de comandos ni de qué hace BECKY con el texto reconocido —
  solo le entrega texto libre a `BeckyAssistant` y habla lo que este
  devuelva.
- **Assistant/Becky** (`assistant/`): recibe lenguaje natural
  (`BeckyAssistantRequest`), decide mediante un `IntentResolver` si el
  texto corresponde a alguna capacidad conocida, la ejecuta a través de
  `CapabilityRegistry`, y si no reconoce nada, delega en un
  `ConversationalEngine` (hoy un placeholder `NoOpConversationalEngine`
  que no hace nada — el seam explícito para conectar una IA real más
  adelante). Esta es la capa que impide que GET_TIME/GET_HEART_RATE/etc.
  sean "el límite de la conversación": son solo las primeras capacidades
  registradas, no un caso especial en el código de esta capa.
- **Command/Capability** (`capability/`): cada acción que BECKY puede
  realizar (hoy, las 6 respaldadas por BLE) se expone como un
  `Capability` con `id` + `description` + `execute()`. El
  `CapabilityRegistry` las descubre y ejecuta por id sin saber cómo
  están implementadas — una futura capacidad no-BLE (recordatorio local,
  consulta a una API web, etc.) se registraría exactamente igual.
- **BLE/Repository** (`bluetooth/` + `communication/`): sin cambios de
  comportamiento salvo un único añadido estrictamente necesario (ver
  abajo) para que el reloj pueda iniciar una interacción.

## Dónde implementar el protocolo específico del reloj (Fase 2)

Ver `communication/BluetoothTransport.kt::setActiveCharacteristic()` y
`communication/BeckyCommandHandler.kt`. La app nunca asume un protocolo
fijo: primero se descubren servicios/características genéricos GATT
(`ConnectionActivity`), y luego se selecciona cuál característica usar
para lectura/escritura/notificación real del reloj concreto.

Los comandos de BECKY (`GET_TIME`, `GET_NOTIFICATIONS`, `GET_STEPS`,
`GET_HEART_RATE`, `GET_BATTERY`, `GET_WATCH_STATUS`) están declarados
como constantes en `communication/BeckyCommandHandler.kt::BeckyCommands`.

**Fase 2 (implementada ahora)**: `DefaultBeckyCommandHandler` añade
petición/respuesta real con correlación por `requestId` y timeout
(`sendCommandForResult()`), sobre el mismo `BridgeMessage` de Fase 1
(campo `requestId` añadido, nullable, retrocompatible). Los 6 comandos
base ya tienen wrapper tipado accesible vía `BridgeRepository.commandHandler`:

| Método | Comando | Resultado tipado |
|---|---|---|
| `getTime()` | `GET_TIME` | `WatchTime` (`asWatchTime()`) |
| `getHeartRate()` | `GET_HEART_RATE` | `WatchHeartRate` (`asWatchHeartRate()`) |
| `getNotifications()` | `GET_NOTIFICATIONS` | `WatchNotifications` (`asWatchNotifications()`) |
| `getSteps()` | `GET_STEPS` | `WatchSteps` (`asWatchSteps()`) |
| `getBattery()` | `GET_BATTERY` | `WatchBattery` (`asWatchBattery()`) |
| `getWatchStatus()` | `GET_WATCH_STATUS` | `WatchStatus` (`asWatchStatus()`) |

El *parseo* de cada respuesta (funciones `asWatchXxx()`) usa una
convención best-effort (`value` numérico o `payload` de texto como
fallback) porque el protocolo GATT real del reloj todavía no se conoce.
Cuando se conozca, solo hay que ajustar el `asWatchXxx()` correspondiente
— el envío, la correlación por `requestId` y el timeout no cambian.

## Arquitectura de voz "Hey Becky" (Fase 3 / 3.1)

Paquetes `voice/` + `assistant/` + `capability/`, completamente
desacoplados de la capa BLE: no modifican `BleScanner`, `GattManager`,
`ReconnectionManager` ni `BridgeRepository`. La única línea de código
que toca `BeckyCommandHandler` es el añadido estrictamente necesario
descrito abajo (`incomingWatchRequests`); todo lo demás **lee**
`BridgeRepository.commandHandler` para enviar comandos al reloj, igual
que el panel manual de `DiagnosticsActivity`.

**Pipeline** (todas las etapas son interfaces intercambiables):

```
WakeWordDetector -> SpeechToTextEngine -> BeckyAssistant (IntentResolver + CapabilityRegistry + ConversationalEngine)
  -> TextToSpeechEngine
```

| Etapa | Interfaz | Implementación actual | Notas |
|---|---|---|---|
| Wake word | `WakeWordDetector` | `PlaceholderWakeWordDetector` | Arquitectura lista para "Hey Becky"; no se embebe un motor de keyword-spotting todavía (evita dependencias pesadas). Expone `simulateWakeWord()` para pruebas y como disparador manual temporal. |
| Speech-to-Text | `SpeechToTextEngine` | `AndroidSpeechToTextEngine` | Usa `android.speech.SpeechRecognizer` nativo de Android — sin dependencias nuevas. Requiere permiso `RECORD_AUDIO` (`VoicePermissions`), solicitado en runtime desde `VoiceActivity`. |
| Resolución de intención | `IntentResolver` | `KeywordIntentResolver` | Coincidencia simple por palabras clave en español ("hora", "pulso", "pasos", "batería", "notificaciones", "estado del reloj") sobre la lista de `CapabilityDescriptor` disponibles — deliberadamente NO es una IA/NLU nueva; es el seam explícito para reemplazar por un NLU/LLM real más adelante sin tocar `BeckyAssistant` ni las `Capability`. |
| Ejecución de capacidad | `CapabilityRegistry` | `DefaultCapabilityRegistry` | Ejecuta la `Capability` resuelta (hoy, una de las 6 respaldadas por BLE en `WatchCapabilities.kt`) y devuelve un `CapabilityResult`. |
| Conversación libre (fallback) | `ConversationalEngine` | `NoOpConversationalEngine` | Se invoca solo si `IntentResolver` no reconoce ninguna capacidad. Placeholder que no hace nada — seam explícito para un motor conversacional/IA real futuro. |
| "Cerebro" de BECKY | `BeckyAssistant` | `DefaultBeckyAssistant` | Punto de entrada único: recibe texto libre, decide capacidad vs. conversación, devuelve `BeckyAssistantResponse` ya lista para hablar. |
| Respuesta hablada | — | `BeckyResponseFormatter` | Convierte cada `CapabilityResult` (Success/NotAvailable/Timeout/Failed) en una frase corta en español. |
| Text-to-Speech | `TextToSpeechEngine` | `AndroidTextToSpeechEngine` | Usa `android.speech.tts.TextToSpeech` nativo de Android — sin dependencias nuevas. |
| Orquestación (voz) | `VoiceEngine` | `DefaultVoiceEngine` | Une wake word -> STT -> `BeckyAssistant` -> TTS vía Kotlin Flows, mismo estilo reactivo que `BridgeRepository`. |
| Ensamblado | — | `VoiceRepository` | Singleton (mismo patrón que `BridgeRepository.getInstance()`) que construye capacidades + assistant + pipeline de voz y expone `voiceEngine`. |
| UI | — | `VoiceActivity` (`ui/voice/`) | Pantalla "Hablar con BECKY": pide permiso de micrófono en runtime, botón para hablar, muestra `VoiceState` y la última `BeckyAssistantResponse`. |

**Único añadido a la capa BLE (estrictamente necesario)**: se agregó
`BeckyCommandHandler.incomingWatchRequests: SharedFlow<BridgeMessage>`.
Toda la lógica de correlación por `requestId` sigue exactamente igual;
solo los mensajes SIN `requestId` (es decir, el reloj hablando por su
cuenta) se emiten además por este nuevo flow. Ningún método existente
(`sendCommand`, `sendCommandForResult`, `getTime()`...`getWatchStatus()`)
cambió de comportamiento.

**Interacción iniciada por el reloj**: `VoiceRepository` observa
`incomingWatchRequests`; cualquier mensaje espontáneo del reloj se trata
como lenguaje natural y se envía al mismo `BeckyAssistant` que usa la
voz del teléfono (con `InteractionSource.WATCH`), respondiendo hoy por
TTS del teléfono. Enviar esa misma respuesta de vuelta al reloj por BLE
queda documentado como próximo paso (no implementado todavía).

**Estado de esta fase**: arquitectura de 4 capas implementada,
compilada y verificada end-to-end (Debug + Release), con una pantalla
(`VoiceActivity`) que ya permite hablar con BECKY y usar lenguaje
natural para invocar las 6 capacidades existentes. Pendiente para fases
posteriores: motor de wake-word local real, `IntentResolver`/
`ConversationalEngine` con NLU/LLM real, envío de la respuesta de vuelta
al reloj por BLE, extensión a Wear OS.

**Extensión futura a Wear OS**: como cada etapa es una interfaz, un
futuro módulo `wear/` podría implementar las mismas interfaces
(`WakeWordDetector`, `SpeechToTextEngine`, `TextToSpeechEngine`) usando
el micrófono/altavoz del reloj, enrutando el texto reconocido a un
`BeckyAssistant` local en el reloj o de vuelta al teléfono por el bridge
BLE ya existente — sin cambiar el diseño de orquestación de `VoiceEngine`
ni de `BeckyAssistant`.

## Cómo compilar

### Dentro de este sandbox / Termux / cualquier Linux con JDK 17 y Android SDK

```bash
cd BeckyBridge
./gradlew assembleDebug      # APK de depuración
./gradlew assembleRelease    # APK firmado, listo para instalar
```

- Debug APK: `app/build/outputs/apk/debug/BECKY-BRIDGE-debug.apk`
- Release APK: `app/build/outputs/apk/release/BECKY-BRIDGE-release.apk`

### En Android Studio

Abrir la carpeta del proyecto directamente; Android Studio detecta el
Gradle Wrapper (`gradlew`) y sincroniza automáticamente. Ejecutar con
▶️ (Run) o generar el APK desde *Build > Build Bundle(s) / APK(s)*.

### Requisitos

- JDK 17 (recomendado: **JDK completo**, no solo JRE — se necesita `jlink`)
- Android SDK: platform 34, build-tools 34.0.0 (o dejar que Android
  Studio los instale automáticamente)
- Gradle 8.9 (gestionado por el wrapper `gradlew`, no requiere
  instalación manual salvo para regenerar el wrapper mismo)

## Cómo modificar el proyecto

- **Añadir un nuevo comando BECKY → reloj**: añade la constante en
  `BeckyCommands` (`communication/BeckyCommandHandler.kt`) y su lógica
  en una futura implementación de `BeckyCommandHandler`.
- **Añadir un nuevo transporte** (p. ej. Wi-Fi real): implementa la
  interfaz `CommunicationTransport` (ver `WifiTransport.kt` como
  esqueleto) y regístralo en `BridgeRepository`.
- **Cambiar la UI**: cada pantalla es una `Activity` independiente en
  `ui/<pantalla>/`; no hay una `MainActivity` monolítica.
- **Ajustar permisos**: `bluetooth/PermissionsHelper.kt` centraliza qué
  permisos se piden según la versión de Android.
- **Ver/exportar logs internos**: `logging/BeckyLogger.kt` es un
  singleton con `StateFlow<List<LogEntry>>`; `LogsActivity` lo
  visualiza y `DiagnosticsActivity` permite exportarlo como texto.

## Permisos usados (y por qué)

| Permiso | Por qué |
|---|---|
| `BLUETOOTH_SCAN` (Android 12+, `neverForLocation`) | Escanear dispositivos BLE cercanos |
| `BLUETOOTH_CONNECT` (Android 12+) | Conectar/leer/escribir GATT |
| `BLUETOOTH_ADVERTISE` (Android 12+) | Reservado para futuro (no usado activamente en Fase 1) |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` (maxSdk 30) | Compatibilidad con Android ≤11 |
| `ACCESS_FINE_LOCATION` (maxSdk 30) | Requerido por el sistema para escaneo BLE en Android ≤11 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Mantener la conexión BLE activa en segundo plano |
| `POST_NOTIFICATIONS` (Android 13+) | Mostrar la notificación persistente de estado de conexión |

No se solicita ningún permiso fuera de esta lista. No se almacenan
contraseñas ni claves privadas. No se transmiten datos personales sin
consentimiento explícito del usuario.

## Dependencias principales

- `androidx.core:core-ktx`, `androidx.appcompat`, `com.google.android.material`
- `androidx.constraintlayout`
- `androidx.lifecycle:*` (runtime-ktx, viewmodel-ktx, livedata-ktx, service)
- `androidx.recyclerview`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` / `-android`
- `org.jetbrains.kotlinx:kotlinx-serialization-json` (protocolo `BridgeMessage`)
- `androidx.datastore:datastore-preferences`
- Testing: `junit`, `androidx.test.ext:junit`, `androidx.test.espresso:espresso-core`

## Hoja de ruta (fases)

- **Fase 1**: puente Bluetooth/BLE sólido — escaneo, conexión, GATT,
  mensajería interna, logging, diagnóstico, servicio en primer plano.
  ✅ Completada y verificada.
- **Fase 2**: comunicación BECKY <-> reloj con petición/respuesta
  correlacionada y timeout. ✅ Completada: los 6 comandos base
  (`GET_TIME`, `GET_HEART_RATE`, `GET_NOTIFICATIONS`, `GET_STEPS`,
  `GET_BATTERY`, `GET_WATCH_STATUS`) implementados end-to-end con
  wrapper tipado, más panel manual de pruebas en `DiagnosticsActivity`.
- **Fase 3**: capa de voz "Hey Becky" — arquitectura desacoplada
  (wake word / STT / TTS), usando únicamente APIs nativas de Android.
  ✅ Completada y verificada.
- **Fase 3.1 (en curso)**: arquitectura de 4 capas
  `Voice -> Assistant/Becky -> Command/Capability -> BLE/Repository`.
  BECKY ya no está limitada a comandos predefinidos: recibe lenguaje
  natural, decide qué capacidad necesita (o conversa si no reconoce
  nada) y responde por TTS. Los 6 comandos BLE existentes son ahora
  capacidades (`capability/WatchCapabilities.kt`), no un caso especial.
  Pantalla `VoiceActivity` para hablar con BECKY desde el teléfono.
  Interacción iniciada por el reloj soportada (texto -> BeckyAssistant
  -> TTS del teléfono). ✅ Arquitectura implementada, compilada y
  verificada. Pendiente: `IntentResolver`/`ConversationalEngine` con
  NLU/LLM real, motor de wake-word local real, envío de la respuesta
  de vuelta al reloj por BLE, extensión a Wear OS.
- **Fase 4**: avatar animado en el reloj (payloads IMAGE/ANIMATION).
- **Fase 5**: sistema de asistente personal completo.

## Próximos pasos recomendados

1. Instalar `BECKY-BRIDGE-debug.apk` en un teléfono real (Android 8+) y
   probar escaneo/conexión contra el reloj objetivo real.
2. Ajustar `BluetoothTransport.setActiveCharacteristic()` con el UUID de
   característica real del reloj una vez conocido su servicio GATT.
3. Elegir e integrar un motor de wake-word local real (reemplazando
   `PlaceholderWakeWordDetector`) y disparar `VoiceEngine.start()` desde
   una Activity o servicio en primer plano.
4. Solicitar el permiso `RECORD_AUDIO` en runtime desde la UI antes de
   usar `SpeechToTextEngine` (hoy solo está declarado en el Manifest).
