package com.malekfinance.voice

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Extracts only into a fresh staging folder. Never trusts ZIP paths or advertised sizes. */
object OfflineModelArchive {
    const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_ENTRIES = 20_000

    fun extract(input: InputStream, staging: File, maxBytes: Long = MAX_BYTES): File {
        require(!staging.exists()) { "Staging directory must be new" }
        check(staging.mkdirs())
        try {
            var total = 0L
            var count = 0
            ZipInputStream(input).use { zip ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++count <= MAX_ENTRIES) { "Too many model files" }
                    require(!entry.name.startsWith('/') && !entry.name.contains('\\')) { "Invalid model path" }
                    val target = File(staging, entry.name)
                    require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "Unsafe ZIP path" }
                    if (entry.isDirectory) check(target.mkdirs() || target.isDirectory)
                    else {
                        check(!target.exists()) { "Duplicate model file" }
                        check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
                        target.outputStream().use { output ->
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                total += read
                                require(total <= maxBytes) { "Model archive exceeds size limit" }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            // Accept a model at the ZIP root or in one enclosing directory.
            val candidates = listOf(staging) + staging.listFiles().orEmpty().filter { it.isDirectory }
            return candidates.singleOrNull { File(it, "am/final.mdl").isFile && File(it, "conf/mfcc.conf").isFile }
                ?: error("Not a supported Vosk model archive")
        } catch (failure: Throwable) {
            staging.deleteRecursively()
            throw failure
        }
    }
}
