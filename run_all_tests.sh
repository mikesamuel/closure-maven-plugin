#!/bin/bash

set -ex

cd -- "$(dirname "$0")"

mvn -Djava.awt.headless=true install

mvn -Djava.awt.headless=true verify -Prun-its
