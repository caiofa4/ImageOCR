package com.accessibility.myocr

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.accessibility.myocr.ui.theme.MyOCRTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import androidx.core.net.toUri
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

class MainActivity : ComponentActivity() {
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val searchResponse =  mutableStateOf("")

    private var selectedImageUri = mutableStateOf<Uri?>(null)
    private var inputImage: InputImage? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyOCRTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (!Environment.isExternalStorageManager()) {
            val uri = "package:com.accessibility.myocr".toUri()
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
        }
    }

    // Register gallery picker
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri.value = uri
            searchResponse.value = ""
            inputImage = InputImage.fromFilePath(this, uri)
        } ?: Log.d("OCR", "No image selected")
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    @Composable
    fun MainScreen(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val textToFind = remember { mutableStateOf("淘宝") }

        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    openGallery()
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Select Image")
            }

            selectedImageUri.value?.let { uri ->
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = "Selected image",
                    modifier = Modifier
                        .size(400.dp)
                        .padding(8.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = textToFind.value,
                onValueChange = {
                    textToFind.value = it
                    searchResponse.value
                },
                label = { Text("Text to be found on image") },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(8.dp)
            )

            Button(
                onClick = {
                    if (textToFind.value.isEmpty()) {
                        Toast.makeText(context, "Texto vazio", Toast.LENGTH_SHORT).show()
                    } else {
                        runOCR(context, textToFind.value)
                    }
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Search")
            }

            if (searchResponse.value.isNotEmpty()) {
                Text(
                    text = searchResponse.value
                )
            }
        }
    }

    private fun runOCR(context: Context, textToFind: String) {
        val image = inputImage ?: getInputImage(context)
        if (image == null) {
            searchResponse.value = "The image doesn't exist."
            return
        }
        detectTextFromImage(
            image = image,
            targetText = textToFind
        ) { found ->
            if (found) {
                searchResponse.value = "The word was found in the image!"
            } else {
                searchResponse.value = "The word was NOT found."
            }
        }
    }

    private fun getInputImage(context: Context, filePath: String = ""): InputImage? {
        val assetFilename = "screenshot_home.jpg"
        if (filePath.isEmpty()) {
            val inputStream = context.assets.open(assetFilename)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            // Create InputImage
            return InputImage.fromBitmap(bitmap, 0)
        }

        val file = File(filePath)
        if (!file.exists()) {
            return null
        }

        // Convert the file path into a Uri
        val uri = Uri.fromFile(file)

        // Create the InputImage
        return InputImage.fromFilePath(context, uri)
    }

    private fun detectTextFromImage(
        image: InputImage,
        targetText: String,
        onResult: (Boolean) -> Unit
    ) {
        val recognizers = listOf(latinRecognizer, chineseRecognizer)

        fun processNext(index: Int) {
            if (index >= recognizers.size) {
                onResult(false)
                return
            }

            val recognizer = recognizers[index]
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val detectedText = result.text
                    if (detectedText.contains(targetText, ignoreCase = true)) {
                        onResult(true)
                    } else {
                        processNext(index + 1)
                    }
                }
                .addOnFailureListener {
                    processNext(index + 1)
                }
        }

        try {
            processNext(0)
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(false)
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        MyOCRTheme {
            MainScreen()
        }
    }
}