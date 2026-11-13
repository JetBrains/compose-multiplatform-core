import json
import os
import re
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

def latest_versions(group, artifact):
    url = (
        "https://repo.maven.apache.org/maven2/"
        f"{group.replace('.', '/')}/{artifact}/maven-metadata.xml"
    )
    with urllib.request.urlopen(url, timeout=60) as response:
        metadata = ET.parse(response)
    stable, unstable = [], []
    for node in metadata.findall("./versioning/versions/version"):
        version = node.text
        match = re.fullmatch(
            r"(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)(\d*)?)?",
            version, re.IGNORECASE,
        )
        if not match:
            continue
        major, minor, patch, stage, number = match.groups()
        key = (int(major), int(minor), int(patch))
        if stage is None:
            stable.append((key, version))
        else:
            rank = {"alpha": 0, "beta": 1, "rc": 2}[stage.lower()]
            unstable.append((key + (rank, int(number or 0)), version))
    if not stable or not unstable:
        raise RuntimeError(f"Missing stable or prerelease version for {artifact}")
    return max(stable)[1], max(unstable)[1]

compose = latest_versions("org.jetbrains.compose", "compose-gradle-plugin")
kotlin = latest_versions("org.jetbrains.kotlin", "kotlin-gradle-plugin")
versions = {
    "compose": {"stable": compose[0], "unstable": compose[1], "local": "local"},
    "kotlin": {"stable": kotlin[0], "unstable": kotlin[1]},
}
results = Path(os.environ["RUNNER_TEMP"]) / "benchmark-results"
results.mkdir(parents=True, exist_ok=True)
(results / "versions.json").write_text(json.dumps(versions, indent=2) + "\n")
with open(os.environ["GITHUB_ENV"], "a") as env:
    env.write(f"COMPOSE_VERSIONS={','.join((*compose, 'local'))}\n")
    env.write(f"KOTLIN_VERSIONS={','.join(kotlin)}\n")
with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
    summary.write("## Benchmark versions\n\n")
    summary.write(f"- Compose: {', '.join((*compose, 'local'))}\n")
    summary.write(f"- Kotlin: {', '.join(kotlin)}\n")
    summary.write("- Local Compose uses the core checkout's own Kotlin version.\n")
print(json.dumps(versions, indent=2))
