#!/bin/sh
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
python3 "$DIR/tests/test_calculator.py"
