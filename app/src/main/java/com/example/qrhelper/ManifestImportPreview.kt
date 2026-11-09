package com.example.qrhelper

/**
 * One physical source document and the rows parsed from that document.
 *
 * Keeping this boundary is important: two suppliers can deliver to the same
 * store, but their manifests must not become indistinguishable during import.
 */
internal data class ImportedManifestDocument(
    val sourceName: String,
    val rows: List<RouteItem>
)

internal data class ManifestImportPreview(
    val documents: List<ImportedManifestDocument>,
    val totalRows: Int,
    val totalDocuments: Int
)

/**
 * Normalizes a multi-file import without merging rows across source documents.
 * Empty documents are retained so the UI can report which selected file failed
 * to produce rows instead of silently hiding it.
 */
internal fun prepareManifestImportPreview(
    documents: List<ImportedManifestDocument>
): ManifestImportPreview {
    val normalized = documents.mapIndexed { index, document ->
        ImportedManifestDocument(
            sourceName = document.sourceName.trim().ifBlank { "Документ ${index + 1}" },
            rows = document.rows.toList()
        )
    }
    return ManifestImportPreview(
        documents = normalized,
        totalRows = normalized.sumOf { it.rows.size },
        totalDocuments = normalized.size
    )
}
