package com.sightline.facestyle

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

object ImageUtils {

    /** Decodes an image from a content Uri, downsampled so its longest side is <= maxSide, with EXIF rotation applied. */
    fun loadBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Could not read that image." }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Could not decode that image.")

        val orientation = runCatching {
            resolver.openInputStream(uri).use { ExifInterface(it!!) }
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale < 1f) matrix.postScale(scale, scale)
        if (!matrix.isIdentity) {
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }
        return bmp
    }

    fun toJpeg(bitmap: Bitmap, quality: Int = 92): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /** Saves to Pictures/FaceStyle in the gallery. Returns the new item's Uri. */
    fun saveToGallery(context: Context, bitmap: Bitmap): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "facestyle_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FaceStyle")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create gallery entry.")
        resolver.openOutputStream(uri).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out!!)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    fun shareIntent(context: Context, bitmap: Bitmap): Intent {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "facestyle_share.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share image")
    }
}
