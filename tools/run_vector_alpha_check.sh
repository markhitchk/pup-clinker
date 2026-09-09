#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
kotlinc "$ROOT/app/src/main/java/com/harleytg/puppyclicker/PuppyVectorAlpha.kt" "$ROOT/tools/VectorAlphaCheck.kt" -include-runtime -d "$WORK/vector-alpha-check.jar"
java -jar "$WORK/vector-alpha-check.jar"
