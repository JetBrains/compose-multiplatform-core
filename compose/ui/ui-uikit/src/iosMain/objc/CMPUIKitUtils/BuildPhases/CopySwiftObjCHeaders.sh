#!/bin/sh

set -euo pipefail

SWIFT_HEADER_NAME="${PRODUCT_MODULE_NAME}-Swift.h"
SWIFT_SOURCE_HEADER="${DERIVED_SOURCES_DIR}/${SWIFT_HEADER_NAME}"
BRIDGE_HEADER_NAME="${PRODUCT_MODULE_NAME}-ImportBridge.h"
BRIDGE_SOURCE_HEADER="${SRCROOT}/${PRODUCT_MODULE_NAME}/${BRIDGE_HEADER_NAME}"

for header in "${SWIFT_SOURCE_HEADER}" "${BRIDGE_SOURCE_HEADER}"; do
  if [ ! -f "${header}" ]; then
    echo "error: Header was not found at ${header}" >&2
    exit 1
  fi
done

copy_headers() {
  mkdir -p "$1"
  cp "${SWIFT_SOURCE_HEADER}" "$1/${SWIFT_HEADER_NAME}"
  cp "${BRIDGE_SOURCE_HEADER}" "$1/${BRIDGE_HEADER_NAME}"
}

copy_headers "${BUILT_PRODUCTS_DIR}/include/${PRODUCT_MODULE_NAME}"
if [ "${ACTION:-}" = "install" ] && [ -n "${DSTROOT:-}" ]; then
  copy_headers "${DSTROOT}${PUBLIC_HEADERS_FOLDER_PATH}/${PRODUCT_MODULE_NAME}"
fi
