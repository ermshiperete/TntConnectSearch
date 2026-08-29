#!/bin/bash
SCRIPTDIR="$(dirname $0)"
if [[ -f "${SCRIPTDIR}/tntconnect-search.jar" ]]; then
  JARFILE="${SCRIPTDIR}/tntconnect-search.jar"
elif [[ -f "${SCRIPTDIR}/target/tntconnect-search.jar" ]]; then
  JARFILE="${SCRIPTDIR}/target/tntconnect-search.jar"
else
  echo "ERROR: can't find tntconnect-search.jar"
  exit 1
fi
java -jar "${JARFILE}" "$@"
