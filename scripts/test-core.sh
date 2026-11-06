#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
javac -d "$test_dir" "$project_dir"/src/main/java/dev/slidev/intellij/core/*.java "$project_dir"/src/test/java/dev/slidev/intellij/core/*.java
java -ea -cp "$test_dir" dev.slidev.intellij.core.TaskScopeTest
java -ea -cp "$test_dir" dev.slidev.intellij.core.CoreTest
java -ea -cp "$test_dir" dev.slidev.intellij.core.SyntaxTest
java -ea -cp "$test_dir" dev.slidev.intellij.core.CompletionTest
java -ea -cp "$test_dir" dev.slidev.intellij.core.ExpressionTest
java -ea -cp "$test_dir" dev.slidev.intellij.core.ReferencesTest
java -ea -cp "$test_dir" dev.slidev.intellij.core.CombinedFeaturesTest
java -ea -cp "$test_dir" dev.slidev.intellij.core.PreviewDiagnosticsTest
java -ea -cp "$test_dir:$project_dir/src/main/resources" dev.slidev.intellij.core.PreviewServerTest "${SLIDEV_TEST_NODE:-node}"
java -ea -cp "$test_dir:$project_dir/src/main/resources" dev.slidev.intellij.core.VueDiagnosticsTest
