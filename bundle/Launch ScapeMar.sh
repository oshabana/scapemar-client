#!/bin/sh
set -eu
cd "$(dirname "$0")"
exec ./runtime/bin/java -cp . ScapeMarLauncher
