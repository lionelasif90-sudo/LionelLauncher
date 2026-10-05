#!/usr/bin/env bash
# Run once on your computer. Then add the printed values as GitHub repo secrets
# (Settings > Secrets and variables > Actions): KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS.
# Keep lionel.jks safe: lose it and you can't update the installed app without reinstalling.
set -e
read -r -s -p "Choose a keystore password: " PW; echo
keytool -genkeypair -keystore lionel.jks -storepass "$PW" -keypass "$PW" -alias lionel \
  -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Lionel Launcher,O=Lionel,C=US"
echo "KEY_ALIAS=lionel"
echo "KEYSTORE_PASSWORD=(the password you typed)"
echo "KEYSTORE_BASE64="
base64 -w0 lionel.jks 2>/dev/null || base64 lionel.jks
echo
