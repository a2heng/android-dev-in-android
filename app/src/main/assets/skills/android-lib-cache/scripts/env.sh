#!/data/data/com.termux/files/usr/bin/bash
# Shared cache paths for Adia. Source it: . env.sh
# HOME is the app-private root, so these survive app updates.

: "${HOME:?HOME is not set}"

export ADIA_TOOLCHAIN="${ADIA_TOOLCHAIN:-$HOME/toolchain}"
export ADIA_GRADLE_HOME="${ADIA_GRADLE_HOME:-$ADIA_TOOLCHAIN/gradle-home}"
export ADIA_M2="${ADIA_M2:-$ADIA_TOOLCHAIN/m2}"

# Gradle, Maven and common JVM tools all read these.
export GRADLE_USER_HOME="$ADIA_GRADLE_HOME"
case " ${MAVEN_OPTS:-} " in
  *" -Dmaven.repo.local="*) ;;
  *) export MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=$ADIA_M2" ;;
esac

mkdir -p "$ADIA_M2" "$ADIA_GRADLE_HOME" 2>/dev/null || true

# Print once so the agent can see the canonical paths.
if [ "${ADIA_QUIET:-0}" != "1" ]; then
  echo "[lib-cache] HOME=$HOME"
  echo "[lib-cache] GRADLE_USER_HOME=$GRADLE_USER_HOME"
  echo "[lib-cache] ADIA_M2=$ADIA_M2"
fi
