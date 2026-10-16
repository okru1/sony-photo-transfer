package com.sonyphototransfer.camera

/** Identity of the camera reported by its own device description. */
data class CameraInfo(
    val friendlyName: String,
    val modelName: String,
    val manufacturer: String,
    val descriptionUrl: String,
) {
    /** Best human-readable model label, e.g. "ILCE-7M3". */
    val displayModel: String
        get() = friendlyName.ifBlank { modelName }.ifBlank { "Cámara Sony" }
}

enum class PhotoFormat(val label: String) {
    JPEG("JPEG"),
    RAW("RAW"),
    HEIF("HEIF"),
    OTHER("Otro"),
    ;

    companion object {
        fun fromFileName(name: String): PhotoFormat = when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> JPEG
            "arw", "srf", "sr2" -> RAW
            "hif", "heif", "heic" -> HEIF
            else -> OTHER
        }
    }
}

/** What a given URL served by the camera actually is. */
enum class ResourceKind {
    /** The unmodified file stored on the memory card. */
    ORIGINAL,

    /** Camera-generated JPEG previews of decreasing size. */
    PREVIEW_LARGE,
    PREVIEW_SMALL,
    THUMBNAIL,
    UNKNOWN,
}

data class PhotoResource(
    val url: String,
    val kind: ResourceKind,
    val mimeType: String,
    /** Byte size announced by the camera, or null when it does not say. */
    val sizeBytes: Long?,
    /** "WIDTHxHEIGHT" as announced by the camera, if any. */
    val resolution: String?,
)

data class CameraPhoto(
    /** Server object id; stable for the current camera session only. */
    val id: String,
    val fileName: String,
    val format: PhotoFormat,
    /** Capture date exactly as reported by the camera (ISO-8601 local time), if any. */
    val date: String?,
    val resources: List<PhotoResource>,
) {
    val original: PhotoResource? get() = resources.firstOrNull { it.kind == ResourceKind.ORIGINAL }
    val thumbnail: PhotoResource?
        get() = firstOf(ResourceKind.THUMBNAIL, ResourceKind.PREVIEW_SMALL, ResourceKind.PREVIEW_LARGE)
    val largePreview: PhotoResource?
        get() = firstOf(ResourceKind.PREVIEW_LARGE, ResourceKind.PREVIEW_SMALL, ResourceKind.THUMBNAIL)

    /** True when the camera offers the untouched file (it never does for RAW over this protocol). */
    val isOriginalAvailable: Boolean get() = original != null

    /** Key used to recognise the same shot across sessions (object ids are not stable). */
    val stableKey: String get() = "$fileName|${date.orEmpty()}"

    private fun firstOf(vararg kinds: ResourceKind): PhotoResource? =
        kinds.firstNotNullOfOrNull { kind -> resources.firstOrNull { it.kind == kind } }
}

class CameraException(message: String, cause: Throwable? = null) : Exception(message, cause)
