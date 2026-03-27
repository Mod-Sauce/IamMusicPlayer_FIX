#!/bin/bash
set -e

echo "=== Upload Script Starting ==="

# Read version from gradle.properties (in same directory)
MOD_VERSION=$(sed -n "s/^mod_version=//p" gradle.properties | tr -d '\r' | xargs)
echo "Version: $MOD_VERSION"

# Collect JARs
mkdir -p persisted_builds
for LOADER in fabric forge neoforge; do
  BUILD_DIR="$LOADER/build/libs"
  if [ -d "$BUILD_DIR" ]; then
    echo "Collecting from $LOADER..."
    find "$BUILD_DIR" -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" ! -name "*-dev.jar" -exec cp {} persisted_builds/ \;
  fi
done

if [ -z "$(ls -A persisted_builds 2>/dev/null)" ]; then
  echo "ERROR: No JAR files found!"
  exit 1
fi

echo "Found artifacts:"
ls -la persisted_builds/

# Upload to Nextcloud
WEBDAV_BASE_PATH="$NEXTCLOUD_URL/remote.php/dav/files/$NEXTCLOUD_USERNAME"
UPLOAD_FOLDER="builds/$MOD_VERSION"

echo "=== Creating directories ==="
curl --location-trusted -u "$NEXTCLOUD_USERNAME:$NEXTCLOUD_PASSWORD" -X MKCOL "$WEBDAV_BASE_PATH/builds/" || true
curl --location-trusted -u "$NEXTCLOUD_USERNAME:$NEXTCLOUD_PASSWORD" -X MKCOL "$WEBDAV_BASE_PATH/$UPLOAD_FOLDER/" || true

echo "=== Uploading files ==="
cd persisted_builds
for FILE in *.jar; do
  [ -e "$FILE" ] || continue
  
  echo "Uploading $FILE..."
  curl --location-trusted -f -u "$NEXTCLOUD_USERNAME:$NEXTCLOUD_PASSWORD" -T "$FILE" "$WEBDAV_BASE_PATH/$UPLOAD_FOLDER/$FILE"
done

echo "=== Upload Completed Successfully ==="