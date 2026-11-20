package com.nanolaba.logging

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * The calls of LOG as Kotlin code writes them. The build compiles this file under the kotlin profile, so an overload
 * that makes a call ambiguous for Kotlin fails CI; KotlinCallersTest runs the calls once.
 */
object KotlinCallers {

    private val e = IllegalStateException("boom")

    /**
     * Every shape at every level, without and with an explicit class.
     */
    fun levels() {
        LOG.trace("plain"); LOG.trace("arg {}", 1); LOG.trace { "lazy" }; LOG.trace(e, "with {}", 2); LOG.trace("last {}", 3, e); LOG.trace(e)
        LOG.debug("plain"); LOG.debug("arg {}", 1); LOG.debug { "lazy" }; LOG.debug(e, "with {}", 2); LOG.debug("last {}", 3, e); LOG.debug(e)
        LOG.info("plain"); LOG.info("arg {}", 1); LOG.info { "lazy" }; LOG.info(e, "with {}", 2); LOG.info("last {}", 3, e); LOG.info(e)
        LOG.warn("plain"); LOG.warn("arg {}", 1); LOG.warn { "lazy" }; LOG.warn(e, "with {}", 2); LOG.warn("last {}", 3, e); LOG.warn(e)
        LOG.error("plain"); LOG.error("arg {}", 1); LOG.error { "lazy" }; LOG.error(e, "with {}", 2); LOG.error("last {}", 3, e); LOG.error(e)

        val source = KotlinCallers::class.java
        LOG.info(source, "plain"); LOG.info(source, "arg {}", 1); LOG.info(source) { "lazy" }; LOG.info(source, e, "with {}", 2); LOG.info(source, e)
        LOG.info(42)
        LOG.isDebugEnabled(); LOG.isDebugEnabled(source)
    }

    /**
     * The views and the argument wrappers.
     */
    fun views() {
        LOG.once().warn("once {}", 1)
        LOG.once().warn { "lazy once" }
        LOG.atMostEvery(10, TimeUnit.SECONDS).info("limited {}", 1)
        LOG.tag("audit").info("tagged {}", 1)
        LOG.tag("audit").once().warn(e, "tagged once")
        LOG.info("fields", LOG.kv("orderId", "A-17"), LOG.kv("items", 3))
        LOG.info("lazy {}", LOG.lazy { 6 * 7 })
        LOG.info("secret {}", LOG.secret("token"))
        LOG.info("content {}", LOG.content("prompt"))
    }

    /**
     * The scopes and the blocks.
     */
    fun blocks(): Int {
        LOG.context("requestId", "r-17").use { LOG.info("in a context") }
        LOG.time("load").use { LOG.info("timed") }
        LOG.timed("count") { LOG.info("timed block") }
        // a bare block takes the Runnable overload; the value of the block needs the Supplier constructor
        val counted: Int = LOG.timed("count", Supplier { 3 })
        LOG.unit("checkout").use { unit -> unit.put("items", 3) }
        LOG.unit("import") { LOG.info("in a unit") }
        val imported: Int = LOG.unit("import", Supplier { 4 })
        LOG.unit(LogEntry.LogEntryLevel.WARN, "retried").use { unit -> unit.put("attempt", 2) }
        LOG.unit(LogEntry.LogEntryLevel.WARN, "retried") { LOG.info("in a unit that fails at WARN") }
        val retried: Int = LOG.unit(LogEntry.LogEntryLevel.WARN, "retried", Supplier { 0 })
        LOG.catching { LOG.info("best effort") }
        val parsed: Int = LOG.catching({ "5".toInt() }, -1)
        LOG.wrap(Runnable { LOG.info("task") }).run()
        val called: Int = LOG.wrap(Callable { 6 }).call()
        return counted + imported + retried + parsed + called
    }

    /**
     * Calls that only need to compile: they print at exit or change the whole JVM.
     */
    @Suppress("unused")
    fun compileOnly(value: Int): Int {
        LOG.dbg()
        LOG.dbg(value, "second")
        LOG.setLevel("com.acme", LogEntry.LogEntryLevel.DEBUG)
        LOG.withLevel("com.acme", "TRACE").use { }
        LOG.skipCaller(KotlinCallers::class.java)
        LOG.addContextProvider { mapOf("traceId" to "t-1") }
        LOG.installUncaughtExceptionHandler()
        LOG.init { entry -> println(entry.formattedMessage) }
        LOG.lock()
        return LOG.dbg(value)
    }
}
