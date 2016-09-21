#!/bin/bash

set -ex

cd -- "$(dirname "$0")"


# separate flags meant for mvn build from those meant for the demo server
MVN_PLUGIN_FLAGS=""
MVN_DEMO_FLAGS=""

while true; do
    case "$1" in
        "fast")
            MVN_PLUGIN_FLAGS="$MVN_PLUGIN_FLAGS -DskipTests=true"
            shift
            ;;
        "clean" | "-X" | "-o")
            MVN_PLUGIN_FLAGS="$MVN_PLUGIN_FLAGS $1"
            MVN_DEMO_FLAGS="$MVN_DEMO_FLAGS $1"
            shift
            ;;
        *)
            break
            ;;
    esac
done


# pick a demo target directory outside the main project tree
export DEMO_TARGET_DIR="$TMPDIR/closure-demo-target"

# build the plugin
mvn install -Djava.awt.headless=true $MVN_PLUGIN_FLAGS

# build the demo
pushd "$PWD/plugin/src/it/demo/"
mvn \
    -PalternateBuildDir -Dalt.build.dir="$DEMO_TARGET_DIR" \
    -Djava.awt.headless=true \
    $MVN_DEMO_FLAGS verify
popd

# execute the jar
exec java -jar "$DEMO_TARGET_DIR"/demo-*.jar "$@"
