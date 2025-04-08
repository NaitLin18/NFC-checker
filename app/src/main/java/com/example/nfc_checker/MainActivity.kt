package com.example.nfc_checker

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import com.example.nfc_checker.ui.theme.NFC_CheckerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response


class MainActivity : ComponentActivity() {
    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

    // NFC variables
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null

    object NFCState {
        var screenState by mutableStateOf("select_issue")
        var selectedIssue by mutableStateOf<String?>(null)
        var description by mutableStateOf("")
        var imageUris = mutableStateListOf<Uri>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC не поддерживается на этом устройстве", Toast.LENGTH_SHORT)
                .show()
            finish()
            return
        }

        // Create a PendingIntent to handle NFC intents
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        // Create intent filters for NFC intents
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("text/plain")
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("Check your MIME type.")
            }
        }
        intentFiltersArray = arrayOf(ndef, IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))

        takePictureLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val imageBitmap = result.data?.extras?.get("data") as Bitmap?
                    imageBitmap?.let {
                        Toast.makeText(this, "Фото успешно сделано!", Toast.LENGTH_SHORT).show()
                        NFCState.screenState = "description"
                        NFCState.imageUris.add(
                            Uri.parse(
                                MediaStore.Images.Media.insertImage(
                                    contentResolver,
                                    it,
                                    "Photo",
                                    null
                                )
                            )
                        )
                    }
                }
            }

        pickImageLauncher =
            registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
                NFCState.imageUris.addAll(uris)
                NFCState.screenState = "description"
            }

        setContent {
            NFC_CheckerTheme {
                NFCContent(
                    takePicture = { intent -> takePictureLauncher.launch(intent) },
                    pickImage = { pickImageLauncher.launch("image/*") })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
    }

    @Composable
    fun NFCContent(takePicture: (Intent) -> Unit, pickImage: () -> Unit) {
        val context = LocalContext.current
        val imageUris = NFCState.imageUris
        var deviceSerialNumber by remember {
            mutableStateOf(
                Build.SERIAL ?: "IZHIOV55CQOFUS75"
            )
        } //  Замените на реальный серийник

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (NFCState.screenState) {
                "select_issue" -> {
                    Text(
                        "Выберите проблему:",
                        fontSize = 24.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "Нет неисправности",
                            "Неисправность",
                            "Экстренная неисправность"
                        ).forEach { issue ->
                            Button(
                                onClick = {
                                    NFCState.selectedIssue = issue
                                    val issueType = NFCState.selectedIssue ?: "Нет неисправности"
                                    if (issue != "Нет неисправности") {
                                        val takePictureIntent =
                                            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                                        if (takePictureIntent.resolveActivity(context.packageManager) != null) {
                                            takePicture(takePictureIntent)
                                            NFCState.screenState = "description"
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Камера недоступна",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            sendDataToServer(
                                                deviceSerialNumber,
                                                "Первое помещение", //  Замените на реальное помещение
                                                issueType,
                                                "", //  Пустое описание для "Нет неисправности"
                                                NFCState.imageUris.firstOrNull()?.toString()
                                                    ?: "" //  URL первой фотографии или пустая строка
                                            )
                                        }
                                        Toast.makeText(
                                            context,
                                            "Данные отправлены на сервер",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        NFCState.screenState = "select_issue"
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(text = issue, color = Color.White, fontSize = 20.sp)
                            }
                        }
                    }
                }

                "description" -> {
                    Text(
                        "Опишите проблему:",
                        fontSize = 24.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    BasicTextField(
                        value = NFCState.description,
                        onValueChange = { NFCState.description = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(8.dp),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Добавьте фотографии:", fontSize = 20.sp)

                    // Список фотографий
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = NFCState.imageUris, key = { it.toString() }) { uri ->
                            Box {
                                Image(
                                    painter = rememberImagePainter(uri),
                                    contentDescription = "photo",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clickable {
                                        }
                                )
                                IconButton(
                                    onClick = {
                                        NFCState.imageUris.remove(uri)
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete"
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = {
                                    pickImage() // Открыть галерею
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Выбрать из галереи", fontSize = 16.sp)
                            }

                            Button(
                                onClick = {
                                    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                                    if (takePictureIntent.resolveActivity(context.packageManager) != null) {
                                        takePicture(takePictureIntent) // Открыть камеру
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Сделать фото", fontSize = 16.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Отправить данные
                    Button(
                        onClick = {
                            val issueType = NFCState.selectedIssue ?: "Неисправность"
                            CoroutineScope(Dispatchers.IO).launch {
                                sendDataToServer(
                                    deviceSerialNumber,
                                    "Первое помещение", // Замените на реальное помещение
                                    issueType,
                                    NFCState.description,
                                    NFCState.imageUris.firstOrNull()?.toString() ?: ""
                                )
                            }
                            Toast.makeText(
                                context,
                                "Данные отправлены на сервер",
                                Toast.LENGTH_SHORT
                            ).show()
                            NFCState.screenState = "select_issue"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Отправить", color = Color.White, fontSize = 20.sp)
                    }
                }
            }
        }
    }

    @Preview
    @Composable
    fun PreviewNFCContent() {
        NFC_CheckerTheme {
            NFCContent(takePicture = {}, pickImage = {})
        }
    }

    private fun handleNfcIntent(intent: Intent) {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            val message = rawMessages?.get(0) as? android.nfc.NdefMessage
            message?.let {
                val record = it.records[0]
                val payload = record.payload
                // Convert payload to string
                val text = String(payload.copyOfRange(3, payload.size))
                Toast.makeText(this, "NFC Tag: $text", Toast.LENGTH_SHORT).show()
                // Здесь можно добавить логику для обработки данных с NFC-метки
            }
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                val tagId = it.id.joinToString(":") { byte -> "%02X".format(byte) }
                Toast.makeText(this, "NFC Tag ID: $tagId", Toast.LENGTH_SHORT).show()
                // Здесь можно добавить логику для обработки ID NFC-метки
            }
        }
    }

    fun sendDataToServer(
        deviceSerialNumber: String,
        location: String,
        issueType: String,
        description: String,
        photoUrl: String
    ) {
        val client = OkHttpClient()
        val mediaType =
            "application/json".toMediaType() //  Исправлено использование MediaType.parse()
        val json = """
        {
            "deviceSerialNumber": "$deviceSerialNumber",
            "location": "$location",
            "issueType": "$issueType",
            "description": "$description",
            "photoUrl": "$photoUrl"
        }
    """.trimIndent()
        val body = RequestBody.create(mediaType, json)

        val request = Request.Builder()
            .url("http://localhost:3000/issues") // Замените на URL вашего API
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Обработка ошибки
                println("Ошибка отправки данных: ${e.message}")
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) { //  Используется импортированный Response
                // Обработка успешного ответа
                val responseData = response.body?.string()
                println("Данные успешно отправлены: $responseData")
            }
        })
    }
}
