#!/bin/sh

set -eu

MVN_VERSION="3.9.9"
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MVN_DIR="$BASE_DIR/.mvn/apache-maven-$MVN_VERSION"
MVN_BIN="$MVN_DIR/bin/mvn"
ARCHIVE="$BASE_DIR/.mvn/apache-maven-$MVN_VERSION-bin.tar.gz"
URL="https://archive.apache.org/dist/maven/maven-3/$MVN_VERSION/binaries/apache-maven-$MVN_VERSION-bin.tar.gz"

if [ ! -x "$MVN_BIN" ]; then
  mkdir -p "$BASE_DIR/.mvn"
  if [ ! -f "$ARCHIVE" ]; then
    echo "Downloading Apache Maven $MVN_VERSION..."
    curl -fsSL "$URL" -o "$ARCHIVE"
  fi
  tar -xzf "$ARCHIVE" -C "$BASE_DIR/.mvn"
fi

exec "$MVN_BIN" "$@"
