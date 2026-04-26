#!/bin/bash

set -euxo pipefail

docker build --progress=plain -t vault-plugin-tests .
