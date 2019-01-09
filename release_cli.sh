#!/bin/bash
mvn install
mvn dependency:copy-dependencies
rm safetymaps-cli-*.zip 2>/dev/null
rm -rf temp 2>/dev/null
mkdir temp
cd temp
mkdir lib
cp -r ../target/classes lib
cp -r ../target/dependency lib
cp ../safetymaps-cli.sh .
sed -i s/target/lib/g safetymaps-cli.sh
zip -r -l -q ../safetymaps-cli-`date '+%Y-%m-%d_%H%M%S'`.zip *
cd ..
rm -rf temp 2>/dev/null

