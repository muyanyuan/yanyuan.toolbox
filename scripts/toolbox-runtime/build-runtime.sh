#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
LOCK_FILE="$ROOT_DIR/scripts/toolbox-runtime/sources.lock"
ABI="${2:-arm64-v8a}"
OUT_DIR="${1:-$ROOT_DIR/out/toolbox-runtime/$ABI}"
TOOLCHAIN_LOCK="$ROOT_DIR/scripts/toolbox-runtime/toolchain.lock"
LOCKED_API="$(awk -F= '$1 == "android_api" { print $2; exit }' "$TOOLCHAIN_LOCK")"
LOCKED_NDK="$(awk -F= '$1 == "ndk_revision" { print $2; exit }' "$TOOLCHAIN_LOCK")"
API="${ANDROID_API_LEVEL:-$LOCKED_API}"
EXPECTED_NDK_VERSION="${TOOLBOX_NDK_VERSION:-$LOCKED_NDK}"
[[ "$API" == "$LOCKED_API" ]] || { printf 'Android API must match toolchain.lock (%s).\\n' "$LOCKED_API" >&2; exit 2; }
VERSION="${TOOLBOX_RUNTIME_VERSION:-0.1.0-foundation}"
CACHE_DIR="${TOOLBOX_SOURCE_CACHE:-$ROOT_DIR/.cache/toolbox-runtime/sources}"
WORK_DIR="$ROOT_DIR/.cache/toolbox-runtime/build/$ABI"

case "$ABI" in
  arm64-v8a) TARGET="aarch64-linux-android"; BUILD_TRIPLE="aarch64-linux-android" ;;
  armeabi-v7a) TARGET="armv7a-linux-androideabi"; BUILD_TRIPLE="armv7a-linux-androideabi" ;;
  x86_64) TARGET="x86_64-linux-android"; BUILD_TRIPLE="x86_64-linux-android" ;;
  *) printf 'Unsupported ABI: %s\n' "$ABI" >&2; exit 2 ;;
esac

case "$(uname -s)" in
  Linux*) ;;
  *) printf 'Build Bash + BusyBox inside Linux/WSL; Windows Git Bash is unsupported.\n' >&2; exit 2 ;;
esac

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the pinned Android NDK directory}"
command -v curl >/dev/null || { printf 'curl is required.\n' >&2; exit 2; }
command -v sha256sum >/dev/null || { printf 'sha256sum is required.\n' >&2; exit 2; }
command -v make >/dev/null || { printf 'make is required.\n' >&2; exit 2; }
command -v tar >/dev/null || { printf 'tar is required.\n' >&2; exit 2; }
command -v zip >/dev/null || { printf 'zip is required.\n' >&2; exit 2; }

NDK_META="$ANDROID_NDK_HOME/source.properties"
[[ -f "$NDK_META" ]] || { printf 'NDK source.properties missing: %s\n' "$NDK_META" >&2; exit 2; }
NDK_VERSION="$(awk -F'= *' '/^Pkg.Revision/ { print $2; exit }' "$NDK_META")"
[[ "$NDK_VERSION" == "$EXPECTED_NDK_VERSION" ]] || {
  printf 'NDK version mismatch: expected %s, got %s\n' "$EXPECTED_NDK_VERSION" "${NDK_VERSION:-unknown}" >&2
  exit 2
}
TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
[[ -x "$TOOLCHAIN/clang" ]] || { printf 'NDK Linux LLVM toolchain not found: %s\n' "$TOOLCHAIN" >&2; exit 2; }
CC="$TOOLCHAIN/${TARGET}${API}-clang"
AR="$TOOLCHAIN/llvm-ar"
RANLIB="$TOOLCHAIN/llvm-ranlib"
STRIP="$TOOLCHAIN/llvm-strip"
for tool in "$CC" "$AR" "$RANLIB" "$STRIP"; do
  [[ -x "$tool" ]] || { printf 'Required NDK tool not found: %s\n' "$tool" >&2; exit 2; }
done

mkdir -p "$CACHE_DIR" "$WORK_DIR"

fetch_source() {
  local name="$1" version="$2" url="$3" expected="$4"
  local archive="$CACHE_DIR/$name-$version.tar"
  [[ "$expected" =~ ^[0-9a-fA-F]{64}$ ]] || { printf 'Invalid SHA-256 pin for %s\n' "$name" >&2; exit 3; }
  if [[ ! -f "$archive" ]]; then
    curl --fail --location --retry 3 --retry-all-errors --connect-timeout 20 --output "$archive.part" "$url"
    mv "$archive.part" "$archive"
  fi
  local actual
  actual="$(sha256sum "$archive" | cut -d' ' -f1)"
  if [[ "${actual,,}" != "${expected,,}" ]]; then
    rm -f "$archive" "$archive.part"
    printf 'SHA-256 mismatch for %s: expected %s, got %s\n' "$name" "$expected" "$actual" >&2
    exit 3
  fi
  printf '%s\n' "$archive"
}

source_row() {
  local name="$1"
  awk -F'|' -v wanted="$name" '$1 == wanted { print; found++ } END { if (found != 1) exit 1 }' "$LOCK_FILE"
}
read_source() {
  local row="$1"
  IFS='|' read -r SOURCE_NAME SOURCE_VERSION SOURCE_URL SOURCE_SHA SOURCE_LICENSE SOURCE_NOTES <<< "$row"
}

read_source "$(source_row bash)"
BASH_ARCHIVE="$(fetch_source "$SOURCE_NAME" "$SOURCE_VERSION" "$SOURCE_URL" "$SOURCE_SHA")"
BASH_VERSION="$SOURCE_VERSION"
BASH_LICENSE="$SOURCE_LICENSE"
read_source "$(source_row busybox)"
BUSYBOX_ARCHIVE="$(fetch_source "$SOURCE_NAME" "$SOURCE_VERSION" "$SOURCE_URL" "$SOURCE_SHA")"
BUSYBOX_VERSION="$SOURCE_VERSION"
BUSYBOX_LICENSE="$SOURCE_LICENSE"

rm -rf "$WORK_DIR/src" "$WORK_DIR/stage" "$WORK_DIR/package"
mkdir -p "$WORK_DIR/src/bash" "$WORK_DIR/src/busybox" "$WORK_DIR/stage" "$WORK_DIR/package"
tar -xf "$BASH_ARCHIVE" -C "$WORK_DIR/src/bash" --strip-components=1
tar -xf "$BUSYBOX_ARCHIVE" -C "$WORK_DIR/src/busybox" --strip-components=1
python3 - "$WORK_DIR/src/bash/builtins/printf.def" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
old = '''#if defined (HANDLE_MULTIBYTE)\n  char mbch[25];\t\t/* 25 > MB_LEN_MAX, plus can handle 4-byte UTF-8 and large Unicode characters*/\n  int mbind, mblen, mb_cur_max;\n#endif'''
new = '''int mb_cur_max;\n#if defined (HANDLE_MULTIBYTE)\n  char mbch[25];\t\t/* 25 > MB_LEN_MAX, plus can handle 4-byte UTF-8 and large Unicode characters*/\n  int mbind, mblen;\n#endif'''
if old not in text:
    raise SystemExit('Bash printf multibyte patch context did not match')
path.write_text(text.replace(old, new, 1))
PY
STAGE="$WORK_DIR/stage"

export CC AR RANLIB STRIP
cat > "$WORK_DIR/mblen-compat.h" <<'EOF'
#include <stddef.h>
#include <wchar.h>
static inline int mblen(const char *text, size_t size) {
  mbstate_t state = {0};
  size_t result = mbrlen(text, size, &state);
  return result == (size_t)-1 || result == (size_t)-2 ? -1 : (int)result;
}
EOF
export LDFLAGS=""

(
  cd "$WORK_DIR/src/bash"
  export CFLAGS="-O2 -fPIC -D_GNU_SOURCE -DHANDLE_MULTIBYTE -include $WORK_DIR/mblen-compat.h"
  ac_cv_func_dprintf=yes ac_cv_func_gettimeofday=yes ac_cv_func_mbrlen=yes ac_cv_func_mbrtowc=yes ./configure --host="$BUILD_TRIPLE" --build="$(./support/config.guess)" --prefix=/ --without-bash-malloc --disable-nls --disable-readline
  make -j"${JOBS:-2}"
  make install DESTDIR="$STAGE"
)

(
  cd "$WORK_DIR/src/busybox"
  ARCH="$(case "$ABI" in arm64-v8a) printf arm64 ;; armeabi-v7a) printf arm ;; x86_64) printf x86_64 ;; esac)"
  make distclean ARCH="$ARCH"
  make defconfig ARCH="$ARCH" CROSS_COMPILE= CC="$CC" </dev/null
  sed -i -e 's/^CONFIG_LOADFONT=y/# CONFIG_LOADFONT is not set/' \
    -e 's/^CONFIG_SETFONT=y/# CONFIG_SETFONT is not set/' \
    -e 's/^CONFIG_HOSTID=y/# CONFIG_HOSTID is not set/' \
    -e 's/^CONFIG_LOGNAME=y/# CONFIG_LOGNAME is not set/' \
    -e 's/^CONFIG_SU=y/# CONFIG_SU is not set/' \
    -e 's/^CONFIG_SULOGIN=y/# CONFIG_SULOGIN is not set/' \
    -e 's/^CONFIG_LOGIN=y/# CONFIG_LOGIN is not set/' \
    -e 's/^CONFIG_GETTY=y/# CONFIG_GETTY is not set/' \
    -e 's/^CONFIG_SHA1_HWACCEL=y/# CONFIG_SHA1_HWACCEL is not set/' \
    -e 's/^CONFIG_SHA256_HWACCEL=y/# CONFIG_SHA256_HWACCEL is not set/' \
    -e 's/^CONFIG_FEATURE_SYNC_FANCY=y/# CONFIG_FEATURE_SYNC_FANCY is not set/' \
    -e 's/^CONFIG_FEATURE_WTMP=y/# CONFIG_FEATURE_WTMP is not set/' \
    -e 's/^CONFIG_ADJTIMEX=y/# CONFIG_ADJTIMEX is not set/' \
    -e 's/^CONFIG_CONSPY=y/# CONFIG_CONSPY is not set/' \
    -e 's/^CONFIG_ETHER_WAKE=y/# CONFIG_ETHER_WAKE is not set/' \
    -e 's/^CONFIG_IFCONFIG=y/# CONFIG_IFCONFIG is not set/' \
    -e 's/^CONFIG_IFUP=y/# CONFIG_IFUP is not set/' \
    -e 's/^CONFIG_IFDOWN=y/# CONFIG_IFDOWN is not set/' \
    -e 's/^CONFIG_IFUPDOWN=y/# CONFIG_IFUPDOWN is not set/' \
    -e 's/^CONFIG_IFPLUGD=y/# CONFIG_IFPLUGD is not set/' \
    -e 's/^CONFIG_TC=y/# CONFIG_TC is not set/' \
    -e 's/^CONFIG_FEATURE_IPV6=y/# CONFIG_FEATURE_IPV6 is not set/' \
    -e 's/^CONFIG_FEATURE_NSLOOKUP_BIG=y/# CONFIG_FEATURE_NSLOOKUP_BIG is not set/' \
    -e 's/^CONFIG_NSLOOKUP=y/# CONFIG_NSLOOKUP is not set/' \
    -e 's/^CONFIG_SEEDRNG=y/# CONFIG_SEEDRNG is not set/' \
    -e 's/^CONFIG_RUNLEVEL=y/# CONFIG_RUNLEVEL is not set/' \
    -e 's/^CONFIG_HUSH=y/# CONFIG_HUSH is not set/' \
    -e 's/^CONFIG_LOGREAD=y/# CONFIG_LOGREAD is not set/' \
    -e 's/^CONFIG_SYSLOGD=y/# CONFIG_SYSLOGD is not set/' \
    -e 's/^CONFIG_IPCRM=y/# CONFIG_IPCRM is not set/' \
    -e 's/^CONFIG_IPCS=y/# CONFIG_IPCS is not set/' \
    -e 's/^CONFIG_FSCK_MINIX=y/# CONFIG_FSCK_MINIX is not set/' \
    -e 's/^CONFIG_MKFS_MINIX=y/# CONFIG_MKFS_MINIX is not set/' \
    -e 's/^CONFIG_FEATURE_MINIX2=y/# CONFIG_FEATURE_MINIX2 is not set/' \
    -e 's/^CONFIG_MOUNT=y/# CONFIG_MOUNT is not set/' \
    -e 's/^CONFIG_SWAPON=y/# CONFIG_SWAPON is not set/' \
    -e 's/^CONFIG_SWAPOFF=y/# CONFIG_SWAPOFF is not set/' \
    -e 's/^CONFIG_FEATURE_VOLUMEID_MINIX=y/# CONFIG_FEATURE_VOLUMEID_MINIX is not set/' \
    -e 's/^CONFIG_HUSH=y/# CONFIG_HUSH is not set/' \
    -e 's/^CONFIG_SHELL_HUSH=y/# CONFIG_SHELL_HUSH is not set/' \
    -e 's/^CONFIG_KLOGD=y/# CONFIG_KLOGD is not set/' \
    -e 's/^CONFIG_FSCK_MINIX=y/# CONFIG_FSCK_MINIX is not set/' \
    -e 's/^CONFIG_STATIC=y/# CONFIG_STATIC is not set/' .config
  make oldconfig </dev/null
  grep -q '^# CONFIG_LOADFONT is not set$' .config
  grep -q '^# CONFIG_SETFONT is not set$' .config
  grep -q '^# CONFIG_HOSTID is not set$' .config
  grep -q '^# CONFIG_LOGNAME is not set$' .config
  grep -q '^# CONFIG_SU is not set$' .config
  grep -q '^# CONFIG_LOGIN is not set$' .config
  grep -q '^# CONFIG_SHA1_HWACCEL is not set$' .config
  grep -q '^# CONFIG_SHA256_HWACCEL is not set$' .config
  grep -q '^# CONFIG_FEATURE_WTMP is not set$' .config
  grep -q '^# CONFIG_ADJTIMEX is not set$' .config
  grep -q '^# CONFIG_CONSPY is not set$' .config
  grep -q '^# CONFIG_ETHER_WAKE is not set$' .config
  grep -q '^# CONFIG_IFCONFIG is not set$' .config
  grep -q '^# CONFIG_RUNLEVEL is not set$' .config
  grep -q '^# CONFIG_IFUP is not set$' .config
  grep -q '^# CONFIG_TC is not set$' .config
  grep -q '^# CONFIG_FEATURE_IPV6 is not set$' .config
  grep -q '^# CONFIG_FEATURE_NSLOOKUP_BIG is not set$' .config
  grep -q '^# CONFIG_NSLOOKUP is not set$' .config
  grep -q '^# CONFIG_SEEDRNG is not set$' .config
  grep -q '^# CONFIG_HUSH is not set$' .config
  grep -q '^# CONFIG_SHELL_HUSH is not set$' .config
  grep -q '^# CONFIG_LOGREAD is not set$' .config
  grep -q '^# CONFIG_IPCRM is not set$' .config
  grep -q '^# CONFIG_IPCS is not set$' .config
  grep -q '^# CONFIG_MKFS_MINIX is not set$' .config
  grep -q '^# CONFIG_FEATURE_VOLUMEID_MINIX is not set$' .config
  grep -q '^# CONFIG_MOUNT is not set$' .config
  grep -q '^# CONFIG_SWAPON is not set$' .config
  grep -q '^# CONFIG_SWAPOFF is not set$' .config
  grep -q '^# CONFIG_FSCK_MINIX is not set$' .config
  ARCH="$(case "$ABI" in arm64-v8a) printf arm64 ;; armeabi-v7a) printf arm ;; x86_64) printf x86_64 ;; esac)"
  make -j"${JOBS:-2}" ARCH="$ARCH" CROSS_COMPILE= CC="$CC" LD="$TOOLCHAIN/ld.lld" AR="$AR" RANLIB="$RANLIB" STRIP="$STRIP" HOSTCC=cc
  make install ARCH="$ARCH" CROSS_COMPILE= CC="$CC" LD="$TOOLCHAIN/ld.lld" AR="$AR" RANLIB="$RANLIB" STRIP="$STRIP" HOSTCC=cc CONFIG_PREFIX="$STAGE"
)

[[ -x "$STAGE/bin/bash" ]] || { printf 'Bash install did not produce bin/bash.\n' >&2; exit 4; }
[[ -x "$STAGE/bin/busybox" ]] || { printf 'BusyBox install did not produce bin/busybox.\n' >&2; exit 4; }
"$STRIP" "$STAGE/bin/bash" "$STAGE/bin/busybox"
mkdir -p "$STAGE/home/.tmp" "$STAGE/etc"
cp "$WORK_DIR/src/bash/COPYING" "$STAGE/LICENSE-bash.txt"
cp "$WORK_DIR/src/busybox/LICENSE" "$STAGE/LICENSE-busybox.txt"
printf '%s\n' "$BASH_LICENSE" > "$STAGE/LICENSE-bash.spdx"
printf '%s\n' "$BUSYBOX_LICENSE" > "$STAGE/LICENSE-busybox.spdx"
printf 'Bash %s\nBusyBox %s\n' "$BASH_VERSION" "$BUSYBOX_VERSION" > "$STAGE/SOURCES.txt"

# Android's installer restores symlinks from this audited, prefix-relative list.
find "$STAGE" -type l -print0 | while IFS= read -r -d '' link; do
  rel="${link#"$STAGE"/}"
  target="$(readlink "$link")"
  case "$target" in /*|*\\*) printf 'Unsafe symlink: %s -> %s\n' "$rel" "$target" >&2; exit 5 ;; esac
  printf '%s→%s\n' "$target" "$rel" >> "$WORK_DIR/package/SYMLINKS.txt"
done
find "$STAGE" -type l -delete
cp -a "$STAGE/." "$WORK_DIR/package/"

python3 - "$WORK_DIR/package" "$ABI" "$VERSION" <<'PY'
import hashlib, json, os, pathlib, sys
root = pathlib.Path(sys.argv[1])
abi, version = sys.argv[2:]
files = []
for path in sorted(root.rglob('*')):
    if not path.is_file() or path.name == 'manifest.json':
        continue
    rel = path.relative_to(root).as_posix()
    data = path.read_bytes()
    mode = path.stat().st_mode & 0o777
    files.append({'path': rel, 'sha256': hashlib.sha256(data).hexdigest(), 'size': len(data), 'mode': mode})
manifest = {'version': version, 'abi': abi, 'files': files}
(root / 'manifest.json').write_text(json.dumps(manifest, sort_keys=True, separators=(',', ':')) + '\n')
PY

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
rm -f "$OUT_DIR/bootstrap.zip" "$OUT_DIR/bootstrap.zip.sha256"
(
  cd "$WORK_DIR/package"
  find . -type f -print0 | sort -z | xargs -0 zip -q -X "$OUT_DIR/bootstrap.zip"
)
sha256sum "$OUT_DIR/bootstrap.zip" > "$OUT_DIR/bootstrap.zip.sha256"
printf 'Built hashed Bash + BusyBox bootstrap for %s at %s\n' "$ABI" "$OUT_DIR/bootstrap.zip"
printf 'APT/dpkg are intentionally not included in this foundation phase.\n'
