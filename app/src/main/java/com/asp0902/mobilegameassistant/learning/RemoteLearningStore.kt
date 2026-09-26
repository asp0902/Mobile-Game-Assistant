package com.asp0902.mobilegameassistant.learning

import android.content.Context
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.asp0902.mobilegameassistant.analysis.InitialFormationRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun safeLearningPath(path: String): Boolean =
    path.length in 1..240 && !path.contains('\\') && !path.contains(':') &&
        path.none { it.code < 32 } && path.split('/').none { it.isBlank() || it == "." || it == ".." } &&
        (path.startsWith("learning/") || path.startsWith("hero_recognition/")) &&
        path.substringAfterLast('.').lowercase() in setOf("json", "jsonl", "png", "jpg", "jpeg", "webp", "md", "txt", "csv", "html")

internal fun copyLearningBytes(input: InputStream, output: OutputStream, limit: Long, tick: () -> Unit = {}): Long {
    var count = 0L
    val buffer = ByteArray(32768)
    while (true) {
        tick()
        val read = input.read(buffer)
        if (read < 0) return count
        count += read
        require(count <= limit) { "자료 크기 제한 초과" }
        output.write(buffer, 0, read)
    }
}

internal fun learningHash(file: File): String = file.inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(32768)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}

private const val REPO = "asp0902/Mobile-Game-Assistant"
private const val BASE_VERSION = 2026091801L
private const val MAX_BYTES = 128L * 1024 * 1024
private const val KNOWLEDGE = "learning/game_knowledge_20260917.json"
private const val RULES = "learning/initial_formation_rules.json"

private data class LearningFile(val path: String, val size: Long, val hash: String)
private data class LearningManifest(
    val version: Long, val minAppVersion: Long, val assetId: Long, val zipSize: Long,
    val zipHash: String, val files: List<LearningFile>,
) {
    companion object {
        fun parse(json: JSONObject): LearningManifest {
            require(json.getInt("formatVersion") == 1) { "새 자료 형식: APK 업데이트 필요" }
            val version = json.getLong("version")
            val minApp = json.getLong("minAppVersion")
            val assetId = json.getLong("assetId")
            val zipSize = json.getLong("zipSize")
            val hash = json.getString("zipSha256")
            val hashes = Regex("[0-9a-f]{64}")
            require(version > 0 && minApp > 0 && assetId > 0 && zipSize in 1..MAX_BYTES && hashes.matches(hash))
            val array = json.getJSONArray("files")
            require(array.length() in 1..5000)
            val files = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                LearningFile(item.getString("path"), item.getLong("size"), item.getString("sha256")).also {
                    require(safeLearningPath(it.path) && it.size in 0..(16L * 1024 * 1024) && hashes.matches(it.hash))
                }
            }
            require(files.map { it.path.lowercase() }.toSet().size == files.size)
            require(files.sumOf { it.size } <= MAX_BYTES)
            require(files.any { it.path == KNOWLEDGE } && files.any { it.path == RULES })
            return LearningManifest(version, minApp, assetId, zipSize, hash, files)
        }
    }
}

// One immutable source per process. Publication never changes an in-flight analysis.
object LearningFiles {
    private data class Source(val directory: File?, val version: Long, val notice: String = "")
    @Volatile private var source: Source? = null
    private val updateMutex = Mutex()

    private fun home(context: Context) = File(context.filesDir, "remote-learning").apply { mkdirs() }
    private fun pointer(context: Context) = AtomicFile(File(home(context), "active.json"))
    private fun readPointer(context: Context): JSONObject = runCatching {
        pointer(context).openRead().bufferedReader().use { JSONObject(it.readText()) }
    }.getOrDefault(JSONObject())

    private fun directory(context: Context, name: String): File {
        require(Regex("v[0-9]+-[a-f0-9-]{36}").matches(name))
        return File(home(context), name)
    }

    @Synchronized private fun current(context: Context): Source {
        source?.let { return it }
        val pointer = readPointer(context)
        var recovered = false
        for (key in listOf("current", "previous")) {
            val name = pointer.optString(key)
            if (name.isBlank()) continue
            val loaded = runCatching {
                val folder = directory(context, name)
                val manifest = LearningManifest.parse(JSONObject(File(folder, "manifest.json").readText()))
                require(manifest.minAppVersion <= appVersion(context))
                validate(context, folder, manifest)
                Source(folder, manifest.version, if (recovered) "최신 자료 손상: 이전 자료로 복구" else "")
            }.getOrNull()
            if (loaded != null) return loaded.also { source = it }
            recovered = true
        }
        return Source(null, BASE_VERSION, if (recovered) "원격 자료 읽기 실패: APK 내장 자료 사용" else "").also { source = it }
    }

    fun status(context: Context): String {
        val active = current(context)
        val saved = readPointer(context).optLong("version", BASE_VERSION)
        return "사용 중: v${active.version}" +
            (if (saved > active.version) " / 저장: v$saved (앱 완전 종료 후 재실행)" else "") +
            (if (active.notice.isBlank()) "" else "\n${active.notice}")
    }

    fun open(context: Context, path: String): InputStream {
        require(safeLearningPath(path))
        val remote = current(context).directory?.let { File(it, path) }
        return if (remote?.isFile == true) remote.inputStream() else context.assets.open(path)
    }

    fun list(context: Context, path: String): List<String> {
        require(safeLearningPath("$path/check.png"))
        return (context.assets.list(path).orEmpty().toList() +
            current(context).directory?.let { File(it, path).list()?.toList() }.orEmpty()).distinct()
    }

    private fun appVersion(context: Context): Long {
        @Suppress("DEPRECATION")
        return context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
    }

    private fun validate(context: Context, folder: File, manifest: LearningManifest) {
        for (item in manifest.files) {
            val file = File(folder, item.path)
            require(file.isFile && file.length() == item.size && learningHash(file) == item.hash) { "파일 무결성 검증 실패" }
            if (file.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp")) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, options)
                require(options.outWidth in 1..8192 && options.outHeight in 1..8192 &&
                    options.outWidth.toLong() * options.outHeight <= 20_000_000) { "지원하지 않는 초상/이미지" }
            }
        }
        val root = JSONObject(File(folder, KNOWLEDGE).readText())
        require(root.getInt("schemaVersion") == 1) { "학습 JSON 형식 변경: APK 업데이트 필요" }
        require(root.getString("snapshotDate").isNotBlank())
        root.getJSONObject("boss").getString("overlay")
        for (key in listOf("entries", "conversationIndex", "heroes", "nameCorrections")) {
            val items = root.getJSONArray(key)
            val ids = mutableSetOf<String>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                if (key == "nameCorrections") {
                    require(item.getString("wrong").isNotBlank() && item.getString("correct").isNotBlank())
                } else {
                    require(item.getString("id").isNotBlank() && ids.add(item.getString("id")))
                    require(item.getString(if (key == "heroes") "name" else "title").isNotBlank())
                    if (key == "heroes") item.getString("faction")
                }
                for (field in listOf("asset", "portrait", "portraitAsset")) {
                    val path = item.optString(field).takeIf { it.isNotBlank() && it != "null" } ?: continue
                    require(safeLearningPath(path))
                    // Missing historic portraits remain CHECK, not fabricated replacements.
                    if (field == "asset") require(File(folder, path).isFile || runCatching {
                        context.assets.open(path).use { }
                    }.isSuccess) { "본문 참조 파일 누락" }
                }
            }
        }
        InitialFormationRules.parse(JSONObject(File(folder, RULES).readText()))
    }

    suspend fun update(context: Context): String = updateMutex.withLock {
        withContext(Dispatchers.IO) {
            current(context) // Pin the old source before saving the next process's source.
            val token = LearningTokenVault(context).read()
            require(!token.isNullOrBlank()) { "읽기 전용 GitHub 토큰을 먼저 저장하세요." }
            val coroutine = currentCoroutineContext()
            val deadline = System.nanoTime() + 300_000_000_000L
            val tick = { coroutine.ensureActive(); check(System.nanoTime() < deadline) { "다운로드 제한 시간 초과" } }
            val metadata = JSONObject(requestText("https://api.github.com/repos/$REPO", token, tick))
            require(metadata.getBoolean("private") && metadata.getString("full_name") == REPO) { "비공개 저장소가 아니므로 업데이트 중단" }
            val channel = JSONObject(requestText(
                "https://api.github.com/repos/$REPO/contents/remote-learning/channel.json?ref=main", token, tick,
                "application/vnd.github.raw+json",
            ))
            val manifest = LearningManifest.parse(channel)
            require(manifest.minAppVersion <= appVersion(context)) { "이 자료에는 새 APK가 필요합니다." }
            val old = readPointer(context)
            if (manifest.version <= maxOf(BASE_VERSION, old.optLong("version"))) return@withContext "새 자료가 없습니다. ${status(context)}"
            val home = home(context)
            require(home.usableSpace > manifest.files.sumOf { it.size } + manifest.zipSize + 16L * 1024 * 1024) { "저장 공간 부족" }
            val stage = File(home, "v${manifest.version}-${UUID.randomUUID()}").apply { check(mkdir()) }
            val zip = File(home, "download-${UUID.randomUUID()}.zip")
            var committed = false
            try {
                zip.outputStream().use { output ->
                    request("https://api.github.com/repos/$REPO/releases/assets/${manifest.assetId}", token,
                        "application/octet-stream") { input ->
                        require(copyLearningBytes(input, output, manifest.zipSize, tick) == manifest.zipSize)
                    }
                }
                require(learningHash(zip) == manifest.zipHash) { "다운로드 SHA-256 불일치" }
                val expected = manifest.files.associateBy { it.path }
                val seen = mutableSetOf<String>()
                ZipInputStream(zip.inputStream()).use { input ->
                    while (true) {
                        tick()
                        val entry = input.nextEntry ?: break
                        val item = expected[entry.name] ?: error("허용하지 않은 ZIP 항목")
                        require(!entry.isDirectory && safeLearningPath(entry.name) && seen.add(entry.name))
                        val file = File(stage, item.path)
                        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
                        file.outputStream().use { output ->
                            require(copyLearningBytes(input, output, item.size, tick) == item.size)
                            output.fd.sync()
                        }
                        input.closeEntry()
                    }
                }
                require(seen == expected.keys) { "다운로드 파일 누락" }
                validate(context, stage, manifest)
                File(stage, "manifest.json").outputStream().use { it.write(channel.toString().toByteArray()); it.fd.sync() }
                tick()
                val next = JSONObject().put("current", stage.name).put("version", manifest.version)
                    .put("previous", current(context).directory?.name ?: "")
                val atomic = pointer(context)
                val output = atomic.startWrite()
                try {
                    output.write(next.toString().toByteArray())
                    atomic.finishWrite(output)
                    committed = true
                } catch (error: Exception) {
                    atomic.failWrite(output)
                    throw error
                }
                "v${manifest.version} 검증·저장 완료. 트래킹을 중지하고 앱을 완전히 종료한 뒤 다시 실행하세요."
            } finally {
                zip.delete()
                if (!committed) stage.deleteRecursively()
                // ponytail: retain prior immutable bundles for recovery; no automatic pruning yet.
            }
        }
    }

    private fun requestText(url: String, token: String, tick: () -> Unit, accept: String = "application/vnd.github+json"): String =
        java.io.ByteArrayOutputStream().use { output ->
            request(url, token, accept) { copyLearningBytes(it, output, 2L * 1024 * 1024, tick) }
            output.toString("UTF-8")
        }

    private fun <T> request(url: String, token: String, accept: String, consume: (InputStream) -> T): T {
        var target = URL(url)
        repeat(4) {
            require(target.protocol == "https" && target.userInfo == null && target.port in listOf(-1, 443) &&
                target.host in setOf("api.github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
            val connection = target.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Accept", accept)
                connection.setRequestProperty("User-Agent", "AFK-Tracker-Learning")
                if (target.host == "api.github.com") {
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
                when (val status = connection.responseCode) {
                    200 -> return connection.inputStream.use(consume)
                    301, 302, 303, 307, 308 -> target = URL(target, connection.getHeaderField("Location") ?: error("잘못된 다운로드 응답"))
                    401, 403, 404 -> error("GitHub 접근 실패($status): 토큰 권한·만료·자료 게시 상태를 확인하세요.")
                    else -> error("GitHub 다운로드 실패($status)")
                }
            } finally { connection.disconnect() }
        }
        error("다운로드 리디렉션 제한 초과")
    }
}

class LearningTokenVault(context: Context) {
    private val prefs = context.getSharedPreferences("learning-auth", Context.MODE_PRIVATE)
    private val alias = "afk-learning-github"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun hasToken() = prefs.contains("ciphertext")
    fun save(token: String) {
        val value = token.trim()
        require(value.length in 20..512 && value.all { it.code in 33..126 }) { "토큰 형식을 확인하세요." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.toByteArray())
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
    fun read(): String? {
        val data = prefs.getString("ciphertext", null) ?: return null
        val iv = Base64.decode(prefs.getString("iv", null) ?: error("토큰을 다시 저장하세요."), Base64.NO_WRAP)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        }
    }
    fun forget() { check(prefs.edit().clear().commit()) }
}
