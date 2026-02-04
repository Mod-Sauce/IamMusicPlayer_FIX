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
curl -L -u "$NEXTCLOUD_USERNAME:$NEXTCLOUD_PASSWORD" -X MKCOL "$WEBDAV_BASE_PATH/builds/" || true
curl -L -u "$NEXTCLOUD_USERNAME:$NEXTCLOUD_PASSWORD" -X MKCOL "$WEBDAV_BASE_PATH/$UPLOAD_FOLDER/" || true

echo "=== Uploading files ==="
cd persisted_builds
for FILE in *.jar; do
  [ -e "$FILE" ] || continue
  
  echo "Uploading $FILE..."
  curl -L -f -u "$NEXTCLOUD_USERNAME:$NEXTCLOUD_PASSWORD" -T "$FILE" "$WEBDAV_BASE_PATH/$UPLOAD_FOLDER/$FILE"
  
  # Share with User 1
  if [ -n "$NEXTCLOUD_SHARE_USER1" ]; then
    echo "Sharing with $NEXTCLOUD_SHARE_USER1..."
    curl -L -s -f -X POST -u "$NEXTCLOUD_USERNAME:$NEXTCLOUD_PASSWORD" \
      "$NEXTCLOUD_URL/ocs/v2.php/apps/files_sharing/api/v1/shares" \
      -H "OCS-APIRequest: true" \
      -d "path=/$UPLOAD_FOLDER/$FILE" \
      -d "shareType=0" \
      -d "shareWith=$NEXTCLOUD_SHARE_USER1" \
      -d "permissions=1" || echo "Share 1 failed (may already exist)"
  fi

  # Share with User 2
  if [ -n "$NEXTCLOUD_SHARE_USER2" ]; then
    echo "Sharing with $NEXTCLOUD_SHARE_USER2..."
    curl -L -s -f -X POST -u "$NEXTCLOUD_USERNAME:$NEXTCLOUD_PASSWORD" \
      "$NEXTCLOUD_URL/ocs/v2.php/apps/files_sharing/api/v1/shares" \
      -H "OCS-APIRequest: true" \
      -d "path=/$UPLOAD_FOLDER/$FILE" \
      -d "shareType=0" \
      -d "shareWith=$NEXTCLOUD_SHARE_USER2" \
      -d "permissions=1" || echo "Share 2 failed (may already exist)"
  fi
done

echo "=== Upload Completed Successfully ==="