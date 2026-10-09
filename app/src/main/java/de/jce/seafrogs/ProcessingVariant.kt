package de.jce.seafrogs

/**
 * NONE keeps normal photography unchanged. The three other modes belong exclusively to the
 * controlled JPEG processing comparison.
 */
enum class ProcessingVariant {
    NONE,
    DEFAULT,
    NR_HIGH_QUALITY,
    NR_EDGE_HIGH_QUALITY,
}
