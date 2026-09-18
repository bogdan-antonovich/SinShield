package app.sinshield

import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/** Creates explicitly low-priority workers so detection cannot make the foreground app janky. */
internal fun newBackgroundSingleThreadExecutor(name: String): ExecutorService =
    Executors.newSingleThreadExecutor(backgroundThreadFactory(name))

internal fun newBackgroundFixedThreadPool(name: String, threads: Int): ExecutorService =
    Executors.newFixedThreadPool(threads, backgroundThreadFactory(name))

private fun backgroundThreadFactory(name: String): ThreadFactory {
    val nextId = AtomicInteger(1)
    return ThreadFactory { task ->
        Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                task.run()
            },
            "$name-${nextId.getAndIncrement()}"
        )
    }
}
