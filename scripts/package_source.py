"""Build the reviewed public source ZIP from an exact Git commit."""

import argparse
import hashlib
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
from zipfile import BadZipFile, ZIP_STORED, ZipFile, ZipInfo


ROOT = Path(__file__).resolve().parents[1]
VERSION = "v0.1.0"
ARCHIVE_NAME = f"contract-testing-toolkit-{VERSION}-source.zip"
MANIFEST_NAME = "SHA256SUMS.txt"
PUBLIC_FILES = frozenset({
    ".github/workflows/ci.yml",
    ".gitignore",
    "README.md",
    "LICENSE",
    "CONTRIBUTING.md",
    "SECURITY.md",
    "CHANGELOG.md",
    "THIRD_PARTY_NOTICES.md",
    "docs/SPEC.md",
    "docs/README.md",
    "docs/LEARNING_FA.md",
    "docs/WEBSITE_EN.md",
    "docs/INSTAGRAM_FA.md",
    "docs/INSTAGRAM_EN.md",
    "docs/LINKEDIN_DE.md",
    "docs/COVER.md",
    "docs/RELEASE_EVIDENCE.md",
    "docs/adr/0001-offline-schema-fixtures.md",
    "docs/adr/0002-local-http-verification.md",
    "docs/releases/v0.1.0.md",
    "fixtures/consumer.tsv",
    "fixtures/provider-compatible.tsv",
    "fixtures/provider-breaking.tsv",
    "fixtures/consumer-http.tsv",
    "src/main/java/toolkit/Compatibility.java",
    "src/main/java/toolkit/Contract.java",
    "src/main/java/toolkit/HttpVerification.java",
    "src/main/java/toolkit/Main.java",
    "src/main/java/toolkit/SampleProvider.java",
    "src/test/java/toolkit/ContractVerifierTest.java",
    "src/test/java/toolkit/OptionalCompatibilityTest.java",
    "src/test/java/toolkit/HttpContractTest.java",
    "src/test/java/toolkit/HttpCliTest.java",
    "scripts/verify.ps1",
    "scripts/package_source.py",
})
PRIVATE_FILES = frozenset({"AGENTS.md", "PROJECT_STATE.json", "docs/EVIDENCE.md", "docs/TASKS.md"})


def git(*arguments: str) -> bytes:
    result = subprocess.run(
        ["git", "--no-replace-objects", "-c", f"safe.directory={ROOT.as_posix()}", *arguments],
        cwd=ROOT,
        env={**os.environ, "GIT_NO_LAZY_FETCH": "1"},
        capture_output=True,
        check=False,
    )
    if result.returncode:
        raise ValueError(f"git {arguments[0]} failed with exit {result.returncode}")
    return result.stdout


def read_tree(commit: str) -> dict[str, tuple[int, str]]:
    resolved = git("rev-parse", "--verify", f"{commit}^{{commit}}").decode("ascii").strip()
    if resolved != commit:
        raise ValueError("the input must be the exact reviewed commit ID")

    sources = {}
    unknown_count = 0
    raw_tree = git("ls-tree", "--full-tree", "-rz", commit)
    for entry in raw_tree.split(b"\0")[:-1]:
        metadata, raw_path = entry.split(b"\t", 1)
        mode, object_type, blob = metadata.decode("ascii").split()
        path = raw_path.decode("utf-8", errors="surrogateescape")
        if object_type != "blob" or mode not in {"100644", "100755"}:
            raise ValueError("the reviewed tree contains a symlink, submodule, or other nonregular Git entry")
        if path in PUBLIC_FILES:
            sources[path] = (int(mode, 8), blob)
        elif path not in PRIVATE_FILES:
            unknown_count += 1
    if unknown_count:
        raise ValueError(f"the reviewed tree contains {unknown_count} paths outside the release scope")
    missing = PUBLIC_FILES - sources.keys()
    if missing:
        raise ValueError("the reviewed tree lacks required files " + ", ".join(sorted(missing)))
    return sources


def write_verified_zip(target: Path, commit: str, sources: dict[str, tuple[int, str]]) -> None:
    names = sorted(sources)
    contents = {name: git("cat-file", "blob", sources[name][1]) for name in names}
    commit_comment = commit.encode("ascii")
    # Stored bytes avoid compression differences between toolchain versions.
    with ZipFile(target, "w", compression=ZIP_STORED) as archive:
        archive.comment = commit_comment
        for name in names:
            info = ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.create_system = 3  # Store Git's regular-file mode as POSIX ZIP metadata.
            info.external_attr = (stat.S_IFREG | stat.S_IMODE(sources[name][0])) << 16
            archive.writestr(info, contents[name])

    with ZipFile(target) as archive:
        if archive.comment != commit_comment or archive.namelist() != names or archive.testzip() is not None:
            raise ValueError("ZIP comment, entry names, or CRC failed read-back verification")
        for name in names:
            if archive.read(name) != contents[name]:
                raise ValueError(f"ZIP content differs from Git for {name}")


def package(commit: str) -> None:
    sources = read_tree(commit)
    output = ROOT / "bin" / "release" / VERSION / commit
    if output.resolve() != output:
        raise ValueError("the release directory must use its ordinary repository-local path")
    output.mkdir(parents=True, exist_ok=True)
    final_archive = output / ARCHIVE_NAME
    final_manifest = output / MANIFEST_NAME

    with tempfile.TemporaryDirectory(dir=output, prefix=".source-") as temporary:
        staged_archive = Path(temporary) / ARCHIVE_NAME
        staged_manifest = Path(temporary) / MANIFEST_NAME
        write_verified_zip(staged_archive, commit, sources)
        digest = hashlib.sha256(staged_archive.read_bytes()).hexdigest()
        staged_manifest.write_bytes(f"{digest}  {ARCHIVE_NAME}\n".encode("ascii"))

        pairs = ((staged_archive, final_archive), (staged_manifest, final_manifest))
        for staged, final in pairs:
            if final.is_symlink() or (final.exists() and final.read_bytes() != staged.read_bytes()):
                raise ValueError(f"the existing {final.name} differs from the verified release output")
        for staged, final in pairs:
            if not final.exists():
                staged.replace(final)

    print(f"Verified {len(sources)} source files from Git commit {commit}.")
    print(f"Archive {final_archive.relative_to(ROOT).as_posix()}")
    print(f"SHA-256 {digest}")
    print(f"Checksums {final_manifest.relative_to(ROOT).as_posix()}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("commit", help="full lowercase commit ID of the reviewed source")
    commit = parser.parse_args().commit
    if re.fullmatch(r"[0-9a-f]{40}", commit) is None:
        parser.error("commit must be a full lowercase 40-character Git commit ID")
    try:
        package(commit)
    except (BadZipFile, OSError, ValueError) as error:
        print(f"Source packaging failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
