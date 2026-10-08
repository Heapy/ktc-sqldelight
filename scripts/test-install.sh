#!/usr/bin/env bash
set -euo pipefail

# Exercise the published source archive and catalog transformation in a fresh consumer.
installer=${1:?usage: test-install.sh INSTALLER [COMMIT] [OWNER/REPO]}
root=$(cd "$(dirname "$0")/.." && pwd)
commit=${2:-$(git -C "$root" rev-parse HEAD)}
repository=${3:-Heapy/ktc-sqldelight}
consumer=$(mktemp -d "${TMPDIR:-/tmp}/ktc-sqldelight-consumer.XXXXXX")
trap 'rm -rf "$consumer"' EXIT

cp "$root/kotlin" "$root/kotlin.bat" "$consumer/"
mkdir -p "$consumer/app"
cp -R "$root/examples/app/schema" "$root/examples/app/test" "$consumer/app/"
printf 'modules:\n  - app\n' > "$consumer/project.yaml"
cat > "$consumer/app/module.yaml" <<'YAML'
product: jvm/lib

dependencies:
  - $libs.ktc.sqldelight.runtime

test-dependencies:
  - $libs.ktc.sqldelight.sqlite.driver

plugins:
  sqldelight:
    enabled: true
    packageName: example.db
    className: ExampleDatabase
    sourceDirectory: schema
YAML

"$installer" add "$repository" --commit "$commit" --enable-in app --project-dir "$consumer"
"$installer" verify --project-dir "$consumer"
"$consumer/kotlin" test --project-dir "$consumer"
