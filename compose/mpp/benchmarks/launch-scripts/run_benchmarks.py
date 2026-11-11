#!/usr/bin/env python3
# Copyright 2026 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Build and run local benchmarks, collecting one report per fresh application process."""

import argparse
from contextlib import contextmanager, ExitStack
from datetime import datetime, timezone
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import plistlib
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import threading
import time
import traceback
from urllib.parse import urlencode
import uuid


ROOT = Path(__file__).resolve().parents[1]
APP_TASK = ":benchmarks-app:"
COLLECTOR_TASK = ":benchmarks-collector:"
BUNDLE_ID = "org.jetbrains.compose.benchmarks.app.BenchmarksIosApp"
TESTS = (
    "AnimatedVisibility", "LazyGrid", "LazyGrid-ItemLaunchedEffect", "LazyGrid-SmoothScroll",
    "LazyGrid-SmoothScroll-ItemLaunchedEffect", "VisualEffects", "LazyList", "MultipleComponents",
    "MultipleComponents-NoVectorGraphics", "TextLayout", "CanvasDrawing", "HeavyShader",
)
MODES = ("averageFrame", "fps", "realFrames", "startup")


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True, choices=("jvm", "macos", "js", "wasmJs", "ios"))
    parser.add_argument("--port", type=int, default=8090, help="collector port (default: 8090)")
    parser.add_argument("--tests", nargs="+", default=["all"], help="case names, space/comma separated")
    parser.add_argument("--modes", nargs="+", default=["all"], help="modes, space/comma separated")
    parser.add_argument("--repeat", type=int, default=1)
    parser.add_argument("--timeout", type=float, default=30, help="seconds per test (default: 30)")
    parser.add_argument("--output-dir", type=Path, help="new directory for this series")
    parser.add_argument("--skip-build", action="store_true", help="reuse existing release artifacts")
    parser.add_argument("--compose-core-path", type=Path, help="include a core checkout instead of published Compose libraries")
    parser.add_argument("--browser", help="Chromium/Firefox/Safari executable, command name, or macOS .app")
    parser.add_argument("--browser-arg", action="append", default=[], help="extra browser flag; use --browser-arg=--flag")
    parser.add_argument("--web-port", type=int, default=0, help="static file server port (default: automatic)")
    parser.add_argument("--simulator", help="iOS Simulator UUID or unique name (default: one booted device)")
    args = parser.parse_args()
    if os.name != "posix":
        parser.error("this launcher requires macOS or Linux")
    if args.target in ("ios", "macos") and sys.platform != "darwin":
        parser.error("Apple targets require macOS")
    if not 1 <= args.port <= 65535 or not 0 <= args.web_port <= 65535:
        parser.error("invalid port")
    if args.repeat < 1 or not 0 < args.timeout < float("inf"):
        parser.error("repeat and timeout must be positive")
    if args.target in ("js", "wasmJs") and not args.browser:
        parser.error("web targets require --browser")
    for name, available in (("tests", TESTS), ("modes", MODES)):
        values = [item for value in getattr(args, name) for item in value.split(",")]
        if values == ["all"]:
            values = list(available)
        unknown = set(values) - set(available)
        if unknown:
            parser.error(f"unknown {name}: {', '.join(sorted(unknown))}")
        setattr(args, name, list(dict.fromkeys(values)))
    return args


def stop_process(process):
    # Each child has its own session. Never signal the user's other browser or a Gradle daemon.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        process.wait()
        return
    except PermissionError:
        # macOS may leave sandboxed XPC helpers in the group after the browser exits.
        if process.poll() is None:
            process.terminate()
    try:
        process.wait(timeout=3)
    except subprocess.TimeoutExpired:
        pass
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    except PermissionError:
        if process.poll() is None:
            process.kill()
    process.wait()


@contextmanager
def managed_process(command, log_path):
    with log_path.open("ab", buffering=0) as log:
        log.write(("$ " + repr([str(arg) for arg in command]) + "\n").encode())
        process = subprocess.Popen(
            [str(arg) for arg in command], cwd=ROOT, stdout=log,
            stderr=subprocess.STDOUT, start_new_session=True,
        )
        try:
            yield process
        finally:
            stop_process(process)


def run_command(command, log_path):
    print(f"Running: {' '.join(str(arg) for arg in command)}", flush=True)
    with managed_process(command, log_path) as process:
        if process.wait() != 0:
            raise RuntimeError(f"command failed; see {log_path}")


def select_simulator(selection):
    data = json.loads(subprocess.check_output(
        ["xcrun", "simctl", "list", "devices", "available", "--json"], text=True,
    ))
    devices = [device for group in data["devices"].values() for device in group if device.get("isAvailable")]
    matches = [device for device in devices if
               (selection in (device["udid"], device["name"]) if selection else device["state"] == "Booted")]
    if len(matches) != 1:
        raise RuntimeError("specify --simulator UUID, or boot exactly one simulator before running")
    return matches[0]


def executable(value):
    path = Path(value).expanduser()
    if path.suffix == ".app" and path.is_dir():
        with (path / "Contents/Info.plist").open("rb") as file:
            path = path / "Contents/MacOS" / plistlib.load(file)["CFBundleExecutable"]
    found = str(path.resolve()) if path.is_file() else shutil.which(value)
    if not found or not os.access(found, os.X_OK):
        raise RuntimeError(f"executable not found: {value}")
    return found


def detect_browser(browser):
    name = Path(browser).name.casefold()
    if name in ("safari", "safari technology preview"):
        return "safari"
    if "firefox" in name:
        return "firefox"
    if any(part in name for part in ("chrome", "chromium", "brave", "edge", "vivaldi")) or name == "arc":
        return "chromium"
    raise RuntimeError(f"cannot determine browser family from executable name: {browser}")


def safari_pids(browser):
    processes = subprocess.check_output(["ps", "-ww", "-axo", "pid=,comm="], text=True)
    executable_path = Path(browser).resolve()
    matches = []
    for line in processes.splitlines():
        fields = line.strip().split(None, 1)
        if len(fields) == 2 and Path(fields[1]).resolve() == executable_path:
            matches.append(int(fields[0]))
    return matches


def require_safari_closed(browser):
    if safari_pids(browser):
        raise RuntimeError("close Safari before starting the series; existing Safari sessions will not be closed")


def safari_bundle(browser):
    for parent in Path(browser).parents:
        if parent.suffix == ".app":
            return parent
    raise RuntimeError("Safari executable must be inside its .app bundle")


def quit_safari(browser, log_path):
    pids = safari_pids(browser)
    if not pids:
        return
    script = 'on run argv\n tell application (item 1 of argv) to quit\nend run'
    with log_path.open("ab") as log:
        try:
            subprocess.run(["osascript", "-e", script, str(safari_bundle(browser))],
                           stdout=log, stderr=log, timeout=5)
        except subprocess.TimeoutExpired:
            log.write(b"Safari quit timed out; terminating the launched process.\n")
    deadline = time.monotonic() + 3
    while time.monotonic() < deadline:
        if not set(pids).intersection(safari_pids(browser)):
            return
        time.sleep(0.1)
    # AppleScript may be denied by macOS Automation permissions. Only signal the owned Safari PIDs.
    remaining = set(pids).intersection(safari_pids(browser))
    for pid in remaining:
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    deadline = time.monotonic() + 3
    while time.monotonic() < deadline:
        if not remaining.intersection(safari_pids(browser)):
            return
        time.sleep(0.1)
    raise RuntimeError("Safari did not stop; refusing to start the next test")


@contextmanager
def launch_safari(browser, url, log_path):
    require_safari_closed(browser)
    with managed_process(["open", "-W", "-a", safari_bundle(browser), url], log_path) as process:
        try:
            yield lambda: process.poll() is not None
        finally:
            quit_safari(browser, log_path)


def java_executable():
    home = os.environ.get("JAVA_HOME")
    return executable(str(Path(home) / "bin/java") if home else "java")


def build(args, out, logs, simulator):
    tasks = [COLLECTOR_TASK + "installDist"]
    tasks += {
        "jvm": [APP_TASK + "packageReleaseUberJarForCurrentOS"],
        "macos": [APP_TASK + "linkReleaseExecutableMacosArm64"],
        "js": [APP_TASK + "jsBrowserDistribution"],
        "wasmJs": [APP_TASK + "wasmJsBrowserDistribution"],
        "ios": [],
    }[args.target]
    run_command([ROOT / "gradlew", *tasks, "--console=plain"], logs / "gradle-build.log")
    if args.target == "ios":
        run_command([
            "xcodebuild", "-project", ROOT / "benchmarks-ios-app/BenchmarksIosApp.xcodeproj",
            "-scheme", "BenchmarksIosApp", "-configuration", "Release", "-sdk", "iphonesimulator",
            "-destination", f"id={simulator['udid']}", "-derivedDataPath", out / "benchmarks-ios-derived",
            "ARCHS=arm64", "CODE_SIGNING_ALLOWED=NO", "build",
        ], logs / "xcode-build.log")


def artifact(target, app_build, out):
    if target == "jvm":
        jars = list((app_build / "compose/jars").glob("*-release.jar"))
        if not jars:
            raise RuntimeError("release JVM jar not found; run without --skip-build")
        return max(jars, key=lambda path: path.stat().st_mtime)
    path = {
        "macos": app_build / "bin/macosArm64/releaseExecutable/benchmarks-app.kexe",
        "js": app_build / "dist/js/productionExecutable",
        "wasmJs": app_build / "dist/wasmJs/productionExecutable",
        "ios": out / "benchmarks-ios-derived/Build/Products/Release-iphonesimulator/BenchmarksIosApp.app",
    }[target]
    if not path.exists():
        raise RuntimeError(f"release artifact not found: {path}; run without --skip-build")
    return path


def ensure_free_port(port):
    with socket.socket() as probe:
        try:
            probe.bind(("127.0.0.1", port))
        except OSError as error:
            raise RuntimeError(f"collector port {port} is already in use") from error


def wait_for_collector(process, port):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("collector exited; see logs/collector.log")
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.2):
                return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError("collector did not start within 30 seconds; see logs/collector.log")


@contextmanager
def web_server(directory, port, log_path):
    with log_path.open("a") as log:
        class Handler(SimpleHTTPRequestHandler):
            extensions_map = {**SimpleHTTPRequestHandler.extensions_map, ".wasm": "application/wasm", ".mjs": "text/javascript"}

            def log_message(self, template, *values):
                log.write((template % values) + "\n")
                log.flush()

        server = ThreadingHTTPServer(("127.0.0.1", port), partial(Handler, directory=str(directory)))
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            yield f"http://127.0.0.1:{server.server_port}"
        finally:
            server.shutdown()
            server.server_close()
            worker.join()


@contextmanager
def launch_test(args, app, browser, simulator, collector_url, web_url, name, mode, run_id, log_path):
    parameters = [collector_url, name, mode, run_id]
    if args.target == "ios":
        try:
            command = ["xcrun", "simctl", "launch", "--console", "--terminate-running-process",
                       simulator["udid"], BUNDLE_ID, *parameters]
            with managed_process(command, log_path) as process:
                yield lambda: process.poll() is not None
        finally:
            with log_path.open("ab") as log:
                subprocess.run(["xcrun", "simctl", "terminate", simulator["udid"], BUNDLE_ID],
                               stdout=log, stderr=log, timeout=10)
        return

    with ExitStack() as stack:
        if args.target in ("js", "wasmJs"):
            query = urlencode({"benchmarkCollectorUrl": collector_url, "test": name, "type": mode, "id": run_id})
            url = web_url + "/?" + query
            if args.browser_kind == "safari":
                with launch_safari(browser, url, log_path) as exited:
                    yield exited
                return
            profile = stack.enter_context(tempfile.TemporaryDirectory(prefix="benchmark-browser-"))
            if args.browser_kind == "chromium":
                command = [browser, f"--user-data-dir={profile}", "--no-first-run",
                           "--no-default-browser-check", "--new-window", *args.browser_arg, url]
            else:
                (Path(profile) / "user.js").write_text(
                    'user_pref("browser.shell.checkDefaultBrowser", false);\n'
                    'user_pref("browser.startup.homepage_override.mstone", "ignore");\n'
                )
                command = [browser, "--no-remote", "--profile", profile, "--new-window", *args.browser_arg, url]
        elif args.target == "jvm":
            command = [java_executable(), "-jar", app, *parameters]
        else:
            command = [app, *parameters]
        process = stack.enter_context(managed_process(command, log_path))
        yield lambda: process.poll() is not None


def wait_for_report(reports, run_id, name, mode, deadline, exited, collector):
    while True:
        for path in reports.glob(f"{run_id}-*.json"):
            report = json.loads(path.read_text())  # Collector publishes complete files atomically.
            if report.get("id") != run_id or report.get("name") != name or report.get("type") != mode:
                raise RuntimeError(f"unexpected report contents: {path}")
            return path
        if collector.poll() is not None:
            raise RuntimeError("collector exited; see logs/collector.log")
        if time.monotonic() >= deadline:
            raise TimeoutError("no report before test timeout")
        if exited():
            # A headless app may exit immediately after POST. Check files before calling this a failure.
            for path in reports.glob(f"{run_id}-*.json"):
                report = json.loads(path.read_text())
                if report.get("id") == run_id and report.get("name") == name and report.get("type") == mode:
                    return path
            raise RuntimeError("application exited without a report")
        time.sleep(0.1)


def run_series(args, out, directory, summary):
    logs, reports = directory / "logs", directory / "reports"
    logs.mkdir()
    reports.mkdir()
    app_build = ROOT / "benchmarks-app/build"
    collector_build = ROOT / "benchmarks-collector/build"
    simulator = select_simulator(args.simulator) if args.target == "ios" else None
    browser = executable(args.browser) if args.target in ("js", "wasmJs") else None
    if browser:
        args.browser_kind = detect_browser(browser)
        summary["browser"] = {"kind": args.browser_kind, "executable": browser}
        print(f"Browser: {args.browser_kind} ({browser})", flush=True)
        if args.browser_kind == "safari":
            if sys.platform != "darwin":
                raise RuntimeError("Safari requires macOS")
            if args.browser_arg:
                raise RuntimeError("--browser-arg is not supported for Safari")
            safari_bundle(browser)
            require_safari_closed(browser)
    ensure_free_port(args.port)
    if not args.skip_build:
        build(args, out, logs, simulator)
    app = artifact(args.target, app_build, out)
    if simulator:
        if simulator["state"] != "Booted":
            run_command(["xcrun", "simctl", "boot", simulator["udid"]], logs / "simulator.log")
        run_command(["xcrun", "simctl", "bootstatus", simulator["udid"], "-b"], logs / "simulator.log")
        run_command(["xcrun", "simctl", "install", simulator["udid"], app], logs / "simulator.log")

    collector_url = f"http://127.0.0.1:{args.port}/reports"
    with ExitStack() as stack:
        ensure_free_port(args.port)
        collector = stack.enter_context(managed_process(
            [collector_build / "install/benchmarks-collector/bin/benchmarks-collector", str(args.port), reports],
            logs / "collector.log",
        ))
        wait_for_collector(collector, args.port)
        web_url = None
        if browser:
            web_url = stack.enter_context(web_server(app, args.web_port, logs / "web-server.log"))
        total = len(args.tests) * len(args.modes) * args.repeat
        for name in args.tests:
            for mode in args.modes:
                for iteration in range(1, args.repeat + 1):
                    if collector.poll() is not None:
                        raise RuntimeError("collector exited; see logs/collector.log")
                    if browser and args.browser_kind == "safari":
                        require_safari_closed(browser)
                    run_id = str(uuid.uuid4())
                    log_path = logs / f"{run_id}.log"
                    result = {"id": run_id, "name": name, "type": mode, "iteration": iteration,
                              "status": "interrupted", "log": str(log_path.relative_to(directory))}
                    summary["runs"].append(result)
                    started = time.monotonic()
                    print(f"[{len(summary['runs'])}/{total}] {name} / {mode} / {run_id}", flush=True)
                    try:
                        with launch_test(args, app, browser, simulator, collector_url, web_url,
                                         name, mode, run_id, log_path) as exited:
                            report = wait_for_report(reports, run_id, name, mode,
                                                     started + args.timeout, exited, collector)
                            result.update(status="passed", report=str(report.relative_to(directory)))
                    except Exception as error:
                        result.update(status="failed", error=str(error))
                        with log_path.open("a") as log:
                            log.write(f"\nLauncher: {error}\n")
                            traceback.print_exc(file=log)
                    finally:
                        result["elapsedSeconds"] = round(time.monotonic() - started, 3)
                    print(f"  {result['status']}: {result.get('error', result.get('report', ''))}", flush=True)
                    (directory / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")


def handle_termination(signum, frame):
    raise KeyboardInterrupt


def main():
    args = parse_args()
    signal.signal(signal.SIGTERM, handle_termination)
    if args.compose_core_path:
        args.compose_core_path = args.compose_core_path.expanduser().resolve()
        # Also reaches the Gradle invocation in the Xcode framework build phase.
        os.environ["ORG_GRADLE_PROJECT_composeCorePath"] = str(args.compose_core_path)
    out = Path(os.environ.get("OUT_DIR", ROOT / "out")).expanduser()
    if not out.is_absolute():
        out = ROOT / out
    out = out.resolve()
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    directory = (args.output_dir or out / "benchmark-reports" / f"{stamp}-{args.target}-{uuid.uuid4().hex[:8]}").expanduser().resolve()
    directory.mkdir(parents=True, exist_ok=False)
    summary = {"target": args.target, "tests": args.tests, "modes": args.modes,
               "repeat": args.repeat, "timeoutSeconds": args.timeout, "runs": []}
    if args.compose_core_path:
        summary["composeCorePath"] = str(args.compose_core_path)
    print(f"Results: {directory}", flush=True)
    exit_code = 0
    try:
        run_series(args, out, directory, summary)
        exit_code = int(any(run["status"] != "passed" for run in summary["runs"]))
    except KeyboardInterrupt:
        summary["error"] = "interrupted"
        exit_code = 130
    except Exception as error:
        summary["error"] = str(error)
        print(f"Error: {error}", file=sys.stderr)
        exit_code = 1
    finally:
        (directory / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    passed = sum(run["status"] == "passed" for run in summary["runs"])
    print(f"Finished: {passed}/{len(summary['runs'])} passed. Results: {directory}", flush=True)
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
