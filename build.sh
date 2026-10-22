#!/bin/bash
# Recap — manual APK build pipeline (no Gradle; same pattern as DropIt Android).
set -e
export JAVA_HOME=~/workspace/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
SDK=~/workspace/android-sdk
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
PROJ=~/workspace/recap-android
BUILD=$PROJ/build/out
KOTLINC=~/workspace/kotlinc-dist/kotlinc/bin/kotlinc
VER_CODE=${VER_CODE:-1}
VER_NAME=${VER_NAME:-0.1.0}

rm -rf $BUILD && mkdir -p $BUILD/gen $BUILD/classes $BUILD/dex $BUILD/apklib

echo "== 0. stage native libs (arm64 + x86_64) =="
for abi in arm64-v8a x86_64; do
  mkdir -p $BUILD/apklib/lib/$abi
  cp $PROJ/build/deps/vosk/jni/$abi/libvosk.so $BUILD/apklib/lib/$abi/
  cp $PROJ/build/deps/jna/jni/$abi/libjnidispatch.so $BUILD/apklib/lib/$abi/
done
cp $PROJ/build/deps/vosk/classes.jar $PROJ/libs/vosk.jar
cp $PROJ/build/deps/jna/classes.jar $PROJ/libs/jna.jar

echo "== 1. compile resources =="
$BT/aapt2 compile --dir $PROJ/app/src/main/res -o $BUILD/compiled_res.zip

echo "== 2. link =="
$BT/aapt2 link -o $BUILD/app-unaligned.apk \
  -I $PLATFORM \
  --manifest $PROJ/app/src/main/AndroidManifest.xml \
  --java $BUILD/gen \
  -A $PROJ/app/src/main/assets \
  --min-sdk-version 29 --target-sdk-version 34 \
  --version-code $VER_CODE --version-name $VER_NAME \
  $BUILD/compiled_res.zip

echo "== 3. compile R.java =="
find $BUILD/gen -name "*.java" > $BUILD/rjava.txt
$JAVA_HOME/bin/javac -source 17 -target 17 -cp $PLATFORM -d $BUILD/classes @$BUILD/rjava.txt

echo "== 4. compile kotlin =="
CP=$(ls $PROJ/libs/*.jar | tr '\n' ':')
$KOTLINC $(find $PROJ/app/src/main/java -name "*.kt") \
  -cp "${CP}$PLATFORM:$BUILD/classes" \
  -d $BUILD/classes -jvm-target 17 -no-stdlib 2>&1 | grep -v "^warning:"; test ${PIPESTATUS[0]} -eq 0
ls $BUILD/classes/com/essef/recap/MainActivity.class >/dev/null

echo "== 5. dex =="
(cd $BUILD/classes && $JAVA_HOME/bin/jar cf $BUILD/app-classes.jar .)
$BT/d8 --min-api 29 --lib $PLATFORM --output $BUILD/dex \
  $BUILD/app-classes.jar $PROJ/libs/kotlin-stdlib-1.8.21.jar $PROJ/libs/vosk.jar $PROJ/libs/jna.jar

echo "== 6. assemble apk (dex + native libs) =="
(cd $BUILD/dex && zip -X -q ../app-unaligned.apk classes.dex)
(cd $BUILD/apklib && zip -X -q ../app-unaligned.apk lib -r)

echo "== 7. zipalign =="
$BT/zipalign -f 4 $BUILD/app-unaligned.apk $BUILD/app-aligned.apk

echo "== 8. sign =="
KEYSTORE=$PROJ/recap.keystore
if [ ! -f $KEYSTORE ]; then
  $JAVA_HOME/bin/keytool -genkeypair -keystore $KEYSTORE \
    -alias recap -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass android -keypass android -dname "CN=Recap" 2>/dev/null
fi
$BT/apksigner sign --ks $KEYSTORE --ks-pass pass:android \
  --out $BUILD/Recap.apk $BUILD/app-aligned.apk

echo "== 9. verify =="
$BT/apksigner verify --print-certs $BUILD/Recap.apk | head -3
$BT/aapt2 dump badging $BUILD/Recap.apk | head -6
ls -la $BUILD/Recap.apk
