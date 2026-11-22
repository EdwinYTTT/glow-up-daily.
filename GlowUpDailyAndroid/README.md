# Glow Up Daily — Android

Aplicación Android nativa en Kotlin + Jetpack Compose, diseñada como espacio personal y local-first.

## Qué incluye

- Asistente de configuración inicial con perfil, objetivos, disponibilidad y preferencias.
- Panel diario con hábitos marcables, porcentaje de avance, nivel, XP y racha.
- Face & Care: rutinas básicas de mañana/noche, notas de piel y cabello, cámara/galería, fotos privadas y consentimiento explícito antes de enviar una imagen a la IA.
- Training: categorías de movimiento, ejercicios corporales de nivel general, temporizador de descanso y registro de sesiones.
- Nutrition: registro manual de comidas y porciones, nutrientes opcionales, fotografías y un historial reciente.
- My Evolution: métricas calculadas desde las actividades registradas.
- Levels & Badges: niveles e insignias desbloqueadas según datos reales.
- Calendario mensual de actividad.
- Perfil, tema claro/oscuro, recordatorios locales, exportación JSON y borrado de datos.
- Almacenamiento privado en el dispositivo con `SharedPreferences` y fotos en el directorio privado de la app.

La aplicación no crea objetivos de pérdida de peso, restricciones calóricas, puntuaciones de belleza ni diagnósticos médicos. El análisis por fotografía está deshabilitado hasta configurar un backend. No se generan resultados de IA de muestra.

## Abrir y generar el APK

1. Instala Android Studio en un ordenador y asegúrate de tener instalado **Android SDK 35** y **JDK 17**.
2. Descomprime este proyecto y abre la carpeta `GlowUpDailyAndroid` desde Android Studio (`File > Open`).
3. Espera a que Gradle sincronice y descargue las dependencias.
4. Selecciona `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
5. Android Studio generará el APK de depuración en `app/build/outputs/apk/debug/app-debug.apk`.
6. Pasa ese APK al móvil Android e instálalo. En el primer inicio completa el cuestionario.

También puedes compilar desde una terminal en Windows:

```bat
gradlew.bat assembleDebug
```

En macOS/Linux:

```bash
./gradlew assembleDebug
```

El pequeño lanzador incluido descarga Gradle 8.9 si todavía no está disponible. Este entorno de creación no dispone de Android SDK ni de Gradle instalado, por lo que aquí se entrega el proyecto fuente y no un APK compilado. Android Studio realizará la sincronización y compilación en tu PC.

## Almacenamiento y privacidad

- Los datos se guardan en el espacio privado de la aplicación en ese teléfono.
- Las fotos de cámara/galería se copian a `files/images` dentro del sandbox privado y se normalizan a JPEG.
- No hay cuenta, perfiles públicos, seguidores ni sincronización en la nube.
- Exportar crea una copia JSON que tú decides dónde compartir.
- `Eliminar todos mis datos` borra el perfil, registros, preferencias y carpeta privada de imágenes.
- Android puede borrar los datos de la app al desinstalarla. Exporta una copia antes si deseas conservarlos.

## Recordatorios

Los avisos se programan con `AlarmManager.setInexactRepeating`. Android 13 o superior solicita permiso de notificaciones. El sistema puede retrasar o agrupar avisos por ahorro de batería; por eso no se promete una hora exacta.

## Activar análisis real con IA (opcional)

El análisis facial/estilo y de alimentos requiere un servidor. La app nunca contiene la clave privada del proveedor.

### 1. Preparar el backend

```bash
cd backend
npm install
cp .env.example .env
```

Edita `backend/.env` y establece `OPENAI_API_KEY` en el servidor. Puedes cambiar `OPENAI_MODEL`. No compartas el `.env` ni lo subas a Git.

Inicia el servicio:

```bash
npm start
```

Comprueba `GET /health`. El servidor acepta `POST /api/analyze/face` y `POST /api/analyze/food` con una imagen multipart llamada `image`.

### 2. Configurar la dirección en Android

En `gradle.properties`, establece `AI_BACKEND_URL` con la URL HTTPS pública de tu backend:

```properties
AI_BACKEND_URL=https://tu-dominio.example
```

Vuelve a sincronizar y compilar la app. Para un móvil físico, el servidor debe ser accesible desde ese teléfono y usar HTTPS válido. No uses `localhost` como dirección del backend en el teléfono: `localhost` apunta al propio teléfono.

La app muestra el error de conexión y conserva las demás secciones si el backend no está disponible. Antes de enviar una foto aparece una casilla de consentimiento; el envío solo ocurre después de marcarla y pulsar el botón de análisis.

### 3. Limitaciones del análisis

- Las imágenes faciales solo reciben orientación general, sin puntuaciones de atractivo, identificación o diagnóstico dermatológico.
- Las imágenes de comida pueden producir estimaciones inciertas; el usuario debe corregir alimentos y porciones.
- Una fotografía no permite conocer con precisión ingredientes, cantidades ni preparación.
- Para menores de 18 años no se ofrecen dietas restrictivas ni metas para perder peso.

## Tecnologías

- Kotlin
- Android SDK / Jetpack Compose / Material 3
- AndroidX Activity + Core
- Gson para serialización local
- Backend opcional Node.js + Express + Multer

## Estructura

```text
GlowUpDailyAndroid/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/glowupdaily/app/
│       │   ├── MainActivity.kt
│       │   ├── Models.kt
│       │   ├── AiService.kt
│       │   └── ReminderReceiver.kt
│       └── res/xml/file_paths.xml
├── backend/
│   ├── index.js
│   ├── package.json
│   └── .env.example
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Nota de estado

El proyecto implementa las funciones locales descritas. La IA necesita configurar y desplegar el backend. Las notificaciones dependen de los permisos y de las políticas de ahorro de batería de Android. No se ha podido ejecutar un build de APK en este entorno porque no tiene instalado Android SDK/Gradle.

## Generar el APK en la nube (sin instalar Android Studio)

Este repositorio incluye `.github/workflows/build-apk.yml` para compilar la aplicación con GitHub Actions.

1. Crea un repositorio **privado** en GitHub.
2. Sube el contenido de esta carpeta al repositorio (incluidos `.github` y `gradlew`).
3. Al terminar la primera subida, abre la pestaña **Actions** del repositorio.
4. Entra en la ejecución llamada **Build Glow Up Daily APK** y espera a que termine correctamente.
5. En la sección **Artifacts**, descarga `GlowUpDaily-debug-apk.zip` y extrae `app-debug.apk`.
6. Transfiere el APK al teléfono e instálalo. No hace falta iniciar un emulador en el PC.

La compilación en la nube necesita que GitHub Actions esté permitido para ese repositorio. El APK generado es de depuración y sirve para instalarlo y probarlo; para distribuirlo formalmente se debe generar una versión firmada de lanzamiento.
