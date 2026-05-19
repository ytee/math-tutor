#!/bin/bash
set -e

JDK_DIR="$HOME/.jdk"

if [ ! -f "$JDK_DIR/bin/java" ]; then
  echo "→ Downloading JDK 21..."
  mkdir -p "$JDK_DIR"
  curl -sL "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_linux_hotspot_21.0.5_11.tar.gz" \
    | tar -xz -C "$JDK_DIR" --strip-components=1
  echo "→ JDK downloaded."
else
  echo "→ JDK already cached."
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

echo "→ Java version:"
java -version

echo "→ Building ClojureScript..."
npx shadow-cljs release app

echo "→ Done. Output in public/"