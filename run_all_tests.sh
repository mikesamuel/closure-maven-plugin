#!/bin/bash

set -ex

cd -- "$(dirname "$0")"

mvn install

for f in src/it/*; do
    if [ -e "$f"/pom.xml ]; then
        cd "$f"
        function copy_logs() {
          mkdir -p target
          # Potential race condition over tmpfile
          cp "$TMPDIR/maven-it-log.txt" target/log.txt
        }

        mvn clean verify -X >& "$TMPDIR/maven-it-log.txt" \
        || (copy_logs; false)

        copy_logs
    fi
done
