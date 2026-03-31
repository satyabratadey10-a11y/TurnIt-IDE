#!/usr/bin/env sh
set -e
APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
JAVA_EXE="java"
if [ -n "$JAVA_HOME" ]; then JAVA_EXE="$JAVA_HOME/bin/java"; fi
exec "$JAVA_EXE" -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
