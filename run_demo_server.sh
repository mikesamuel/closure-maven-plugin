#!/bin/bash

set -ex

cd -- "$(dirname "$0")"


# separate flags meant for mvn build from those meant for the demo server
MVN_FLAGS=""

case "$1" in
    "clean" | "-X")
        MVN_FLAGS="$MVN_FLAGS $1"
        shift
        ;;
esac


# pick a demo target directory outside the main project tree
export DEMO_TARGET_DIR="$TMPDIR/closure-demo-target"

# build the plugin
mvn install

# build the demo
pushd "$PWD/plugin/src/it/demo/"
mvn -PalternateBuildDir -Dalt.build.dir="$DEMO_TARGET_DIR" $MVN_FLAGS verify
popd

# execute the jar
exec java -jar "$DEMO_TARGET_DIR"/demo-*.jar "$@"
