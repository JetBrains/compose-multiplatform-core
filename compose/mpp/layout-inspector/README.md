# Layout inspector PoC

Four fork-only Kotlin Multiplatform libraries provide transport for future layout inspector tools.

- `:compose:mpp:layout-inspector:session`: the shared JSON protocol, the bidirectional `InspectorSession` contract, and a Ktor WebSocket adapter for accepted or outbound connections. It has no HTTP client/server engine dependencies.
- `:compose:mpp:layout-inspector:agent-core`: the `InspectorAgent` interface and `DefaultInspectorAgent` protocol implementation. They operate on an established session and have no server, port, or connection-provider configuration.
- `:compose:mpp:layout-inspector:agent`: the `ComposeLayoutInspector` convenience facade, which starts an in-process Ktor CIO server and delegates its accepted sessions to an agent.
- `:compose:mpp:layout-inspector:client`: a client with discovery, connection state, ping, and heartbeat APIs.

All four transport libraries and the `runtime-tooling` library target JVM, native macOS on Apple Silicon, and Apple Silicon iOS Simulator. The `demo` module provides a native macOS app and a JVM integration test.

No layout data is collected. There is no CLI, IDE plugin, real-device transport, Intel simulator target, or JS/Wasm target yet. The transport libraries have no Compose Runtime or UI dependencies. The modules are for local development and are unregistered for publication.

## Local Runtime metrics

The `:compose:mpp:layout-inspector:runtime-tooling` library uses Compose Runtime's observer API independently of the transport. Create a `RuntimeScopeInspector(composition)` for the composition you want to observe. Call `snapshot()` to read per-scope counters and `close()` to detach. Runtime allows one observer per composition, and a subcomposition can install its own observer. Attaching before initial composition captures initial visits.

| Metric | What it counts |
|---|---|
| Observed states | Distinct objects reported by the Runtime read callback in the latest observed read pass, deduplicated by identity. Skips preserve the previous count. The value is unknown until there is a read pass to count. |
| Invalidations | Runtime invalidation callbacks for the scope, including direct invalidation. |
| Scope visits | Completed scope-enter and scope-exit pairs. These include first executions, re-executions, skips, and derived-state read replays. |
| Skips | Visits in which Runtime sets the internal skip flag. A flag left set by a previous visit is ignored. |

Scope IDs are local to the collector. Disposed scopes disappear from later snapshots. The macOS demo's **Print scope counts** action writes the current snapshot to stdout.

### Scope outcomes after attachment

The Runtime tooling overload `onScopeExit(scope, outcome)` reports execution or skip for completed restart-group visits. Both GapComposer and LinkComposer use the original scope-enter and scope-exit callbacks when replaying tracked reads for an unchanged derived value. Once the inspector consumes a Runtime build with the overload, it can count executions and skips since attachment and update the observed-state count on execution. The execution count includes any initial executions observed after attachment.

## Agent

For the built-in app-side server, keep a facade instance in the app and start it explicitly from a coroutine:

```kotlin
import androidx.compose.mpp.layoutinspector.ComposeLayoutInspector

suspend fun inspect() {
    val agent = ComposeLayoutInspector()
    val port = agent.start()
    try {
        // Keep the app's inspection session alive here.
    } finally {
        agent.stop()
    }
}
```

`start()` binds `127.0.0.1` on the first available port in `52500..52509` and returns that port. The range is configurable through the constructor. Repeated starts return the running server's port. `stop()` closes its sessions and releases the port; repeated stops are safe. An instance can be started again after stopping.

The agent's `jvm` target is usable by a Compose Desktop application's JVM source set, including a target named `desktop`:

```kotlin
desktopMain.dependencies {
    implementation(project(":compose:mpp:layout-inspector:agent"))
}
```

The same `ComposeLayoutInspector` API starts the server inside the desktop app. Keep the instance alive while the app is being inspected. The host client can discover it on loopback using its default configuration. A desktop app and a simulator app share the same port range; the client selects the first compatible agent across both.

For a transport that establishes the connection elsewhere, depend on `:compose:mpp:layout-inspector:agent-core` and supply an established session:

```kotlin
import androidx.compose.mpp.layoutinspector.DefaultInspectorAgent
import androidx.compose.mpp.layoutinspector.InspectorAgent
import androidx.compose.mpp.layoutinspector.InspectorSession

suspend fun inspectSession(session: InspectorSession) {
    val agent: InspectorAgent = DefaultInspectorAgent()
    agent.run(session)
}
```

`InspectorSession` provides ordered `send`/`receive` operations and `close`. Normal remote closure returns `null` from `receive`; framing errors throw `InspectorProtocolException`, and transport failures propagate. `InspectorAgent.run` handles greetings and commands, suspends for the session lifetime, and closes it on completion, failure, or cancellation. One default agent can serve independent sessions concurrently.

`KtorInspectorSession` adapts a default Ktor `WebSocketSession`, whether accepted by a server or opened through a client. The host sends the first inspector greeting in either case. The agent's protocol implementation therefore works with a server in the target app, on the host, or behind a future relay. Connection establishment and reconnect policy belong to the caller. `ComposeLayoutInspector` accepts an optional `InspectorAgent` for its app-side server.

The host client also uses `InspectorSession` for message exchange. Its current public `connect()` API still discovers and opens outbound connections; an incoming-listener API is not implemented. No browser or Node.js transport is included.

## Host

```kotlin
import androidx.compose.mpp.layoutinspector.LayoutInspectorClient

suspend fun inspectTarget() {
    val client = LayoutInspectorClient()
    try {
        val endpoint = client.connect()
        client.ping()
        // Observe client.state in a coroutine for Connected/Disconnected changes.
    } finally {
        client.disconnect()
    }
}
```

Discovery tries loopback ports in ascending order and selects the first endpoint that returns a `greetingAccepted` message. It skips unreachable endpoints, greeting timeouts, and incompatible replies.

The default per-endpoint discovery timeout is 1 second. Once connected, the client sends a JSON heartbeat ping every 5 seconds and allows 2 seconds for a response. These values are configurable. Ping requests use IDs so concurrent pings can share a connection. Cancellation removes an outstanding request without disconnecting other callers.

The client exposes `StateFlow<InspectorConnectionState>`. Remote closure, malformed responses, and heartbeat failure transition to `Disconnected(cause)` and release the connection. There is no automatic reconnect or rescan: call `connect()` explicitly to try again. A failed discovery throws `InspectorConnectionException`; cancellation preserves coroutine cancellation. To cancel an ongoing discovery, cancel the coroutine running `connect()`.

## Future real-device transport

`InspectorConnectionProvider.acquire()` returns an `InspectorConnectionLease` containing an ordered list of host-reachable `InspectorEndpoint`s. The default `LoopbackConnectionProvider` supplies loopback ports for desktop and simulators with no forwarding resources. `SimulatorConnectionProvider` delegates to the same provider for simulator-specific call sites.

A future usbmuxd provider can create local TCP forwards and return their loopback endpoints. WebSocket handling, protocol negotiation, requests, and heartbeats stay in the client. The client closes each successfully acquired lease once when discovery fails, is cancelled, or its established session ends. The provider owns cleanup of partially acquired resources if `acquire()` itself fails or is cancelled. A lease must keep its endpoint list stable for its lifetime.

## Wire format

The endpoint is `ws://127.0.0.1:<port>/compose-layout-inspector`. Each message is one JSON text message with a `type` discriminator. The host sends the greeting and the agent acknowledges it.

```json
{"type":"greeting"}
{"type":"greetingAccepted"}
```

After the greeting, the agent responds to each `ping` with a `pong` carrying the same ID. Invalid greetings/messages are rejected. Unknown JSON fields are ignored; unknown message types are rejected. The built-in server and client limit incoming WebSocket messages to 1 MiB, and the default agent requires a greeting within 2 seconds.

## Tests

### Native macOS demo

From the repository root on an Apple Silicon Mac, run the demo to open a Compose window with a counter and the inspector's listening port. Closing the window stops the agent.

```sh
./gradlew :compose:mpp:layout-inspector:demo:runDebugExecutableMacosArm64 --console=plain
```

The demo's JVM integration task builds and launches the native app, waits for its listening port, pings it, disconnects, and shuts the app down. Run it from a logged-in macOS desktop session so the app can open its window.

```sh
./gradlew :compose:mpp:layout-inspector:demo:macosDemoIntegrationTest --console=plain
```

### Library tests

Tests were written against unimplemented APIs and run to establish a failing baseline before implementing the transport. Shared agent tests, the client loopback test, and the Runtime collector tests live in `commonTest`. JVM client tests live in `jvmTest`, and the simulator fixture lives in `iosTest`. `InspectorAgentSessionTest` exercises the core through an in-memory session with no server. `OutboundInspectorAgentTest` runs the same agent against a host-side WebSocket server on all three targets, verifying that connection direction does not change protocol roles.

`LayoutInspectorLoopbackTest` starts a real client and agent in the same process, connects over loopback, and checks ping and disconnect. It runs on JVM, macOS ARM64, and iOS Simulator. Use the Gradle task for the target you want to test.

```sh
./gradlew :compose:mpp:layout-inspector:client:macosArm64Test --console=plain
```

```sh
./gradlew :compose:mpp:layout-inspector:client:iosSimulatorArm64Test --console=plain
```

The Kotlin/Native test runner manages simulator boot and shutdown. Pass `-PlayoutInspector.simulatorUdid` to select a specific device.

The client module's ordinary `jvmTest` task includes `DesktopIntegrationTest`. It launches a test-only agent in a separate JVM and checks discovery, greeting, ping/pong, concurrent pings, and notification when the desktop process stops. It requires no simulator or UI. For a desktop-only run, compile `jvmTestClasses` for the agent-core, agent, and client modules, then run their `cleanJvmTest` and `jvmTest` tasks with `--no-build-cache --console=plain`.

Compile before running:

```sh
./gradlew \
  :compose:mpp:layout-inspector:agent-core:jvmTestClasses \
  :compose:mpp:layout-inspector:agent-core:compileTestKotlinMacosArm64 \
  :compose:mpp:layout-inspector:agent-core:compileTestKotlinIosSimulatorArm64 \
  :compose:mpp:layout-inspector:agent:jvmTestClasses \
  :compose:mpp:layout-inspector:agent:compileTestKotlinMacosArm64 \
  :compose:mpp:layout-inspector:agent:compileTestKotlinIosSimulatorArm64 \
  :compose:mpp:layout-inspector:client:jvmTestClasses \
  :compose:mpp:layout-inspector:client:compileTestKotlinMacosArm64 \
  :compose:mpp:layout-inspector:client:compileTestKotlinIosSimulatorArm64 \
  :compose:mpp:layout-inspector:runtime-tooling:jvmTestClasses \
  :compose:mpp:layout-inspector:runtime-tooling:compileTestKotlinMacosArm64 \
  :compose:mpp:layout-inspector:runtime-tooling:compileTestKotlinIosSimulatorArm64 \
  --console=plain
```

Run the JVM, macOS, and iOS Simulator suites together.

```sh
./gradlew \
  :compose:mpp:layout-inspector:agent-core:cleanAllTests \
  :compose:mpp:layout-inspector:agent:cleanAllTests \
  :compose:mpp:layout-inspector:client:cleanAllTests \
  :compose:mpp:layout-inspector:runtime-tooling:cleanAllTests \
  :compose:mpp:layout-inspector:agent-core:jvmTest \
  :compose:mpp:layout-inspector:agent:jvmTest \
  :compose:mpp:layout-inspector:client:jvmTest \
  :compose:mpp:layout-inspector:runtime-tooling:jvmTest \
  :compose:mpp:layout-inspector:agent-core:macosArm64Test \
  :compose:mpp:layout-inspector:agent:macosArm64Test \
  :compose:mpp:layout-inspector:client:macosArm64Test \
  :compose:mpp:layout-inspector:runtime-tooling:macosArm64Test \
  :compose:mpp:layout-inspector:agent-core:iosSimulatorArm64Test \
  :compose:mpp:layout-inspector:agent:iosSimulatorArm64Test \
  :compose:mpp:layout-inspector:client:iosSimulatorArm64Test \
  :compose:mpp:layout-inspector:runtime-tooling:iosSimulatorArm64Test \
  --no-build-cache --console=plain
```

`:compose:mpp:layout-inspector:client:simulatorIntegrationTest` is a separate macOS task that requires a booted simulator. It links the agent's native test executable, launches only `SimulatorAgentFixture.serveHostClient` through `simctl spawn`, and connects from the JVM. It checks discovery, greeting, ping/pong, concurrent pings, and remote-disconnect notification. A temporary stop file controls fixture shutdown and is removed afterward. The fixture does not start during ordinary agent tests. Without the Gradle property, the integration task uses `booted`.
