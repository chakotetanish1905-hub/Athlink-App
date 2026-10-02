#!/bin/sh
# Gradle wrapper script - run `./gradlew` to build
GRADLE_OPTS="${GRADLE_OPTS:-"-Dfile.encoding=UTF-8"}"
exec gradle "$@"
