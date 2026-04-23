package com.triloo.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class ReceiptOcrResult(
    val imageUri: String,
    val parsed: ParsedReceiptData
)

@Singleton
class ReceiptOcrService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun analyzeReceipt(
        imageUri: String,
        fallbackCurrency: String? = null
    ): ReceiptOcrResult {
        val image = InputImage.fromFilePath(context, Uri.parse(imageUri))
        val text = recognizer.process(image).await().text
        val parsed = ReceiptTextParser.parse(text, fallbackCurrency)
        return ReceiptOcrResult(
            imageUri = imageUri,
            parsed = parsed
        )
    }
}
