# build.sh
#!/bin/bash
set -e

echo "→ Installing Java 21..."
apt-get install -y openjdk-21-jdk-headless 2>/dev/null || true
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

echo "→ Java version:"
java -version

echo "→ Installing npm deps..."
npm ci

echo "→ Building ClojureScript..."
npx shadow-cljs release app
