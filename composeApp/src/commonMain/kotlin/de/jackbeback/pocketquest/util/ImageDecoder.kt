package de.jackbeback.pocketquest.util

import androidx.compose.ui.graphics.ImageBitmap

/** Decodes a PNG/JPEG [ByteArray] into an [ImageBitmap] suitable for Canvas drawing. */
expect fun ByteArray.toImageBitmap(): ImageBitmap
