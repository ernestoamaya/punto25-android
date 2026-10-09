#!/usr/bin/env python3
"""Regression tests for the trusted pre-merge physical APK policy."""

from __future__ import annotations

import importlib.util
import os
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POLICY_PATH = ROOT / ".github" / "scripts" / "physical_apk_policy.py"
WORKFLOW_PATH = ROOT / ".github" / "workflows" / "physical-apk.yml"

spec = importlib.util.spec_from_file_location("physical_apk_policy", POLICY_PATH)
assert spec and spec.loader
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)

REPOSITORY = "ernestoamaya/punto25-android"
TARGET = "a" * 40
OTHER = "b" * 40
CERT = "c" * 64


def pr_fixture(**overrides):
    value = {
        "number": 38,
        "state": "open",
        "merged": False,
        "base": {"ref": "main"},
        "head": {"sha": TARGET, "repo": {"full_name": REPOSITORY}},
    }
    for key, item in overrides.items():
        value[key] = item
    return value


class TargetPolicyTest(unittest.TestCase):
    def test_REG_PHYSICAL_APK_TARGET_001_sha_format_and_pr_number_are_strict(self):
        self.assertTrue(policy.is_full_lower_sha1(TARGET))
        for candidate in ("a" * 39, "A" * 40, "g" * 40, "", "main"):
            self.assertFalse(policy.is_full_lower_sha1(candidate), candidate)
        self.assertTrue(policy.is_valid_pr_number("38"))
        for candidate in ("0", "-1", "01", "abc", ""):
            self.assertFalse(policy.is_valid_pr_number(candidate), candidate)

    def test_REG_PHYSICAL_APK_TARGET_001_valid_open_same_repo_main_head_passes(self):
        policy.validate_pr_metadata(pr_fixture(), REPOSITORY, TARGET, 38)

    def test_REG_PHYSICAL_APK_TARGET_001_wrong_closed_merged_fork_base_stale_or_arbitrary_fails(self):
        bad = [
            pr_fixture(number=39),
            pr_fixture(state="closed"),
            pr_fixture(merged=True),
            pr_fixture(base={"ref": "develop"}),
            pr_fixture(head={"sha": TARGET, "repo": {"full_name": "someone/fork"}}),
            pr_fixture(head={"sha": OTHER, "repo": {"full_name": REPOSITORY}}),
        ]
        for candidate in bad:
            with self.subTest(candidate=candidate):
                with self.assertRaises(policy.PolicyError):
                    policy.validate_pr_metadata(candidate, REPOSITORY, TARGET, 38)

    def test_REG_PHYSICAL_APK_TARGET_001_nonexistent_and_nonancestor_commit_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.name", "Punto25 Test"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.email", "test@localhost"], cwd=repo, check=True)
            (repo / "a.txt").write_text("one\n")
            subprocess.run(["git", "add", "a.txt"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-q", "-m", "one"], cwd=repo, check=True)
            first = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
            (repo / "a.txt").write_text("two\n")
            subprocess.run(["git", "commit", "-qam", "two"], cwd=repo, check=True)
            second = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
            tree = subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], cwd=repo, text=True).strip()
            env = os.environ | {
                "GIT_AUTHOR_NAME": "Punto25 Test",
                "GIT_AUTHOR_EMAIL": "test@localhost",
                "GIT_COMMITTER_NAME": "Punto25 Test",
                "GIT_COMMITTER_EMAIL": "test@localhost",
            }
            divergent = subprocess.check_output(
                ["git", "commit-tree", tree], cwd=repo, env=env, input="divergent\n", text=True
            ).strip()
            self.assertTrue(policy.git_commit_exists(first, str(repo)))
            self.assertFalse(policy.git_commit_exists("0" * 40, str(repo)))
            self.assertTrue(policy.git_is_ancestor(first, second, str(repo)))
            self.assertFalse(policy.git_is_ancestor(divergent, second, str(repo)))

    def test_REG_PHYSICAL_APK_TARGET_001_original_base_survives_trusted_main_advance(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            subprocess.run(["git", "init", "-q", "-b", "main"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.name", "Punto25 Test"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.email", "test@localhost"], cwd=repo, check=True)
            (repo / "base.txt").write_text("base\n")
            subprocess.run(["git", "add", "."], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-q", "-m", "base"], cwd=repo, check=True)
            base = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
            subprocess.run(["git", "checkout", "-q", "-b", "feature"], cwd=repo, check=True)
            (repo / "feature.txt").write_text("feature\n")
            subprocess.run(["git", "add", "."], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-q", "-m", "feature"], cwd=repo, check=True)
            target = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
            subprocess.run(["git", "checkout", "-q", "main"], cwd=repo, check=True)
            (repo / "p20.txt").write_text("p20\n")
            subprocess.run(["git", "add", "."], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-q", "-m", "trusted main advance"], cwd=repo, check=True)
            trusted_main = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
            self.assertEqual(base, policy.git_original_pr_base(target, trusted_main, str(repo)))


class BuildSurfacePolicyTest(unittest.TestCase):
    def test_REG_PHYSICAL_APK_BUILD_SURFACE_001_sensitive_surfaces_fail_closed(self):
        sensitive = [
            ".github/workflows/evil.yml",
            ".github/actions/custom/action.yml",
            "build.gradle",
            "build.gradle.kts",
            "app/build.gradle",
            "app/build.gradle.kts",
            "settings.gradle",
            "settings.gradle.kts",
            "gradle.properties",
            "app/gradle.properties",
            "local.properties",
            "app/local.properties",
            "gradlew",
            "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.properties",
            "gradle/libs.versions.toml",
            "buildSrc/src/main/kotlin/Evil.kt",
            "build-logic/src/main/kotlin/Evil.kt",
            "scripts/build.sh",
            "tools/package.py",
            "feature/custom.gradle.kts",
        ]
        self.assertEqual(sorted(sensitive), policy.sensitive_build_paths(sensitive))
        self.assertFalse(policy.is_sensitive_build_path("app/src/main/java/ar/com/mandados/app/Foo.kt"))
        self.assertFalse(policy.is_sensitive_build_path("app/src/main/res/values/strings.xml"))


class ConfigAndSigningPolicyTest(unittest.TestCase):
    def test_REG_PHYSICAL_APK_CONFIG_001_exact_allowlist_https_and_no_whatsapp(self):
        policy.validate_runtime_config_names(policy.ALLOWED_RUNTIME_CONFIG)
        self.assertTrue(policy.is_https_url("https://example.invalid"))
        self.assertFalse(policy.is_https_url("http://example.invalid"))
        self.assertFalse(any(name.startswith("WHATSAPP_") for name in policy.ALLOWED_RUNTIME_CONFIG))
        with self.assertRaises(policy.PolicyError):
            policy.validate_runtime_config_names(policy.ALLOWED_RUNTIME_CONFIG - {"FIREBASE_API_KEY"})
        with self.assertRaises(policy.PolicyError):
            policy.validate_runtime_config_names(policy.ALLOWED_RUNTIME_CONFIG | {"WHATSAPP_VERIFY_NUMBER"})

    def test_REG_PHYSICAL_APK_SIGN_001_wrong_certificate_is_rejected(self):
        self.assertTrue(policy.certificate_matches(CERT, CERT))
        self.assertFalse(policy.certificate_matches(CERT, "d" * 64))
        self.assertFalse(policy.certificate_matches(CERT, "not-a-digest"))


class ProvenanceAndArtifactPolicyTest(unittest.TestCase):
    def test_REG_PHYSICAL_APK_PROVENANCE_001_mismatch_fails(self):
        expected = {
            "pr_number": "38",
            "requested_target_sha": TARGET,
            "checked_out_target_sha": TARGET,
            "applicationId": "ar.com.mandados.app",
        }
        policy.validate_provenance(dict(expected), expected)
        inconsistent = dict(expected)
        inconsistent["checked_out_target_sha"] = OTHER
        with self.assertRaises(policy.PolicyError):
            policy.validate_provenance(inconsistent, expected)

    def test_REG_PHYSICAL_APK_ARTIFACT_001_exact_bundle_only(self):
        apk = f"Punto25-physical-v0.3-{TARGET}.apk"
        expected = [apk, "SHA256SUMS.txt", "BUILD_PROVENANCE.txt"]
        policy.validate_artifact_files(expected, apk)
        for bad in (
            [apk, "SHA256SUMS.txt"],
            expected + [f"{apk}.idsig"],
            expected + ["extra.log"],
        ):
            with self.subTest(files=bad):
                with self.assertRaises(policy.PolicyError):
                    policy.validate_artifact_files(bad, apk)


class TrustedWorkflowStaticTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.build = cls.text.split("  build-intermediate:", 1)[1].split("  sign-apk:", 1)[0]
        cls.sign = cls.text.split("  sign-apk:", 1)[1]
        cls.validate = cls.text.split("  validate:", 1)[1].split("  build-intermediate:", 1)[0]

    def test_REG_PHYSICAL_APK_TRUSTED_WORKFLOW_001_source_is_current_main_and_target_cannot_replace_policy(self):
        self.assertIn('if: github.ref == \'refs/heads/main\'', self.validate)
        self.assertIn('[[ "$WORKFLOW_SOURCE_SHA" == "$TRUSTED_MAIN_SHA" ]]', self.validate)
        self.assertIn(".github/scripts/physical_apk_policy.py validate-pr", self.validate)
        self.assertIn(".github/scripts/physical_apk_policy.py validate-paths", self.validate)
        self.assertIn("physical_apk_policy.py derive-base", self.validate)
        self.assertNotIn("jq -r '.base.sha", self.validate)
        self.assertIn('git cat-file -e "${REQUESTED_TARGET_SHA}^{commit}"', self.validate)
        self.assertIn("persist-credentials: false", self.validate)

    def test_REG_PHYSICAL_APK_SECRETS_001_jobs_are_strictly_split(self):
        for secret in (
            "ALPHA_KEYSTORE_B64",
            "ALPHA_KEYSTORE_PASSWORD",
            "ALPHA_KEY_ALIAS",
            "ALPHA_KEY_PASSWORD",
        ):
            self.assertNotIn(secret, self.build)
            self.assertIn(secret, self.sign)
        self.assertNotIn("actions/checkout@", self.sign)
        self.assertNotIn("testDebugUnitTest", self.sign)
        self.assertNotRegex(self.sign, r"(?m)^\s*gradle\s")
        self.assertNotIn("FIREBASE_API_KEY", self.sign)
        self.assertNotIn("PUNTO25_API_BASE_URL", self.sign)

    def test_REG_PHYSICAL_APK_CONFIG_001_build_receives_only_client_allowlist(self):
        build_secret_refs = set(re.findall(r"secrets\.([A-Z0-9_]+)", self.build))
        self.assertEqual(policy.ALLOWED_RUNTIME_CONFIG, frozenset(build_secret_refs))
        self.assertNotIn("WHATSAPP_", self.build)
        self.assertNotIn("testDebugUnitTest", self.build)
        self.assertIn("gradle :app:assembleDebug", self.build)
        self.assertIn("cache-disabled: true", self.build)
        self.assertNotIn("cache-provider: basic", self.build)

    def test_REG_PHYSICAL_APK_SIGN_001_alignment_resign_v4_disabled_and_single_signer_verification(self):
        zipalign_pos = self.sign.index('"$ZIPALIGN" -f -v 4')
        sign_pos = self.sign.index('"$APKSIGNER" sign')
        v4_pos = self.sign.index("--v4-signing-enabled false")
        self.assertLess(zipalign_pos, sign_pos)
        self.assertGreater(v4_pos, sign_pos)
        self.assertIn('[[ "$SIGNER_COUNT" == "1" ]]', self.sign)
        self.assertIn('certificate_matches "$EXPECTED_ALPHA_CERT_SHA256" "$APK_SIGNER_CERT_SHA256"', self.sign)
        self.assertIn("DIFFERENT_CERT_SHA256", self.sign)

    def test_REG_PHYSICAL_APK_NOWRITE_001_permissions_and_actions_are_read_only(self):
        self.assertNotRegex(self.text, r"(?m)^\s*(contents|pull-requests|actions|packages|security-events):\s*write\s*$")
        for forbidden in ("gh pr merge", "gh release", "create-release", "upload-release", "play store", "google play"):
            self.assertNotIn(forbidden, self.text.lower())
        self.assertIn("permissions:\n  contents: read\n  pull-requests: read", self.text)

    def test_REG_PHYSICAL_APK_ARTIFACT_001_retention_exact_bundle_and_url(self):
        self.assertIn("retention-days: 1", self.build)
        self.assertIn("retention-days: 7", self.sign)
        self.assertIn("if-no-files-found: error", self.sign)
        self.assertIn("BUILD_PROVENANCE.txt", self.sign)
        self.assertIn("SHA256SUMS.txt", self.sign)
        self.assertIn('EXPECTED_FILES=("BUILD_PROVENANCE.txt" "$APK_NAME" "SHA256SUMS.txt")', self.sign)
        self.assertIn('[[ "${#BUNDLE_FILES[@]}" == "3" ]]', self.sign)
        self.assertIn("outputs.artifact-url", self.sign)
        self.assertIn('[[ -n "$ARTIFACT_URL" && -n "$ARTIFACT_ID" ]]', self.sign)

    def test_REG_PHYSICAL_APK_PROVENANCE_001_required_fields_present(self):
        for field in (
            "workflow_source_sha=",
            "trusted_main_sha=",
            "pr_number=",
            "pr_base_sha=",
            "requested_target_sha=",
            "checked_out_target_sha=",
            "versionName=",
            "versionCode=",
            "applicationId=",
            "alpha_signer_cert_sha256=",
            "apk_sha256=",
        ):
            self.assertIn(field, self.sign)

    def test_all_external_actions_are_pinned_to_immutable_sha(self):
        uses = re.findall(r"(?m)^\s*uses:\s*([^\s#]+)", self.text)
        self.assertGreater(len(uses), 0)
        for item in uses:
            with self.subTest(item=item):
                self.assertRegex(item, r"^[^@/]+/[^@]+@[0-9a-f]{40}$")


if __name__ == "__main__":
    unittest.main(verbosity=2)
