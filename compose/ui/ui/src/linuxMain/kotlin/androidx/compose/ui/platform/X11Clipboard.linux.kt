/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.platform

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.POLLIN
import platform.posix.poll
import platform.posix.pollfd
import x11gl.Atom
import x11gl.AtomVar
import x11gl.Display
import x11gl.SelectionNotify
import x11gl.Window
import x11gl.XChangeProperty
import x11gl.XCheckTypedWindowEvent
import x11gl.XConnectionNumber
import x11gl.XConvertSelection
import x11gl.XDeleteProperty
import x11gl.XEvent
import x11gl.XFlush
import x11gl.XFree
import x11gl.XGetSelectionOwner
import x11gl.XGetWindowProperty
import x11gl.XInternAtom
import x11gl.XSelectionClearEvent
import x11gl.XSelectionRequestEvent
import x11gl.XSendEvent
import x11gl.XSetSelectionOwner

/** `((Atom) 4)` in Xatom.h; the cast macro is not exposed by cinterop. */
private val XA_ATOM: Atom = 4UL

/** `((Atom) 31)` in Xatom.h; the cast macro is not exposed by cinterop. */
private val XA_STRING: Atom = 31UL

/** `None`/`CurrentTime`/`AnyPropertyType` all expand to 0 with X11's resource types. */
private val NONE: ULong = 0UL

/** `PropModeReplace` in X.h. */
private const val PROP_MODE_REPLACE = 0

/** How long a paste waits for the selection owner to answer [XConvertSelection]. */
private val SELECTION_TIMEOUT = 500.milliseconds

/**
 * The X11 `CLIPBOARD` selection, shared backend for [Clipboard] and [ClipboardManager].
 *
 * Copy claims selection ownership with [XSetSelectionOwner] and keeps the text in-process;
 * the window event loop forwards `SelectionRequest`/`SelectionClear` events here so other
 * clients can fetch it ([handleSelectionRequest]) and takeovers drop it
 * ([handleSelectionClear]). Paste short-circuits to the in-process text when any registered
 * window owns the selection (this also avoids deadlocking on a window served by the same
 * event loop); otherwise it issues [XConvertSelection] and blocks on the display connection
 * for up to [SELECTION_TIMEOUT] waiting for `SelectionNotify`, trying `UTF8_STRING` first
 * and falling back to `STRING` (Latin-1) for legacy owners.
 *
 * Main-thread confined, like the event loop that drives it. INCR (multi-chunk) transfers
 * are not supported: pastes larger than the server's transfer limit (hundreds of KB) fail,
 * and copies that large may be refused by the server.
 */
internal object X11Clipboard {

    private class Binding(val display: CPointer<Display>, val window: Window) {
        val clipboard: Atom = XInternAtom(display, "CLIPBOARD", 0)
        val utf8String: Atom = XInternAtom(display, "UTF8_STRING", 0)
        val targets: Atom = XInternAtom(display, "TARGETS", 0)
        val text: Atom = XInternAtom(display, "TEXT", 0)
        val incr: Atom = XInternAtom(display, "INCR", 0)

        /** Destination property on [window] for incoming selection transfers. */
        val transferProperty: Atom = XInternAtom(display, "COMPOSE_CLIPBOARD", 0)
    }

    private enum class Conversion { DELIVERED, REFUSED, TIMED_OUT }

    private val bindings = mutableListOf<Binding>()

    /** The binding whose window currently owns the CLIPBOARD selection, if any. */
    private var owner: Binding? = null

    /** The text served to selection requests while [owner] is non-null. */
    private var ownedText: String? = null

    /** Registers a window's display connection as a clipboard endpoint. */
    fun register(display: CPointer<Display>, window: Window) {
        bindings.add(Binding(display, window))
    }

    /**
     * Forgets [window]. X drops any selection ownership when the window is destroyed,
     * so a pending copy dies with it (standard for apps without a clipboard manager).
     */
    fun unregister(window: Window) {
        bindings.removeAll { it.window == window }
        if (owner?.window == window) owner = null
    }

    /** Stores [text] and claims the CLIPBOARD selection; `null` releases both. */
    fun setText(text: String?) {
        ownedText = text
        val binding = bindings.firstOrNull() ?: return
        if (text != null) {
            XSetSelectionOwner(binding.display, binding.clipboard, binding.window, NONE)
            owner = binding.takeIf {
                XGetSelectionOwner(it.display, it.clipboard) == it.window
            }
        } else if (owner != null) {
            XSetSelectionOwner(binding.display, binding.clipboard, NONE, NONE)
            owner = null
        }
        XFlush(binding.display)
    }

    /** Returns the CLIPBOARD text, or `null` when it is empty or not convertible to text. */
    fun getText(): String? {
        val binding = bindings.firstOrNull() ?: return ownedText
        val selectionOwner = XGetSelectionOwner(binding.display, binding.clipboard)
        return when {
            selectionOwner == NONE -> null
            bindings.any { it.window == selectionOwner } -> ownedText
            else -> fetchRemoteText(binding)
        }
    }

    /**
     * Whether a paste would plausibly yield text. For an external owner this reports `true`
     * without fetching TARGETS; it only gates edit menus, so optimistic is fine.
     */
    fun hasText(): Boolean {
        val binding = bindings.firstOrNull() ?: return !ownedText.isNullOrEmpty()
        val selectionOwner = XGetSelectionOwner(binding.display, binding.clipboard)
        return when {
            selectionOwner == NONE -> false
            bindings.any { it.window == selectionOwner } -> !ownedText.isNullOrEmpty()
            else -> true
        }
    }

    /**
     * Serves our clipboard text to another client. Supports the `TARGETS` introspection
     * target plus `UTF8_STRING`/`TEXT` (UTF-8) and `STRING` (Latin-1); anything else is
     * refused by replying with a `None` property, as ICCCM prescribes.
     */
    fun handleSelectionRequest(request: XSelectionRequestEvent) {
        val binding = bindings.firstOrNull { it.window == request.owner } ?: return
        val text = ownedText.takeIf { request.selection == binding.clipboard }
        // Obsolete (pre-ICCCM) requestors pass property None, meaning "use the target atom".
        val property = if (request.property == NONE) request.target else request.property
        val delivered = text != null && when (request.target) {
            binding.targets -> writeTargets(binding, request.requestor, property)
            binding.utf8String, binding.text ->
                writeText(binding, request.requestor, property, binding.utf8String, text.encodeToByteArray())
            XA_STRING ->
                writeText(binding, request.requestor, property, XA_STRING, text.encodeLatin1())
            else -> false
        }
        sendSelectionNotify(binding, request, if (delivered) property else NONE)
    }

    /** Another client took the selection; our copy is stale, drop it. */
    fun handleSelectionClear(event: XSelectionClearEvent) {
        val binding = owner ?: return
        if (event.selection == binding.clipboard && event.window == binding.window) {
            owner = null
            ownedText = null
        }
    }

    /** Fetches from an external owner: `UTF8_STRING` first, `STRING` if that is refused. */
    private fun fetchRemoteText(binding: Binding): String? =
        when (convertSelection(binding, binding.utf8String)) {
            Conversion.DELIVERED -> readTransferProperty(binding)
            Conversion.REFUSED ->
                when (convertSelection(binding, XA_STRING)) {
                    Conversion.DELIVERED -> readTransferProperty(binding)
                    else -> null
                }
            Conversion.TIMED_OUT -> null
        }

    /**
     * Asks the selection owner to write [target]-typed data into [Binding.transferProperty]
     * and waits for its `SelectionNotify` answer. Only `SelectionNotify` is consumed from
     * the queue; all other events stay put for the main event loop.
     */
    private fun convertSelection(binding: Binding, target: Atom): Conversion {
        XDeleteProperty(binding.display, binding.window, binding.transferProperty)
        XConvertSelection(
            binding.display,
            binding.clipboard,
            target,
            binding.transferProperty,
            binding.window,
            NONE,
        )
        XFlush(binding.display)
        memScoped {
            val event = alloc<XEvent>()
            val start = TimeSource.Monotonic.markNow()
            while (start.elapsedNow() < SELECTION_TIMEOUT) {
                val found = XCheckTypedWindowEvent(
                    binding.display,
                    binding.window,
                    SelectionNotify,
                    event.ptr,
                )
                when {
                    found == 0 -> pollConnection(binding.display, timeoutMs = 10)
                    event.xselection.selection != binding.clipboard -> {}
                    event.xselection.property == NONE -> return Conversion.REFUSED
                    else -> return Conversion.DELIVERED
                }
            }
        }
        return Conversion.TIMED_OUT
    }

    private fun readTransferProperty(binding: Binding): String? = memScoped {
        val actualType = alloc<AtomVar>()
        val actualFormat = alloc<IntVar>()
        val itemCount = alloc<ULongVar>()
        val bytesLeft = alloc<ULongVar>()
        val data = alloc<CPointerVar<UByteVar>>()
        val status = XGetWindowProperty(
            binding.display,
            binding.window,
            binding.transferProperty,
            0,
            // Length is in 32-bit multiples; this caps a transfer at 4 MiB.
            (4 * 1024 * 1024) / 4,
            1, // delete the property once read
            NONE, // AnyPropertyType
            actualType.ptr,
            actualFormat.ptr,
            itemCount.ptr,
            bytesLeft.ptr,
            data.ptr,
        )
        val pointer = data.value
        val bytes = pointer
            ?.takeIf { status == 0 && actualFormat.value == 8 && actualType.value != binding.incr }
            ?.readBytes(itemCount.value.toInt())
        pointer?.let { XFree(it) }
        when {
            bytes == null -> null
            actualType.value == XA_STRING -> bytes.decodeLatin1()
            else -> bytes.decodeToString()
        }
    }

    private fun writeTargets(binding: Binding, requestor: Window, property: Atom): Boolean =
        memScoped {
            val supported = listOf(binding.targets, binding.utf8String, binding.text, XA_STRING)
            val atoms = allocArray<AtomVar>(supported.size) { index ->
                value = supported[index.toInt()]
            }
            XChangeProperty(
                binding.display,
                requestor,
                property,
                XA_ATOM,
                32,
                PROP_MODE_REPLACE,
                atoms.reinterpret(),
                supported.size,
            )
            true
        }

    private fun writeText(
        binding: Binding,
        requestor: Window,
        property: Atom,
        type: Atom,
        bytes: ByteArray,
    ): Boolean {
        if (bytes.isEmpty()) {
            XChangeProperty(
                binding.display, requestor, property, type, 8, PROP_MODE_REPLACE, null, 0,
            )
        } else {
            bytes.asUByteArray().usePinned { pinned ->
                XChangeProperty(
                    binding.display,
                    requestor,
                    property,
                    type,
                    8,
                    PROP_MODE_REPLACE,
                    pinned.addressOf(0),
                    bytes.size,
                )
            }
        }
        return true
    }

    /** Answers a `SelectionRequest`; [property] of `None` means the conversion was refused. */
    private fun sendSelectionNotify(
        binding: Binding,
        request: XSelectionRequestEvent,
        property: Atom,
    ): Unit = memScoped {
        val reply = alloc<XEvent>()
        reply.xselection.apply {
            type = SelectionNotify
            display = request.display
            requestor = request.requestor
            selection = request.selection
            target = request.target
            time = request.time
        }
        reply.xselection.property = property
        XSendEvent(binding.display, request.requestor, 0, 0L, reply.ptr)
        XFlush(binding.display)
    }

    /** Sleeps until the display connection is readable or [timeoutMs] passes. */
    private fun pollConnection(display: CPointer<Display>, timeoutMs: Int): Unit = memScoped {
        val fd = alloc<pollfd>().apply {
            this.fd = XConnectionNumber(display)
            events = POLLIN.toShort()
        }
        poll(fd.ptr, 1u, timeoutMs)
    }
}

/**
 * Decodes X11 `STRING` (Latin-1) bytes, where each byte is the identically numbered
 * Unicode code point.
 */
internal fun ByteArray.decodeLatin1(): String =
    CharArray(size) { (this[it].toInt() and 0xFF).toChar() }.concatToString()

/**
 * Encodes for the X11 `STRING` (Latin-1) target; code points above U+00FF become `?`,
 * matching what legacy requestors can represent.
 */
internal fun String.encodeLatin1(): ByteArray =
    ByteArray(length) { index ->
        this[index].code.let { if (it < 256) it.toByte() else '?'.code.toByte() }
    }
