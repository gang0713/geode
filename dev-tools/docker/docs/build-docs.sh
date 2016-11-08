#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e -x -u

SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

. $SCRIPT_DIR/build-image-common.sh

docker run -i -t \
  --rm=true \
  -w "/home/${USER_NAME}/incubator-geode/geode-book" \
  -u "${USER_NAME}" \
  -v "$PWD:/home/${USER_NAME}/incubator-geode" \
  ${IMAGE_NAME}-${USER_NAME} \
  bundle exec bookbinder bind local

popd

