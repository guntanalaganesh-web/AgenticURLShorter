#!/bin/sh
# Apache Maven Wrapper startup shell script (classic form)

set -e

MAVEN_PROJECTBASEDIR=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE="java"
fi

exec "$JAVA_EXE" $JAVA_OPTS $MAVEN_OPTS \
  "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
  -classpath "$WRAPPER_JAR" \
  $WRAPPER_LAUNCHER "$@"
