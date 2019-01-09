#!/bin/bash
mvn clean install
mvn dependency:copy-dependencies
rm safetymaps-cli-*.tar.gz 2>/dev/null
rm -rf safetymaps-cli 2>/dev/null
mkdir safetymaps-cli
cd safetymaps-cli
mkdir lib
cp -r ../target/classes lib
cp -r ../target/dependency lib
cp ../safetymaps-cli.sh .
sed -i s/target/lib/g safetymaps-cli.sh
cd ..
tar czf safetymaps-cli-`date '+%Y-%m-%d_%H%M%S'`.tar.gz safetymaps-cli
rm -rf safetymaps-cli 2>/dev/null

