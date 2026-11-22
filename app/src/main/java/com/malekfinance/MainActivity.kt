package com.malekfinance

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.malekfinance.voice.VoiceTransactionParser
import com.malekfinance.ui.FinanceApp
import com.malekfinance.ui.FinanceColors
import java.text.NumberFormat
import java.util.Locale

@Entity(tableName = "transactions", indices = [Index(value = ["walletId"])])
data class FinanceTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val kind: String,
    val note: String,
    @ColumnInfo(defaultValue = "'عام'") val category: String = "عام",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1") val walletId: Long = 1,
    @ColumnInfo(defaultValue = "'YER'") val currencyCode: String = "YER"
)

@Entity(tableName = "wallets")
data class FinanceWallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currencyCode: String = "YER",
    val openingBalanceMinor: Long = 0,
    val walletType: String = "CASH",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transfers",
    indices = [Index(value = ["fromWalletId"]), Index(value = ["toWalletId"])]
)
data class FinanceTransfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromWalletId: Long,
    val toWalletId: Long,
    val amountMinor: Long,
    val currencyCode: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observe(): Flow<List<FinanceTransaction>>

    @Query("SELECT * FROM transactions WHERE walletId = :walletId")
    suspend fun forWallet(walletId: Long): List<FinanceTransaction>
    @Insert suspend fun insert(item: FinanceTransaction)
    @Update suspend fun update(item: FinanceTransaction)
    @Delete suspend fun delete(item: FinanceTransaction)
}

@Dao
interface FinanceWalletDao {
    @Query("SELECT * FROM wallets ORDER BY createdAt ASC, id ASC")
    fun observe(): Flow<List<FinanceWallet>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(wallet: FinanceWallet): Long

    @Update
    suspend fun update(wallet: FinanceWallet)
}

@Dao
interface FinanceTransferDao {
    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun observe(): Flow<List<FinanceTransfer>>

    @Query("SELECT * FROM transfers WHERE fromWalletId = :walletId OR toWalletId = :walletId")
    suspend fun forWallet(walletId: Long): List<FinanceTransfer>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transfer: FinanceTransfer): Long
}

@Database(
    entities = [FinanceTransaction::class, FinanceWallet::class, FinanceTransfer::class],
    version = 3,
    exportSchema = false
)
abstract class FinanceDb : RoomDatabase() {
    abstract fun dao(): FinanceDao
    abstract fun walletDao(): FinanceWalletDao
    abstract fun transferDao(): FinanceTransferDao

    companion object {
        private const val DEFAULT_WALLET_SQL =
            "INSERT OR IGNORE INTO wallets(id, name, currencyCode, openingBalanceMinor, walletType, createdAt) " +
                "VALUES(1, 'المحفظة الرئيسية', 'YER', 0, 'CASH', CAST(strftime('%s','now') AS INTEGER) * 1000)"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT NOT NULL DEFAULT 'عام'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS wallets (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, currencyCode TEXT NOT NULL, " +
                        "openingBalanceMinor INTEGER NOT NULL, walletType TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(DEFAULT_WALLET_SQL)
                db.execSQL("ALTER TABLE transactions ADD COLUMN walletId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE transactions ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'YER'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_walletId ON transactions(walletId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS transfers (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "fromWalletId INTEGER NOT NULL, toWalletId INTEGER NOT NULL, " +
                        "amountMinor INTEGER NOT NULL, currencyCode TEXT NOT NULL, " +
                        "note TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_fromWalletId ON transfers(fromWalletId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_toWalletId ON transfers(toWalletId)")
            }
        }

        fun seedDefaultWallet(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS wallets (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, currencyCode TEXT NOT NULL, " +
                    "openingBalanceMinor INTEGER NOT NULL, walletType TEXT NOT NULL, createdAt INTEGER NOT NULL)"
            )
            db.execSQL(DEFAULT_WALLET_SQL)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, FinanceDb::class.java, "malek-finance.db")
            .addMigrations(FinanceDb.MIGRATION_1_2, FinanceDb.MIGRATION_2_3)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    FinanceDb.seedDefaultWallet(db)
                }
            })
            .build()
        setContent { FinanceApp(db) }
    }
}

private val background = FinanceColors.Background
private val panel = FinanceColors.Surface
private val purple = FinanceColors.Primary
private val muted = FinanceColors.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinanceHome(db: FinanceDb, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows by db.dao().observe().collectAsState(initial = emptyList())
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("EXPENSE") }
    var category by remember { mutableStateOf("عام") }
    var status by remember { mutableStateOf("بياناتك محفوظة محلياً على جهازك") }
    var transcript by remember { mutableStateOf("") }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!words.isNullOrBlank()) {
            val parsed = VoiceTransactionParser.parse(words)
            transcript = words
            amount = parsed.amount.orEmpty()
            note = parsed.description
            kind = parsed.kind ?: kind
            category = parsed.category
            status = if (parsed.amount == null) "لم أتعرف على المبلغ؛ أدخله يدوياً ثم راجع البيانات" else "راجع نوع المعاملة والمبلغ والوصف قبل الحفظ"
        } else {
            status = "لم يتم التقاط كلام واضح"
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceRecognition(voice)
        else status = "لم يتم منح إذن الميكروفون"
    }

    val income = rows.filter { it.kind == "INCOME" }.sumOf { it.amountMinor }
    val expenses = rows.filter { it.kind == "EXPENSE" }.sumOf { it.amountMinor }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = darkColorScheme(primary = purple, background = background, surface = panel)) {
            Surface(color = background, modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 28.dp, bottom = 32.dp)
                ) {
                    item {
                        Text("مالك فاينانس", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("أموالك تحت سيطرتك، بصوتك فقط", color = muted)
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                Text("الرصيد الحالي", color = muted)
                                Text(money(income - expenses), style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column { Text("إجمالي الدخل", color = muted); Text(money(income), color = Color(0xFF69D6A2), fontWeight = FontWeight.Bold) }
                                    Column { Text("إجمالي المصروفات", color = muted); Text(money(expenses), color = Color(0xFFFF8A8A), fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    startVoiceRecognition(voice)
                                } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = purple)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "الميكروفون")
                            Spacer(Modifier.width(8.dp))
                            Text("أضف معاملة بصوتك", fontWeight = FontWeight.Bold)
                        }
                    }
                    item { Text("إضافة معاملة", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = kind == "EXPENSE", onClick = { kind = "EXPENSE" }, label = { Text("مصروف") })
                            FilterChip(selected = kind == "INCOME", onClick = { kind = "INCOME" }, label = { Text("دخل") })
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("المبلغ بالريال اليمني") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الوصف") })
                    }
                    item {
                        Text("التصنيف", color = muted)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("عام", "بقالة", "مطاعم", "مواصلات", "بنزين", "فواتير", "راتب")) { item ->
                                FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                            }
                        }
                    }
                    item {
                        Text(status, color = muted, style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = {
                                val normalized = normalizeDigits(amount).replace('٫', '.').replace(',', '.').trim()
                                val value = normalized.toBigDecimalOrNull()
                                val minor = runCatching { value?.movePointRight(2)?.longValueExact() }.getOrNull()
                                if (minor == null || minor <= 0L || note.isBlank()) {
                                    status = "أدخل مبلغاً صحيحاً أكبر من صفر ووصفاً للمعاملة"
                                } else {
                                    scope.launch {
                                        db.dao().insert(FinanceTransaction(amountMinor = minor, kind = kind, note = note.trim(), category = category))
                                        amount = ""; note = ""; transcript = ""
                                        status = "تم حفظ المعاملة على جهازك"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = purple)
                        ) { Text("تأكيد وحفظ المعاملة") }
                    }
                    item { Text("آخر المعاملات", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    if (rows.isEmpty()) {
                        item { Text("لا توجد معاملات بعد. أضف أول دخل أو مصروف.", color = muted) }
                    } else {
                        items(rows, key = { it.id }) { transaction ->
                            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(transaction.note.ifBlank { "معاملة مالية" }, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text(transaction.category + " • " + if (transaction.kind == "INCOME") "دخل" else "مصروف", color = muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text((if (transaction.kind == "INCOME") "+" else "−") + money(transaction.amountMinor), color = if (transaction.kind == "INCOME") Color(0xFF69D6A2) else Color(0xFFFF8A8A), fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { scope.launch { db.dao().delete(transaction) } }) { Text("حذف") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun startVoiceRecognition(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-YE")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "مثال: صرفت ١٥٠٠ ريال بنزين")
    }
    try { launcher.launch(intent) } catch (_: Exception) { /* Handled by device capability status in the UI. */ }
}

private fun normalizeDigits(value: String): String {
    val arabic = "٠١٢٣٤٥٦٧٨٩"
    val persian = "۰۱۲۳۴۵۶۷۸۹"
    return buildString {
        value.forEach { ch ->
            val a = arabic.indexOf(ch); val p = persian.indexOf(ch)
            append(if (a >= 0) a else if (p >= 0) p else ch)
        }
    }
}

private fun money(valueMinor: Long): String =
    NumberFormat.getNumberInstance(Locale("ar")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(java.math.BigDecimal.valueOf(valueMinor, 2)) + " ر.ي"
