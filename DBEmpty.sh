#!/bin/sh
cd "$(dirname "${0}")" || exit 1

jdk/bin/java -cp .:resources:resources/lib/* com.maknoon.EmptyDatabase
