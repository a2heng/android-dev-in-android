# AndroidApp

Proyecto Android de ejemplo, listo para compilar en el propio telefono con
Adia (JDK 21 + SDK 35 + Gradle 9.8.1).

```sh
bash ~/.config/opencode/skills/android-build/scripts/build.sh . debug
```

El APK sale en `app/build/outputs/apk/debug/app-debug.apk`.

Es un proyecto Java sin dependencias externas para que la primera compilacion
funcione sin red. Anade tus librerias y usa el skill `android-lib-cache` para
que se descarguen una sola vez.
