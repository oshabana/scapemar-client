#!/bin/sh
set -eu
cd "$(dirname "$0")"
out=${1:-dist}
mkdir -p "$out" dist/runtimes
out=$(cd "$out" && pwd)
cache=$(cd dist/runtimes && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT HUP INT TERM
curl -fLsS --retry 3 -o "$tmp/rsprox-launcher.jar" \
  https://github.com/blurite/rsprox/releases/download/v1.0/rsprox-launcher.jar
actual=$(shasum -a 256 "$tmp/rsprox-launcher.jar" | cut -d ' ' -f 1)
expected=44c60c78809e83a39173d9ba780928091f89defc17acd90ad81e44b85048ce11
if [ "$actual" != "$expected" ]; then
  echo 'RSProx download hash does not match the reviewed release.' >&2
  exit 1
fi
version=${VERSION:-$(git describe --tags --abbrev=0 2>/dev/null | sed 's/^v//')}
case "$version" in
  [0-9]*.[0-9]*) ;;
  *) echo 'Set VERSION to a number like 0.5.0, or tag the release first.' >&2; exit 1 ;;
esac
javac --release 21 -d "$tmp" bundle/ScapeMarLauncher.java bundle/ScapeMarAuto.java
printf 'Premain-Class: ScapeMarAuto\n' > "$tmp/auto.mf"
jar --create --file "$tmp/ScapeMar-Auto.jar" --manifest "$tmp/auto.mf" -C "$tmp" ScapeMarAuto.class
printf '%s\n' "$version" > "$tmp/version.txt"
./build-login-plugin.sh "$tmp/ScapeMar-Login.jar" > /dev/null

runtime() {
  url=$(curl -fsS -o /dev/null -w '%{redirect_url}' \
    "https://api.adoptium.net/v3/binary/latest/21/ga/$1/$2/jdk/hotspot/normal/eclipse")
  file="$cache/$(basename "$url")"
  if [ ! -f "$file" ]; then
    curl -fLsS --retry 3 -o "$file.part" "$url"
    want=$(curl -fLsS --retry 3 "$url.sha256.txt" | cut -d ' ' -f 1)
    got=$(shasum -a 256 "$file.part" | cut -d ' ' -f 1)
    if [ "$want" != "$got" ]; then
      echo "Java runtime download hash does not match Adoptium's: $url" >&2
      exit 1
    fi
    mv "$file.part" "$file"
  fi
  rm -rf "$tmp/jre" && mkdir "$tmp/jre"
  case "$file" in
    *.zip) unzip -q "$file" -d "$tmp/jre" ;;
    *) tar xzf "$file" -C "$tmp/jre" ;;
  esac
  mv "$tmp"/jre/* "$3"
}

common() {
  mkdir -p "$1"
  cp "$tmp/ScapeMarLauncher.class" "$tmp/rsprox-launcher.jar" "$tmp/ScapeMar-Login.jar" \
    "$tmp/ScapeMar-Auto.jar" "$tmp/version.txt" \
    proxy-targets.yaml bundle/RSProx-LICENSE.txt "$1/"
}

app="$tmp/mac/ScapeMar.app"
mkdir -p "$app/Contents/MacOS"
common "$app/Contents/Resources"
cp bundle/mac/Info.plist "$app/Contents/"
cp bundle/mac/ScapeMar "$app/Contents/MacOS/"
cp bundle/icons/ScapeMar.icns "$app/Contents/Resources/"
runtime mac aarch64 "$app/Contents/Resources/runtime-arm64"
runtime mac x64 "$app/Contents/Resources/runtime-x64"
codesign --force -s - "$app"
ln -s /Applications "$tmp/mac/Applications"
rm -f "$out/ScapeMar-macos.dmg"
hdiutil create -quiet -volname ScapeMar -srcfolder "$tmp/mac" -format UDZO "$out/ScapeMar-macos.dmg"

win="$tmp/ScapeMar-windows"
common "$win"
cp bundle/icons/ScapeMar.ico bundle/README.txt 'bundle/Launch ScapeMar.bat' "$win/"
runtime windows x64 "$win/runtime"
makensis -V2 -DOUTFILE="$out/ScapeMar-windows-setup.exe" -DSOURCE="$win" \
  -DICON="$(pwd)/bundle/icons/ScapeMar.ico" bundle/windows/installer.nsi
rm -f "$out/ScapeMar-windows.zip"
(cd "$tmp" && zip -q -r "$out/ScapeMar-windows.zip" ScapeMar-windows)

linux="$tmp/ScapeMar-linux"
common "$linux"
cp bundle/README.txt 'bundle/Launch ScapeMar.sh' "$linux/"
runtime linux x64 "$linux/runtime"
rm -f "$out/ScapeMar-linux.zip"
(cd "$tmp" && zip -q -r -y "$out/ScapeMar-linux.zip" ScapeMar-linux)

(cd "$out" && shasum -a 256 ScapeMar-macos.dmg ScapeMar-windows-setup.exe ScapeMar-windows.zip \
  ScapeMar-linux.zip > SHA256SUMS.txt)

cp proxy-targets.yaml "$tmp/ScapeMar-Login.jar" "$out/"
printf '{"version":"%s","proxy-targets.yaml":"%s","ScapeMar-Login.jar":"%s"}\n' "$version" \
  "$(shasum -a 256 proxy-targets.yaml | cut -d ' ' -f 1)" \
  "$(shasum -a 256 "$tmp/ScapeMar-Login.jar" | cut -d ' ' -f 1)" > "$out/update.json"
