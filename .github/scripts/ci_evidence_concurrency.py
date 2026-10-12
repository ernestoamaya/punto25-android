#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CI = ROOT / ".github/workflows/ci.yml"
CODEQL = ROOT / ".github/workflows/codeql.yml"

EXPECTED = {
    CI: (
        "android-ci-${{ github.workflow }}-${{ github.event_name == 'pull_request' && format('pr-{0}', github.event.pull_request.number) || format('run-{0}', github.run_id) }}",
        "${{ github.event_name == 'pull_request' }}",
    ),
    CODEQL: (
        "codeql-${{ github.workflow }}-${{ github.event_name == 'pull_request' && format('pr-{0}', github.event.pull_request.number) || format('run-{0}', github.run_id) }}",
        "${{ github.event_name == 'pull_request' }}",
    ),
}


def concurrency_values(path: Path) -> tuple[str, str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    try:
        start = lines.index("concurrency:")
    except ValueError as exc:
        raise AssertionError(f"{path}: missing top-level concurrency block") from exc

    block: dict[str, str] = {}
    for line in lines[start + 1 :]:
        if line and not line.startswith(" "):
            break
        stripped = line.strip()
        if not stripped or ":" not in stripped:
            continue
        key, value = stripped.split(":", 1)
        block[key] = value.strip()

    assert set(block) == {"group", "cancel-in-progress"}, (
        f"{path}: concurrency block must contain only group/cancel-in-progress, got {sorted(block)}"
    )
    return block["group"], block["cancel-in-progress"]


def resolved(prefix: str, workflow: str, event: str, run_id: int, pr_number: int | None = None) -> tuple[str, bool]:
    if event == "pull_request":
        assert pr_number is not None
        identity = f"pr-{pr_number}"
        cancel = True
    else:
        identity = f"run-{run_id}"
        cancel = False
    return f"{prefix}-{workflow}-{identity}", cancel


def assert_workflow_contracts() -> None:
    for path, expected in EXPECTED.items():
        actual = concurrency_values(path)
        assert actual == expected, f"{path}: unexpected concurrency contract: {actual!r}"

    ci_text = CI.read_text(encoding="utf-8")
    codeql_text = CODEQL.read_text(encoding="utf-8")

    for token in (
        "  test-and-build:",
        "Classify PR changes",
        "gradle :app:testDebugUnitTest :app:assembleDebug --stacktrace --no-daemon",
        "*.md|LICENSE|.github/CODEOWNERS",
    ):
        assert token in ci_text, f"ci.yml lost required invariant: {token}"

    for token in (
        "- cron: '31 10 * * 0'",
        "- language: actions",
        "- language: java-kotlin",
        "build-mode: manual",
        "gradle :app:compileDebugUnitTestKotlin :app:assembleDebug --stacktrace --no-daemon",
    ):
        assert token in codeql_text, f"codeql.yml lost required invariant: {token}"
    assert "workflow_dispatch:" not in codeql_text, "P26-A must not add CodeQL workflow_dispatch"


def assert_resolved_semantics() -> None:
    ci_pr_old = resolved("android-ci", "Android CI", "pull_request", 1001, 77)
    ci_pr_new = resolved("android-ci", "Android CI", "pull_request", 1002, 77)
    ci_other_pr = resolved("android-ci", "Android CI", "pull_request", 1003, 78)
    assert ci_pr_old == ci_pr_new
    assert ci_pr_old[1] is True
    assert ci_other_pr[0] != ci_pr_old[0]

    ci_push_a = resolved("android-ci", "Android CI", "push", 2001)
    ci_push_b = resolved("android-ci", "Android CI", "push", 2002)
    ci_manual = resolved("android-ci", "Android CI", "workflow_dispatch", 2003)
    assert len({ci_push_a[0], ci_push_b[0], ci_manual[0]}) == 3
    assert all(cancel is False for _, cancel in (ci_push_a, ci_push_b, ci_manual))

    codeql_pr_old = resolved("codeql", "CodeQL Advanced", "pull_request", 3001, 77)
    codeql_pr_new = resolved("codeql", "CodeQL Advanced", "pull_request", 3002, 77)
    assert codeql_pr_old == codeql_pr_new
    assert codeql_pr_old[1] is True

    codeql_push_a = resolved("codeql", "CodeQL Advanced", "push", 4001)
    codeql_push_b = resolved("codeql", "CodeQL Advanced", "push", 4002)
    codeql_schedule_a = resolved("codeql", "CodeQL Advanced", "schedule", 5001)
    codeql_schedule_b = resolved("codeql", "CodeQL Advanced", "schedule", 5002)
    assert len({codeql_push_a[0], codeql_push_b[0], codeql_schedule_a[0], codeql_schedule_b[0]}) == 4
    assert all(cancel is False for _, cancel in (codeql_push_a, codeql_push_b, codeql_schedule_a, codeql_schedule_b))
    assert codeql_push_a[0] != codeql_schedule_a[0]


if __name__ == "__main__":
    assert_workflow_contracts()
    assert_resolved_semantics()
    print(
        "CI evidence concurrency regressions PASS: same-PR heads share a cancellable group; "
        "non-PR runs use unique run groups with cancellation disabled; CodeQL push/schedule remain independent."
    )
