#!/bin/bash
DIR=$(dirname "$0")
java -cp $DIR/target/classes:$(echo $DIR/target/dependency/*.jar | tr ' ' ':') nl.opengeogroep.safetymaps.Main $@
