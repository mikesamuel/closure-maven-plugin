#!/bin/bash

set -ex

cd -- "$(dirname "$0")"

mvn -Djava.awt.headless=true install -Prun-its
