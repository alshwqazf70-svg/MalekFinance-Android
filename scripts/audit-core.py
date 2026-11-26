#!/usr/bin/env python3
"""Fallback audit when Android/Gradle is unavailable; NOT an Android/JUnit build.

Uses original domain/policy sources and tests, minimal Room annotations/transaction
stubs, and a small assertion shim. Does not validate Room SQL, Compose, PDF,
backup crypto or native speech. CSV/XLSX code is copied unchanged but writePdf is
removed from the temporary exporter. No generated files are written into Git.

KOTLINC=/path/to/kotlinc JAVA_HOME=/path/to/jdk python3 scripts/audit-core.py
The Kotlin distribution must include kotlinx-coroutines-core-jvm.jar.
"""
import os
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / 'app/src/main/java/com/malekfinance'
TEST = ROOT / 'app/src/test/java'
kotlinc = Path(os.environ['KOTLINC']).resolve()
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java')
coroutines = kotlinc.parent.parent / 'lib/kotlinx-coroutines-core-jvm.jar'
assert coroutines.is_file(), 'Kotlin distribution is missing coroutines jar'

with tempfile.TemporaryDirectory(prefix='finance-audit-') as directory:
    temp = Path(directory)
    def write(name, text):
        p = temp / name
        p.write_text(text)
        return str(p)
    files = [write('Room.kt', '''package androidx.room
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [])
annotation class Index(val value: Array<String>, val unique: Boolean = false)
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class ColumnInfo(val defaultValue: String = "")
annotation class Dao
annotation class Insert(val onConflict: Int = 0)
annotation class Update
annotation class Delete
annotation class Query(val value: String)
object OnConflictStrategy { const val ABORT = 0 }
open class RoomDatabase
suspend fun <T> RoomDatabase.withTransaction(block: suspend () -> T): T = block()
'''), write('Assertions.kt', '''package org.junit
import kotlin.reflect.KClass
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Test(val expected: KClass<out Throwable> = None::class) { class None : Throwable() }
object Assert {
    fun assertEquals(expected: Any?, actual: Any?) { check(expected == actual) { "Expected <$expected>, actual <$actual>" } }
    fun assertTrue(value: Boolean) { check(value) { "Expected true" } }
    fun assertFalse(value: Boolean) { check(!value) { "Expected false" } }
    fun assertNull(value: Any?) { check(value == null) { "Expected null: $value" } }
    fun assertNotNull(value: Any?) { check(value != null) { "Expected non-null" } }
}
''')]
    activity = (MAIN / 'MainActivity.kt').read_text()
    models = activity[activity.index('@Entity'):activity.index('@Database')]
    db = activity[activity.index('abstract class FinanceDb'):activity.index('    companion object {')]
    files.append(write('ModelsAndDb.kt', 'package com.malekfinance\nimport androidx.room.*\nimport kotlinx.coroutines.flow.Flow\n' + models + db + '}\n'))
    excluded = {'MainActivity.kt', 'FinanceBackupManager.kt', 'FinanceReportExporter.kt', 'FinanceDatabaseProvider.kt'}
    files += [str(p) for p in sorted(MAIN.glob('*.kt')) if p.name not in excluded]
    files += [str(MAIN / 'voice' / name) for name in ['VoiceTransactionParser.kt', 'VoiceQueryEngine.kt', 'SpeechCompatibility.kt']]
    exporter = (MAIN / 'FinanceReportExporter.kt').read_text()
    exporter = '\n'.join(line for line in exporter.splitlines() if not line.startswith('import android.'))
    a, b = exporter.index('    fun writePdf('), exporter.index('    private fun decimalAmount(')
    files.append(write('CsvXlsxExporter.kt', exporter[:a] + exporter[b:]))
    # Android framework/shadow tests run in real Gradle/Robolectric, not this shim.
    android_tests = {'SpeechIntentAndroidTest.kt', 'SystemSpeechLifecycleAndroidTest.kt'}
    tests = [p for p in sorted(TEST.rglob('*Test.kt')) if p.name not in android_tests]
    classes = []
    for p in tests:
        source = p.read_text()
        assert '@Before' not in source and '@After' not in source and '@RunWith' not in source, 'Unsupported test lifecycle'
        package = re.search(r'^package (.+)$', source, re.M)[1]
        cls = re.search(r'^class (\w+)', source, re.M)[1]
        classes.append(f'{package}.{cls}::class.java')
        files.append(str(p))
    files.append(write('Runner.kt', '''import org.junit.Test
fun main() {
    var passed = 0
    var failed = 0
    for (cls in listOf(''' + ','.join(classes) + ''')) {
        for (method in cls.declaredMethods.filter { it.getAnnotation(Test::class.java) != null }.sortedBy { it.name }) {
            val expected = method.getAnnotation(Test::class.java).expected.java
            var thrown: Throwable? = null
            try { method.invoke(cls.getDeclaredConstructor().newInstance()) }
            catch (e: java.lang.reflect.InvocationTargetException) { thrown = e.cause }
            if (if (expected == Test.None::class.java) thrown == null else thrown != null && expected.isInstance(thrown)) passed++
            else { failed++; println("FAIL ${cls.simpleName}.${method.name}: $thrown") }
        }
    }
    println("$passed passed; $failed failed (isolated JVM assertion shim; NOT Gradle/JUnit/Android)")
    check(failed == 0)
}
'''))
    jar = str(temp / 'checks.jar')
    subprocess.run(['bash', str(kotlinc), *files, '-cp', str(coroutines), '-include-runtime', '-d', jar], check=True)
    for zone in ['UTC', 'Asia/Aden', 'America/New_York']:
        print('Timezone:', zone, flush=True)
        subprocess.run([java, '-Duser.timezone=' + zone, '-cp', jar + os.pathsep + str(coroutines), 'RunnerKt'], check=True)
