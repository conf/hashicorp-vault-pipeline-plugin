#!/bin/bash

set -euxo pipefail

docker run --rm -v "${HOME}/.m2:/root/.m2" vault-plugin-tests
