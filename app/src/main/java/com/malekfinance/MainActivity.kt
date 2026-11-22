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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Entity(tableName="transactions")
data class FinanceTransaction(@PrimaryKey(autoGenerate=true) val id:Long=0,val amountMinor:Long,val kind:String,val note:String,val createdAt:Long=System.currentTimeMillis())
@Dao interface FinanceDao {
 @Query("SELECT * FROM transactions ORDER BY createdAt DESC") fun observe():Flow<List<FinanceTransaction>>
 @Insert suspend fun insert(item:FinanceTransaction)
}
@Database(entities=[FinanceTransaction::class],version=1,exportSchema=false)
abstract class FinanceDb:RoomDatabase(){abstract fun dao():FinanceDao}

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val db=Room.databaseBuilder(applicationContext,FinanceDb::class.java,"malek-finance.db").build()
  setContent{MaterialTheme{FinanceHome(db)}}
 }
}
private val bg=Color(0xFF0D0D12)
private val panel=Color(0xFF191923)
private val purple=Color(0xFFA855F7)

@Composable private fun FinanceHome(db:FinanceDb){
 val ctx=LocalContext.current
 val scope= rememberCoroutineScope()
 val rows by db.dao().observe().collectAsState(initial= emptyList())
 var amount by remember{mutableStateOf("")}
 var note by remember{mutableStateOf("")}
 var kind by remember{mutableStateOf("EXPENSE")}
 var status by remember{mutableStateOf("بياناتك محفوظة محلياً على جهازك")}
 var transcript by remember{mutableStateOf("")}
 val voice=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){res->
  val words=res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
  if(!words.isNullOrBlank()){transcript=words;note=words
   Regex("""[0-9٠-٩]+(?:[.,٫][0-9٠-٩]+)?""").find(words)?.value?.let{amount=it;status="راجع المبلغ والوصف قبل الحفظ"} ?: run{status="أدخل المبلغ يدوياً ثم راجع النص"}
  }
 }
 val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ok->
  if(ok) voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-YE")})
  else status="لم يتم منح إذن الميكروفون"
 }
 val income=rows.filter{it.kind=="INCOME"}.sumOf{it.amountMinor}
 val spent=rows.filter{it.kind=="EXPENSE"}.sumOf{it.amountMinor}
 Surface(color=bg,modifier=Modifier.fillMaxSize()){
  Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text("مالك فاينانس",style=MaterialTheme.typography.headlineMedium,color=Color.White,fontWeight=FontWeight.Bold)
   Text("أموالك تحت سيطرتك، بصوتك فقط",color=Color.LightGray)
   Card(colors=CardDefaults.cardColors(containerColor=panel)){
    Column(Modifier.fillMaxWidth().padding(16.dp)){
     Text("الرصيد الحالي",color=Color.LightGray)
     Text(money(income-spent),style=MaterialTheme.typography.headlineLarge,color=Color.White,fontWeight=FontWeight.Bold)
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
      Text("الدخل: "+money(income),color=Color(0xFF69D6A2))
      Text("المصروفات: "+money(spent),color=Color(0xFFFF8A8A))
     }
    }
   }
   Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
    FilterChip(selected=kind=="EXPENSE",onClick={kind="EXPENSE"},label={Text("مصروف")})
    FilterChip(selected=kind=="INCOME",onClick={kind="INCOME"},label={Text("دخل")})
    Spacer(Modifier.weight(1f))
    Button(onClick={if(ContextCompat.checkSelfPermission(ctx,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-YE")}) else mic.launch(Manifest.permission.RECORD_AUDIO)}){
     Icon(Icons.Default.Mic,contentDescription="الميكروفون");Text("تحدث")
    }
   }
   if(transcript.isNotBlank()) Text("النص المسموع: $transcript",color=Color.LightGray)
   OutlinedTextField(amount,{amount=it},Modifier.fillMaxWidth(),label={Text("المبلغ بالريال اليمني")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
   OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text("الوصف")})
   Text(status,color=Color.LightGray,style=MaterialTheme.typography.bodySmall)
   Button(onClick={
    val raw=amount.trim().replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9').replace('٫','.').replace(',','.')
    val parsed=raw.toBigDecimalOrNull()
    val minor=runCatching{parsed?.movePointRight(2)?.longValueExact()}.getOrNull()
    if(minor==null||minor<=0) status="أدخل مبلغاً صحيحاً أكبر من صفر"
    else scope.launch{db.dao().insert(FinanceTransaction(amountMinor=minor,kind=kind,note=note.trim()));amount="";note="";transcript="";status="تم حفظ المعاملة"}
   },Modifier.fillMaxWidth()){Text("حفظ المعاملة")}
   Text("آخر المعاملات",color=Color.White,fontWeight=FontWeight.Bold)
   LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){
    items(rows,key={it.id}){item->Card(colors=CardDefaults.cardColors(containerColor=panel)){
     Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){
      Text(item.note.ifBlank{"معاملة مالية"},color=Color.White)
      Text((if(item.kind=="INCOME") "+" else "−")+money(item.amountMinor),color=if(item.kind=="INCOME") Color(0xFF69D6A2) else Color(0xFFFF8A8A))
     }
    }}
   }
  }
 }
}
private fun money(v:Long)=NumberFormat.getNumberInstance(Locale("ar")).format(v/100.0)+" ر.ي"
