#!/system/bin/sh

# binhmod @ github
# https://github.com/binhmod/AoVSkins
# Script dùng Gemini để việt hóa lại, có test đầy đủ nên yên tâm nhen!

# Đường dẫn công cụ giải nén
UNZIP="/data/local/tmp/binhmod/libminiz.so"

# Đường dẫn hệ thống
ROOT="/storage/emulated/0"
DEST="$ROOT/Android/data/com.garena.game.kgvn"
GAME="$DEST/files/Resources"
TMP="$ROOT/binhmod_tmp"

# Hàm lấy phiên bản game (thư mục con đầu tiên)
get_version() {
  [ ! -d "$1" ] && return
  for i in "$1"/*; do
    [ -e "$i" ] || break
    basename "$i"
    return
  done
}

# ================= RESTORE =================

restore_mode() {
  echo "===== Chế độ Khôi phục ====="

  CUR_VER=$(get_version "$GAME")
  [ -z "$CUR_VER" ] && { echo "Lỗi: Không tìm thấy phiên bản game"; exit 1; }

  LAST=$(ls -d "$ROOT"/binhmod_backup_* 2>/dev/null | tail -n 1)
  [ -z "$LAST" ] || [ ! -d "$LAST" ] && { echo "Lỗi: Không có backup"; exit 1; }

  BAK_VER=$(echo "$LAST" | sed 's/.*backup_//')

  echo "Game: $CUR_VER"
  echo "Backup: $BAK_VER"

  [ "$CUR_VER" != "$BAK_VER" ] && { echo "Lỗi: Phiên bản không khớp"; exit 1; }

  echo "Đang khôi phục..."

  if [ -d "$LAST/files" ]; then
    cp -r "$LAST/files" "$DEST/" || { echo "Lỗi restore"; exit 1; }
    echo "Dọn backup cũ..."
    echo "Khôi phục THÀNH CÔNG!"
  else
    echo "Backup hỏng"; exit 1
  fi
  for b in "$ROOT"/binhmod_backup_*; do
    [ -d "$b" ] && [ "$b" != "$LAST" ] && rm -rf "$b"
  done

  exit 0
}

[ "$1" = "restore" ] && restore_mode

# ================= INSTALL =================

ZIP="$1"
[ -z "$ZIP" ] && { echo "Dùng: $0 file.zip | restore"; exit 1; }

chmod 755 "$UNZIP" 2>/dev/null

echo "===== Kiểm tra phiên bản ====="

CUR_VER=$(get_version "$GAME")
echo "Game version: ${CUR_VER:-Không rõ}"

[ -z "$CUR_VER" ] && { echo "Không tìm thấy game"; exit 1; }

# TMP an toàn
[ -n "$TMP" ] && [ "$TMP" != "/" ] && rm -rf "$TMP"
mkdir -p "$TMP" || { echo "Không tạo TMP"; exit 1; }

echo "Giải nén..."
"$UNZIP" "$ZIP" "$TMP" || { echo "Giải nén lỗi"; exit 1; }

echo "===== Quét cấu trúc ZIP ====="

SRC=""

FOUND=$(find "$TMP" -type d -name Resources 2>/dev/null | head -n 1)

if [ -n "$FOUND" ]; then
  SRC=$(dirname "$FOUND")
else
  echo "ZIP sai cấu trúc: không tìm thấy Resources"
  exit 1
fi

echo "Phát hiện Resources tại:"
echo "$FOUND"

echo "===== Đọc mod.prop ====="

PROP="$TMP/mod.prop"

MOD_NAME=""
MOD_VER=""
MOD_AUTHOR=""
MOD_DESC=""
MOD_DATE=""

if [ -f "$PROP" ]; then

  get_prop() {
    grep "^$1=" "$PROP" | head -n 1 | cut -d= -f2-
  }

  MOD_NAME=$(get_prop name)
  MOD_VER=$(get_prop version)
  MOD_AUTHOR=$(get_prop author)
  MOD_DESC=$(get_prop description)
  MOD_DATE=$(get_prop date)

else
  echo "Không có mod.prop"
fi

ZIP_VER=$(get_version "$SRC/Resources")
echo "ZIP version: ${ZIP_VER:-Không rõ}"

[ -z "$ZIP_VER" ] && { echo "ZIP thiếu version"; exit 1; }
[ "$CUR_VER" != "$ZIP_VER" ] && { echo "Version mismatch"; exit 1; }

echo "===== Backup ====="

BACKUP="$ROOT/binhmod_backup_$CUR_VER"

[ -d "$BACKUP" ] && rm -rf "$BACKUP"
mkdir -p "$BACKUP"

cd "$SRC" || exit 1

find . -type f | while read f; do
  src="$DEST/files/$f"
  dst="$BACKUP/files/$f"

  if [ -f "$src" ]; then
    mkdir -p "$(dirname "$dst")"
    cp "$src" "$dst"
    echo "Backup: $f"
  fi
done

echo "Dọn backup cũ..."
for b in "$ROOT"/binhmod_backup_*; do
  [ -d "$b" ] && [ "$b" != "$BACKUP" ] && rm -rf "$b"
done

cp -r "$SRC" "$DEST/" || { echo "Cài lỗi"; exit 1; }

[ -n "$TMP" ] && [ "$TMP" != "/" ] && rm -rf "$TMP"
echo "========================="
clear
if [ -n "$MOD_NAME" ]; then
  echo "===== Thông tin Mod ====="

  [ -n "$MOD_NAME" ] && echo "Tên: $MOD_NAME"
  [ -n "$MOD_VER" ] && echo "Version: $MOD_VER"
  [ -n "$MOD_AUTHOR" ] && echo "Author: $MOD_AUTHOR"
  [ -n "$MOD_DESC" ] && echo "Mô tả: $MOD_DESC"
  [ -n "$MOD_DATE" ] && echo "Ngày: $MOD_DATE"

  echo "========================="
fi
echo "Phiên bản game hiện tại: ${CUR_VER:-Không rõ}"
echo "Phiên bản mod cho game: ${ZIP_VER:-Không rõ}"
echo "Backup: $BACKUP"
echo "Make with ❤️ by BinhMod"
echo "HOÀN TẤT"