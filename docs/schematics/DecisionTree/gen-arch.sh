#!/usr/bin/env bash

if ! command -v plantuml &> /dev/null
then
    echo "Plantuml could not be found."
    exit
fi

echo "Generating flow diagram."
plantuml -v -tpng flow.puml
