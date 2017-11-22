#!/bin/bash
java -cp target/classes:$(echo target/dependency/*.jar | tr ' ' ':') nl.opengeogroep.safetymaps.Main $@
