#!/usr/bin/env bash

if ! command -v ditaa &> /dev/null
then
    echo "Ditaa could not be found."
    exit
fi

echo "Generating architecture schematic."
ditaa arch.txt arch.png -Eo
