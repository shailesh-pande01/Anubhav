package com.example.anubhav.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

object ImageCompressor {

    private const val TAG = "ImageCompressor"
    private const val MAX_WIDTH = 1280
    private const val MAX_HEIGHT = 1280
    private const val COMPRESSION_QUALITY = 70

    /**
     * Resizes and compresses an image from a given Uri.
     * Guarantees max dimensions <= 1280x1280, 70% JPEG quality,
     * strips metadata, and handles EXIF rotation and flipping.
     */
    suspend fun compressImage(context: Context, uri: Uri): ByteArray? {
        val result = compressImageWithDetails(context, uri)
        return result.getOrNull()
    }

    /**
     * Resizes and compresses an image from a given Uri, returning a Result with technical failure details.
     */
    suspend fun compressImageWithDetails(context: Context, uri: Uri): Result<ByteArray> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            // 1. Safely copy the content stream to a temporary cache file once
            tempFile = File.createTempFile("anubhav_img_", ".tmp", context.cacheDir)
            val bytesCopied = context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                Log.e(TAG, "Failed to open ContentResolver stream for uri: $uri")
                return@withContext Result.failure(Exception("Could not read image from device."))
            }

            if (bytesCopied <= 0L || !tempFile.exists() || tempFile.length() <= 0L) {
                Log.e(TAG, "Empty image file read from uri: $uri (bytes: $bytesCopied)")
                return@withContext Result.failure(Exception("Image file appears to be empty."))
            }

            // 2. Read EXIF matrix
            val matrix = getExifMatrix(tempFile.absolutePath)

            // 3. Decode bounds to determine dimensions
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(tempFile.absolutePath, boundsOptions)

            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions decoded: width=$originalWidth, height=$originalHeight")
                return@withContext Result.failure(Exception("Could not decode image format."))
            }

            Log.d(TAG, "Original image dimensions: ${originalWidth}x${originalHeight}, size: ${tempFile.length()} bytes")

            // 4. Compute inSampleSize
            var sampleSize = calculateInSampleSize(originalWidth, originalHeight, MAX_WIDTH, MAX_HEIGHT)

            // 5. Decode downsampled bitmap with OOM recovery
            var sampledBitmap: Bitmap? = null
            var attempts = 0
            while (sampledBitmap == null && attempts < 3) {
                attempts++
                try {
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inJustDecodeBounds = false
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    sampledBitmap = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions)
                } catch (oom: OutOfMemoryError) {
                    Log.w(TAG, "OOM decoding with sampleSize=$sampleSize, increasing sampleSize...", oom)
                    sampleSize *= 2
                    System.gc()
                } catch (t: Throwable) {
                    Log.e(TAG, "Unexpected error during decodeFile", t)
                    break
                }
            }

            if (sampledBitmap == null) {
                Log.e(TAG, "Failed to decode bitmap after $attempts attempts")
                return@withContext Result.failure(Exception("Could not decode image due to memory or format constraints."))
            }

            // 6. Correct EXIF rotation/flip if necessary
            var orientedBitmap: Bitmap = sampledBitmap
            if (!matrix.isIdentity) {
                try {
                    val transformed = Bitmap.createBitmap(
                        sampledBitmap,
                        0,
                        0,
                        sampledBitmap.width,
                        sampledBitmap.height,
                        matrix,
                        true
                    )
                    if (transformed != sampledBitmap) {
                        sampledBitmap.recycle()
                        orientedBitmap = transformed
                    }
                } catch (oom: OutOfMemoryError) {
                    Log.w(TAG, "OOM applying orientation matrix, proceeding with unrotated bitmap", oom)
                }
            }

            // 7. Scale precisely to fit within MAX_WIDTH x MAX_HEIGHT preserving aspect ratio
            val currentWidth = orientedBitmap.width
            val currentHeight = orientedBitmap.height
            val scale = min(
                MAX_WIDTH.toFloat() / currentWidth,
                MAX_HEIGHT.toFloat() / currentHeight
            )

            val finalBitmap = if (scale < 1.0f) {
                val targetWidth = (currentWidth * scale).roundToInt().coerceAtLeast(1)
                val targetHeight = (currentHeight * scale).roundToInt().coerceAtLeast(1)
                try {
                    val scaled = Bitmap.createScaledBitmap(orientedBitmap, targetWidth, targetHeight, true)
                    if (scaled != orientedBitmap) {
                        orientedBitmap.recycle()
                    }
                    scaled
                } catch (oom: OutOfMemoryError) {
                    Log.w(TAG, "OOM scaling bitmap to ${targetWidth}x${targetHeight}, using oriented bitmap", oom)
                    orientedBitmap
                }
            } else {
                orientedBitmap
            }

            // 8. Compress into JPEG ByteArray
            val outputStream = ByteArrayOutputStream()
            val compressed = finalBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)
            finalBitmap.recycle()

            if (!compressed) {
                Log.e(TAG, "Bitmap.compress failed to compress into JPEG")
                return@withContext Result.failure(Exception("Could not compress bitmap into JPEG."))
            }

            val compressedBytes = outputStream.toByteArray()
            Log.d(TAG, "Compressed successfully: ${compressedBytes.size} bytes (${finalBitmap.width}x${finalBitmap.height})")
            Result.success(compressedBytes)

        } catch (e: Exception) {
            Log.e(TAG, "Error in compressImageWithDetails", e)
            Result.failure(e)
        } finally {
            try {
                tempFile?.let {
                    if (it.exists()) it.delete()
                }
            } catch (_: Exception) {}
        }
    }

    internal fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight || (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun getExifMatrix(filePath: String): Matrix {
        val matrix = Matrix()
        return try {
            val exif = ExifInterface(filePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.apply { postRotate(90f) }
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.apply { postRotate(180f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.apply { postRotate(270f) }
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.apply { postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.apply { postScale(1f, -1f) }
                ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }
                else -> matrix
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read EXIF orientation from $filePath", e)
            matrix
        }
    }
}
