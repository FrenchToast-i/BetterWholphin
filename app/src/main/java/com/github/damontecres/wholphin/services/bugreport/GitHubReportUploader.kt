package com.github.damontecres.wholphin.services.bugreport

import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads a finished report as a release asset on GitHub.
 *
 * A release asset rather than an issue attachment: issues cannot carry a fifty-megabyte video,
 * and GitHub's release upload endpoint is the one binary API a fine-grained token can reach with
 * `contents: write` alone. All reports share a `bug-reports` release, so reading them is a single
 * `gh release download bug-reports` away, and the report keeps the name it was recorded with.
 *
 * The token never appears in a log line, an exception message or the UI; only the HTTP status is
 * reported back.
 */
@Singleton
class GitHubReportUploader
    @Inject
    constructor(
        @param:StandardOkHttpClient private val client: OkHttpClient,
    ) {
        suspend fun upload(
            repo: String,
            token: String,
            file: File,
            label: String,
        ): Result<String> =
            withContext(WholphinDispatchers.IO) {
                try {
                    require(repo.contains('/')) { "Repository must be owner/name" }
                    require(token.isNotBlank()) { "No GitHub token configured" }
                    val release = getOrCreateRelease(repo, token)
                    val uploadUrl = release.optString("upload_url").substringBefore("{")
                    val name = URLEncoder.encode(file.name, "UTF-8")
                    val labelEncoded = URLEncoder.encode(label, "UTF-8")
                    val request =
                        requestBuilder(
                            "$uploadUrl?name=$name&label=$labelEncoded",
                            token,
                        ).post(file.asRequestBody("application/zip".toMediaType()))
                            .build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            return@withContext Result.failure(
                                ReportUploadException("GitHub refused the upload (${response.code})"),
                            )
                        }
                        val json = JSONObject(body)
                        val url = json.optString("browser_download_url").ifBlank { json.optString("url") }
                        Timber.i("Bug report uploaded to %s", url)
                        Result.success(url)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Bug report upload failed")
                    Result.failure(ReportUploadException(ex.message ?: "Upload failed"))
                }
            }

        private fun getOrCreateRelease(
            repo: String,
            token: String,
        ): JSONObject {
            val existing =
                client.newCall(
                    requestBuilder("https://api.github.com/repos/$repo/releases/tags/bug-reports", token)
                        .get()
                        .build(),
                ).execute()
            existing.use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) return JSONObject(body)
                if (response.code != 404) {
                    throw ReportUploadException("Could not read the bug-reports release (${response.code})")
                }
            }
            val createBody =
                JSONObject()
                    .put("tag_name", "bug-reports")
                    .put("name", "Bug reports")
                    .put("body", "Screen recordings and logs uploaded from the television.")
                    .toString()
            val create =
                client.newCall(
                    requestBuilder("https://api.github.com/repos/$repo/releases", token)
                        .post(createBody.toRequestBody("application/json".toMediaType()))
                        .build(),
                ).execute()
            create.use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ReportUploadException("Could not create the bug-reports release (${response.code})")
                }
                return JSONObject(body)
            }
        }

        private fun requestBuilder(
            url: String,
            token: String,
        ) = Request
            .Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Wholphin-BugReport")
    }

class ReportUploadException(
    message: String,
) : Exception(message)
