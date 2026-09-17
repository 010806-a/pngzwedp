package com.example.myno.pngzwedp.storage
import androidx.documentfile.provider.DocumentFile
import android.content.Context
import android.content.Intent
import android.net.Uri

object OutputDirectoryManager {

    private const val PREFS_NAME = "pngzwedp_settings"
    private const val KEY_OUTPUT_URI = "output_uri"
    /**
 * 获取当前实际输出目录的可读路径。
 *
 * 返回值始终根据当前保存的 SAF URI 动态生成，
 * 不使用固定的 pngzwedp 或写死的目录。
 */
fun getOutputDirectoryPath(
    context: Context
): String? {

    val uri = getOutputUri(context)
        ?: return null

    return try {

        /*
         * 优先解析 Android 外部存储的 SAF URI。
         *
         * 例如：
         * content://com.android.externalstorage.documents/tree/primary%3AAndroidIDEProjects
         *
         * 解析后：
         * /storage/emulated/0/AndroidIDEProjects/
         */
        if (
            uri.authority ==
                "com.android.externalstorage.documents"
        ) {

            val documentId =
                uri.lastPathSegment
                    ?.let { Uri.decode(it) }

            if (!documentId.isNullOrBlank()) {

                val separatorIndex =
                    documentId.indexOf(':')

                if (separatorIndex > 0) {

                    val storageId =
                        documentId.substring(
                            0,
                            separatorIndex
                        )

                    val relativePath =
                        documentId.substring(
                            separatorIndex + 1
                        )

                    if (
                        storageId.equals(
                            "primary",
                            ignoreCase = true
                        )
                    ) {

                        return if (
                            relativePath.isBlank()
                        ) {
                            "/storage/emulated/0/"
                        } else {
                            "/storage/emulated/0/$relativePath/"
                        }
                    }
                }
            }
        }

        /*
         * 如果不是标准 primary 外部存储，
         * 至少显示 DocumentFile 获取到的真实目录名称。
         */
        DocumentFile
            .fromTreeUri(
                context,
                uri
            )
            ?.name

    } catch (_: Exception) {
        null
    }
}
    /**
     * 保存用户选择的输出目录。
     */
    fun saveOutputUri(
        context: Context,
        uri: Uri
    ) {
        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_OUTPUT_URI,
                uri.toString()
            )
            .apply()
    }

    /**
     * 获取之前保存的输出目录。
     */
    fun getOutputUri(
        context: Context
    ): Uri? {

        val value =
            context
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(
                    KEY_OUTPUT_URI,
                    null
                )

        return value?.let(Uri::parse)
    }

    /**
     * 清除保存的输出目录。
     */
    fun clearOutputUri(
        context: Context
    ) {
        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(KEY_OUTPUT_URI)
            .apply()
    }

    /**
     * 判断当前保存的目录 URI 是否仍然存在。
     */
    fun hasOutputUri(
        context: Context
    ): Boolean {
        return getOutputUri(context) != null
    }

    /**
     * 保存 SAF 持久化读写权限。
     *
     * 用户第一次选择输出目录后调用。
     */
    fun takePersistablePermission(
        context: Context,
        intent: Intent,
        uri: Uri
    ) {
        val flags =
            intent.flags and
                (
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

        if (flags == 0) {
            return
        }

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                flags
            )
        } catch (_: SecurityException) {
            // 某些文件管理器不提供持久化权限。
            // 不影响 URI 本身保存。
        }
    }

    /**
     * 检查之前保存的 URI 是否仍然拥有访问权限。
     */
    fun hasPersistedPermission(
        context: Context
    ): Boolean {

        val uri = getOutputUri(context)
            ?: return false

        return try {
            context.contentResolver
                .persistedUriPermissions
                .any { permission ->
                    permission.uri == uri &&
                        (
                            permission.isReadPermission ||
                                permission.isWritePermission
                            )
                }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 删除保存的目录以及相关持久化权限。
     */
    fun clearOutputUriAndPermission(
        context: Context
    ) {

        val uri = getOutputUri(context)

        if (uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
                // 权限不存在时无需处理。
            }
        }

        clearOutputUri(context)
    }
}