#! /bin/bash

# following https://codetinkering.com/how-to-use-jpackage-tool-cli-for-macos-apps/

if [[ $# -eq 0 ]] ; then
    echo 'gimme the name of the jar file'
    exit 0
fi

# make jar/ directory if needed, and clear it out
mkdir -p jar/
rm -f jar/*

# copy argument jar to jar/ directory
cp $1 jar/\

# make icons
mkdir icons.iconset
sips -z 128 128 images/MarsThumbnail.png --out icons.iconset/icon_128x128.png
sips -z 128 128 images/MarsThumbnail.png --out icons.iconset/icon_64x64@2x.png
sips -z 64 64 images/MarsThumbnail.png --out icons.iconset/icon_64x64.png
sips -z 64 64 images/MarsThumbnail.png --out icons.iconset/icon_32x32@2x.png
sips -z 32 32 images/MarsThumbnail.png --out icons.iconset/icon_32x32.png
sips -z 32 32 images/MarsThumbnail.png --out icons.iconset/icon_16x16@2x.png
sips -z 16 16 images/MarsThumbnail.png --out icons.iconset/icon_16x16.png
iconutil -c icns icons.iconset/
rm -Rf icons.iconset/

# come up with some variables
NAME=${1%.*}
VERSION=${NAME:5}
VERSION=4.5.${VERSION//_/}

# package it
jpackage \
	--input jar/ \
	--dest downloads/ \
	--name "$NAME" \
	--icon icons.icns \
	--main-jar $1 \
	--type dmg \
	--app-version "$VERSION" \
	--vendor "com.jfbillingsley" \
	--mac-package-name "Mars" \
	--verbose

# remove icon file
rm -f icons.icns