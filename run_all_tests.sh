#!/bin/bash

set -ex

cd -- "$(dirname "$0")"

mvn install -Prun-its
