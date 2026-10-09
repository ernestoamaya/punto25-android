#!/usr/bin/env python3
"""Trusted policy helpers for Punto25 pre-merge physical APK packaging."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath
from typing import Iterable

SHA1_RE = re.compile(r"^[0-9a-f]{40}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
PR_NUMBER_RE = re.compile(r"^[1-9][0-9]*$")

ALLOWED_RUNTIME_CONFIG = frozenset(
    {
        "FIREBASE_API_KEY",
        "FIREBASE_APP_ID",
        "FIREBASE_PROJECT_ID",
        "GOOGLE_WEB_CLIENT_ID",
        "PUNTO25_API_BASE_URL",
    }
)

EXACT_SENSITIVE_PATHS = frozenset(
    {
        "build.gradle",
        "build.gradle.kts",
        "settings.gradle",
        "settings.gradle.kts",
        "gradle.properties",
        "local.properties",
        "gradlew",
        "gradlew.bat",
    }
)

SENSITIVE_PREFIXES = (
    ".github/",
    "gradle/",
    "buildSrc/",
    "build-logic/",
    "scripts/",
    "tools/",
)


class PolicyError(ValueError):
    pass


def is_full_lower_sha1(value: str) -> bool:
    return bool(SHA1_RE.fullmatch(value or ""))


def is_sha256(value: str) -> bool:
    return bool(SHA256_RE.fullmatch(value or ""))


def is_valid_pr_number(value: str) -> bool:
    return bool(PR_NUMBER_RE.fullmatch(value or ""))


def validate_pr_metadata(pr: dict, repository: str, target_sha: str, pr_number: int | None = None) -> None:
    if not is_full_lower_sha1(target_sha):
        raise PolicyError("target_sha must be a full lowercase 40-character SHA-1")
    if pr_number is not None and pr.get("number") != pr_number:
        raise PolicyError("PR response number does not match requested pr_number")

    if pr.get("state") != "open":
        raise PolicyError("PR must be OPEN")
    if pr.get("merged") is not False:
        raise PolicyError("PR must not be merged")

    base = pr.get("base") or {}
    if base.get("ref") != "main":
        raise PolicyError("PR base must be main")

    head = pr.get("head") or {}
    head_repo = head.get("repo") or {}
    if head_repo.get("full_name") != repository:
        raise PolicyError("PR head repository must be the canonical same repository")
    if head.get("sha") != target_sha:
        raise PolicyError("target_sha must exactly match the PR current HEAD")


def is_sensitive_build_path(path: str) -> bool:
    normalized = str(PurePosixPath(path))
    if normalized in EXACT_SENSITIVE_PATHS:
        return True
    if normalized.startswith(SENSITIVE_PREFIXES):
        return True

    name = PurePosixPath(normalized).name
    if name in {"build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"}:
        return True
    if name in {"libs.versions.toml", "gradle.properties", "local.properties"}:
        return True
    if normalized.endswith(".gradle") or normalized.endswith(".gradle.kts"):
        return True
    return False


def sensitive_build_paths(paths: Iterable[str]) -> list[str]:
    return sorted({path for path in paths if path and is_sensitive_build_path(path)})


def validate_runtime_config_names(names: Iterable[str]) -> None:
    supplied = frozenset(names)
    if supplied != ALLOWED_RUNTIME_CONFIG:
        missing = sorted(ALLOWED_RUNTIME_CONFIG - supplied)
        unexpected = sorted(supplied - ALLOWED_RUNTIME_CONFIG)
        raise PolicyError(f"runtime config allowlist mismatch; missing={missing} unexpected={unexpected}")


def git_commit_exists(sha: str, cwd: str | None = None) -> bool:
    if not is_full_lower_sha1(sha):
        return False
    result = subprocess.run(
        ["git", "cat-file", "-e", f"{sha}^{{commit}}"],
        cwd=cwd,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    return result.returncode == 0


def git_is_ancestor(ancestor: str, descendant: str, cwd: str | None = None) -> bool:
    if not is_full_lower_sha1(ancestor) or not is_full_lower_sha1(descendant):
        return False
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", ancestor, descendant],
        cwd=cwd,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    return result.returncode == 0


def git_original_pr_base(target_sha: str, trusted_main_sha: str, cwd: str | None = None) -> str:
    """Derive the PR branch point from first-parent history and verify it is the merge base."""
    if not git_commit_exists(target_sha, cwd) or not git_commit_exists(trusted_main_sha, cwd):
        raise PolicyError("target or trusted main commit does not exist")

    result = subprocess.run(
        ["git", "rev-list", "--reverse", "--first-parent", f"{trusted_main_sha}..{target_sha}"],
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if result.returncode != 0:
        raise PolicyError("unable to enumerate PR first-parent history")
    unique_commits = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if not unique_commits:
        raise PolicyError("target has no PR-specific commit outside trusted main")

    first_pr_commit = unique_commits[0]
    parent = subprocess.run(
        ["git", "rev-parse", f"{first_pr_commit}^1"],
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if parent.returncode != 0:
        raise PolicyError("first PR commit has no valid first parent")
    base_sha = parent.stdout.strip()
    if not is_full_lower_sha1(base_sha):
        raise PolicyError("derived PR base SHA is not canonical")

    common = subprocess.run(
        ["git", "merge-base", target_sha, trusted_main_sha],
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if common.returncode != 0 or common.stdout.strip() != base_sha:
        raise PolicyError("first-parent PR base does not match trusted merge base")
    if not git_is_ancestor(base_sha, target_sha, cwd):
        raise PolicyError("PR base is not an ancestor of target")
    if not git_is_ancestor(base_sha, trusted_main_sha, cwd):
        raise PolicyError("PR base is not an ancestor of trusted main")
    return base_sha


def is_https_url(value: str) -> bool:
    return value.startswith("https://") and len(value) > len("https://")


def validate_artifact_files(file_names: Iterable[str], apk_name: str) -> None:
    actual = sorted(file_names)
    expected = sorted([apk_name, "SHA256SUMS.txt", "BUILD_PROVENANCE.txt"])
    if actual != expected:
        raise PolicyError(f"artifact contents mismatch; expected={expected} actual={actual}")


def certificate_matches(expected: str, actual: str) -> bool:
    return is_sha256(expected) and is_sha256(actual) and expected == actual


def validate_provenance(record: dict[str, str], expected: dict[str, str]) -> None:
    for key, expected_value in expected.items():
        if record.get(key) != expected_value:
            raise PolicyError(f"provenance mismatch for {key}")


def _load_json(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise PolicyError("JSON document must be an object")
    return value


def _read_nul_paths(path: str) -> list[str]:
    data = Path(path).read_bytes()
    if not data:
        return []
    if data[-1:] != b"\0":
        raise PolicyError("changed-path list must be NUL terminated")
    decoded = data[:-1].split(b"\0")
    return [item.decode("utf-8", errors="strict") for item in decoded]


def command_validate_pr(args: argparse.Namespace) -> int:
    if not is_valid_pr_number(args.pr_number):
        raise PolicyError("pr_number must be a positive decimal integer")
    pr = _load_json(args.pr_json)
    validate_pr_metadata(pr, args.repository, args.target_sha, int(args.pr_number))
    return 0


def command_validate_paths(args: argparse.Namespace) -> int:
    blocked = sensitive_build_paths(_read_nul_paths(args.nul_file))
    if blocked:
        for path in blocked:
            print(f"blocked build surface: {path}", file=sys.stderr)
        raise PolicyError("target modifies build-executable/sensitive surface")
    return 0


def command_validate_config(args: argparse.Namespace) -> int:
    validate_runtime_config_names(args.name)
    return 0


def command_derive_base(args: argparse.Namespace) -> int:
    print(git_original_pr_base(args.target_sha, args.trusted_main_sha, args.repo))
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)

    validate_pr = sub.add_parser("validate-pr")
    validate_pr.add_argument("--pr-json", required=True)
    validate_pr.add_argument("--repository", required=True)
    validate_pr.add_argument("--pr-number", required=True)
    validate_pr.add_argument("--target-sha", required=True)
    validate_pr.set_defaults(handler=command_validate_pr)

    validate_paths = sub.add_parser("validate-paths")
    validate_paths.add_argument("--nul-file", required=True)
    validate_paths.set_defaults(handler=command_validate_paths)

    validate_config = sub.add_parser("validate-config")
    validate_config.add_argument("--name", action="append", required=True)
    validate_config.set_defaults(handler=command_validate_config)

    derive_base = sub.add_parser("derive-base")
    derive_base.add_argument("--target-sha", required=True)
    derive_base.add_argument("--trusted-main-sha", required=True)
    derive_base.add_argument("--repo", default=None)
    derive_base.set_defaults(handler=command_derive_base)

    return parser


def main() -> int:
    try:
        args = build_parser().parse_args()
        return args.handler(args)
    except PolicyError as exc:
        print(f"policy rejected input: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
