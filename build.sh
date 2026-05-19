#!/bin/bash
set -e

echo "→ Installing Java 21..."
apt-get install -y openjdk-21-jdk-headless 2>/dev/null || true

echo "→ Locating Java..."
JAVA_BIN=$(which java 2>/dev/null || find /usr -name "java" -type f 2>/dev/null | head -1)
if [ -z "$JAVA_BIN" ]; then
  echo "ERROR: java not found after install"
  find /usr/lib/jvm -name "java" 2>/dev/null || echo "No JVM found"
  exit 1
fi

export JAVA_HOME=$(dirname $(dirname $(readlink -f $JAVA_BIN)))
export PATH=$JAVA_HOME/bin:$PATH

echo "→ Java found at: $JAVA_BIN"
echo "→ JAVA_HOME: $JAVA_HOME"
java -version

echo "→ Building ClojureScript..."
npx shadow-cljs release app

echo "→ Done. Output in public/"