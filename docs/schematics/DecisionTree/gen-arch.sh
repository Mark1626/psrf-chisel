#!/usr/bin/env bash

if ! command -v ditaa &> /dev/null
then
    echo "Ditaa could not be found."
    exit
fi

echo "Generating architecture schematic."
ditaa arch.txt arch.png -E

if ! command -v plantuml &> /dev/null
then
    echo "Plantuml could not be found."
    exit
fi

echo "Generating flow diagram."
plantuml -v -tpng flow.puml
