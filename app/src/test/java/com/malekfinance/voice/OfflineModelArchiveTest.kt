package com.malekfinance.voice

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModelArchiveTest {
    private fun archive(vararg entries: Pair<String, ByteArray>) = ByteArrayInputStream(ByteArrayOutputStream().apply {
        ZipOutputStream(this).use { zip -> entries.forEach { (name, data) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
        } }
    }.toByteArray())

    private fun inTemp(test: (File, File) -> Unit) {
        val base = Files.createTempDirectory("voice-model-test").toFile()
        try { test(base, File(base, "staging")) } finally { base.deleteRecursively() }
    }

    @Test fun acceptsRootModel() = inTemp { _, staging ->
        val root = OfflineModelArchive.extract(archive("am/final.mdl" to byteArrayOf(1), "conf/mfcc.conf" to byteArrayOf(2)), staging)
        assertEquals(staging, root)
        assertTrue(File(root, "am/final.mdl").isFile)
    }

    @Test fun acceptsOneEnclosingFolder() = inTemp { _, staging ->
        val root = OfflineModelArchive.extract(archive("arabic/am/final.mdl" to byteArrayOf(1), "arabic/conf/mfcc.conf" to byteArrayOf(2)), staging)
        assertEquals(File(staging, "arabic"), root)
    }

    @Test fun rejectsPathTraversalAndCleansStaging() = inTemp { base, staging ->
        val failed = runCatching { OfflineModelArchive.extract(archive("../escaped" to byteArrayOf(1)), staging) }
        assertTrue(failed.isFailure)
        assertFalse(File(base, "escaped").exists())
        assertFalse(staging.exists())
    }

    @Test fun rejectsAbsolutePaths() = inTemp { _, staging ->
        assertTrue(runCatching { OfflineModelArchive.extract(archive("/absolute" to byteArrayOf(1)), staging) }.isFailure)
        assertFalse(staging.exists())
    }

    @Test fun enforcesExpandedSizeLimitNotZipMetadata() = inTemp { _, staging ->
        assertTrue(runCatching { OfflineModelArchive.extract(archive("am/final.mdl" to ByteArray(5000)), staging, maxBytes = 1024) }.isFailure)
        assertFalse(staging.exists())
    }

    @Test fun rejectsUnrelatedOrTruncatedArchives() = inTemp { _, staging ->
        assertTrue(runCatching { OfflineModelArchive.extract(archive("readme.txt" to byteArrayOf(1)), staging) }.isFailure)
        assertFalse(staging.exists())
        assertTrue(runCatching { OfflineModelArchive.extract(ByteArrayInputStream(byteArrayOf(1, 2, 3)), staging) }.isFailure)
    }

    @Test fun neverDeletesAnExistingDirectory() = inTemp { _, staging ->
        staging.mkdirs()
        File(staging, "existing").writeText("keep")
        assertTrue(runCatching { OfflineModelArchive.extract(archive(), staging) }.isFailure)
        assertEquals("keep", File(staging, "existing").readText())
    }
}
