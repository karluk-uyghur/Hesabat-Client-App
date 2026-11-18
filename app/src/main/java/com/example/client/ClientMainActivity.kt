package com.example.client

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// باشقۇرغۇچى ئەپى بىلەن 100% ئورتاق شىفىرلىق ئاچقۇچ ۋە باشقۇرغۇچى ئېلخەت ئادرېسى
const val ADMIN_MASTER_EMAIL = "uyghur162142@gmail.com"
const val SHARED_CRYPTO_SALT = "HESABAT_SYNC_2026_SECRET"
const val CURRENT_CLIENT_VERSION = "1.0.0"

val RoyalGold = Color(0xFFD4AF37)
val RoyalCrimson = Color(0xFF991B1B)
val ClientIndigo = Color(0xFF6366F1)
val ClientTurquoise = Color(0xFF06B6D4)
val ClientEmerald = Color(0xFF10B981)
val ClientRose = Color(0xFFF43F5E)
val ClientAmber = Color(0xFFF59E0B)
val ClientDarkBg = Color(0xFF070C18)
val ClientCardBg = Color(0xE6111A2E)

enum class ClientLanguage(val code: String, val title: String) {
    UG("ug", "ئۇيغۇرچە"),
    AR("ar", "العربية")
}

fun tr(lang: ClientLanguage, ug: String, ar: String): String {
    return if (lang == ClientLanguage.UG) ug else ar
}

enum class RegistrationAuthMethod(val ugLabel: String, val arLabel: String, val icon: ImageVector) {
    BY_GMAIL("Gmail ئارقىلىق تىزىملىتىش / كىرىش", "التسجيل / الدخول عبر Gmail", Icons.Filled.Email),
    BY_NAME("ئىسىم ئارقىلىق تىزىملىتىش / كىرىش", "التسجيل / الدخول عبر الاسم", Icons.Filled.Person),
    BY_ACCOUNT_ID("ھېسابات نامى ئارقىلىق كىرىش", "الدخول عبر معرف الحساب", Icons.Filled.Badge)
}

enum class ClientBottomTab(val ugTitle: String, val arTitle: String, val icon: ImageVector) {
    HOME("باش بەت", "الرئيسية", Icons.Filled.Home),
    SETTINGS("تەڭشەكلەر", "الإعدادات", Icons.Filled.Settings),
    ACCOUNTS("ھېساباتلار", "الحسابات", Icons.Filled.Group),
    REMINDERS("ئەسلەتمىلەر", "التذكيرات", Icons.Filled.Notifications)
}

class ClientMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.decorView.filterTouchesWhenObscured = true

        enableEdgeToEdge()
        setContent {
            ClientRootApp()
        }
    }
}

data class RegisteredAuthUser(
    val identifier: String, // Gmail ياكى ئىسىم ياكى ھېسابات نامى
    val authMethod: String, // BY_GMAIL, BY_NAME, BY_ACCOUNT_ID
    val fullName: String,
    val gmail: String,
    val passwordHash: String,
    val address: String,
    val isVerified: Boolean,
    val isVip: Boolean,
    val vipExpireMillis: Long,
    val licenseCode: String
) {
    fun canDirectLoginWithoutCode(requireCodePolicy: Boolean): Boolean {
        val vipValid = isVip && (vipExpireMillis == 0L || vipExpireMillis > System.currentTimeMillis())
        return !requireCodePolicy || isVerified || vipValid
    }
}

data class PersonAccountItem(
    val id: String,
    val ownerIdentifier: String,
    val fullName: String,
    val accountName: String,
    val password: String,
    val streetName: String,
    val houseNumber: String,
    val flowAmount: String, // ئېقىم مىقدارى (مەسىلەن: 100 GB)
    val openDate: String,
    val notes: String,
    val paidMonths: List<Boolean>
) {
    fun paidCount(): Int = paidMonths.count { it }
}

data class AdminReminderNote(
    val id: String,
    val title: String,
    val content: String,
    val targetUsername: String,
    val timestamp: String
)

data class ClientFeedbackNote(
    val id: String,
    val clientUsername: String,
    val clientName: String,
    val message: String,
    val adminReply: String,
    val timestamp: String
)

data class RemoteAdminUpdateInfo(
    val versionName: String,
    val apkDownloadUrl: String,
    val releaseNotes: String,
    val forceUpdate: Boolean,
    val targetAccount: String
)

data class RemoteAdminConfig(
    val allowNewRegistration: Boolean,
    val requireVerificationCode: Boolean,
    val remoteMaintenanceLock: Boolean,
    val announcementBanner: String
)

data class FastSpeedTestResult(
    val downloadMbps: Float,
    val downloadMBps: Float,
    val pingMs: Int,
    val testedAt: String
)

class ClientVaultManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("hesabat_client_v4_vault", Context.MODE_PRIVATE)
    private val dbFile: File
        get() = File(context.filesDir, "hesabat_client_v4_records.json")
    private val bgFile: File
        get() = File(context.filesDir, "client_custom_bg.jpg")
    private val avatarFile: File
        get() = File(context.filesDir, "client_custom_avatar.jpg")

    fun getLanguage(): ClientLanguage {
        return if (prefs.getString("app_lang", "ug") == "ar") ClientLanguage.AR else ClientLanguage.UG
    }

    fun setLanguage(lang: ClientLanguage) {
        prefs.edit().putString("app_lang", lang.code).commit()
    }

    fun isDarkMode(): Boolean = prefs.getBoolean("dark_mode", true)

    fun setDarkMode(dark: Boolean) {
        prefs.edit().putBoolean("dark_mode", dark).commit()
    }

    fun getCloudSyncUrl(): String = prefs.getString("cloud_sync_url", "") ?: ""

    fun setCloudSyncUrl(url: String) {
        prefs.edit().putString("cloud_sync_url", url.trim()).commit()
    }

    /**
     * باشقۇرغۇچى ئەپىدە چىقىرىلغان كودنى دەلىللەش (Gmail، ئىسىم ياكى ھېسابات نامىنىڭ قايسىسى بىلەن چىقىرىلغان بولسا شۇنى تونۇيدۇ)
     */
    private fun generateExpectedCode(key: String, isVip: Boolean): String {
        val clean = key.trim().lowercase()
        if (clean.isEmpty()) return ""
        val raw = sha256("$SHARED_CRYPTO_SALT::$clean").uppercase()
        val prefix = if (isVip) "VIP" else "HSB"
        return "$prefix-${raw.substring(0, 4)}-${raw.substring(4, 8)}"
    }

    fun verifyCodeFromAdmin(
        identifier: String,
        fullName: String,
        gmail: String,
        inputCode: String
    ): Pair<Boolean, Boolean> {
        val cleanInput = inputCode.trim().uppercase().replace(" ", "")
        if (cleanInput.isEmpty()) return false to false
        val compactInput = cleanInput.replace("-", "")

        val candidateKeys = listOf(
            identifier.trim(),
            fullName.trim(),
            gmail.trim(),
            gmail.substringBefore("@").trim()
        ).filter { it.isNotEmpty() }.distinct()

        for (key in candidateKeys) {
            val std = generateExpectedCode(key, false)
            val vip = generateExpectedCode(key, true)
            if (cleanInput == std || compactInput == std.replace("-", "")) {
                return true to false
            }
            if (cleanInput == vip || compactInput == vip.replace("-", "")) {
                return true to true
            }
        }
        return false to false
    }

    fun saveCustomBackgroundFromUri(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                bgFile.outputStream().use { out -> input.copyTo(out) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearCustomBackground() {
        runCatching { if (bgFile.exists()) bgFile.delete() }
    }

    fun loadCustomBackgroundBitmap(): ImageBitmap? {
        return try {
            if (bgFile.exists()) BitmapFactory.decodeFile(bgFile.absolutePath)?.asImageBitmap() else null
        } catch (_: Exception) {
            null
        }
    }

    fun saveCustomAvatarFromUri(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                avatarFile.outputStream().use { out -> input.copyTo(out) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadCustomAvatarBitmap(): ImageBitmap? {
        return try {
            if (avatarFile.exists()) BitmapFactory.decodeFile(avatarFile.absolutePath)?.asImageBitmap() else null
        } catch (_: Exception) {
            null
        }
    }

    fun loadRegisteredUsers(): List<RegisteredAuthUser> {
        val raw = prefs.getString("auth_users_v4", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                RegisteredAuthUser(
                    identifier = o.optString("identifier"),
                    authMethod = o.optString("authMethod", "BY_GMAIL"),
                    fullName = o.optString("fullName"),
                    gmail = o.optString("gmail"),
                    passwordHash = o.optString("passwordHash"),
                    address = o.optString("address"),
                    isVerified = o.optBoolean("isVerified", false),
                    isVip = o.optBoolean("isVip", false),
                    vipExpireMillis = o.optLong("vipExpireMillis", 0L),
                    licenseCode = o.optString("licenseCode")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRegisteredUsers(users: List<RegisteredAuthUser>) {
        val arr = JSONArray()
        users.forEach { u ->
            arr.put(
                JSONObject().apply {
                    put("identifier", u.identifier)
                    put("authMethod", u.authMethod)
                    put("fullName", u.fullName)
                    put("gmail", u.gmail)
                    put("passwordHash", u.passwordHash)
                    put("address", u.address)
                    put("isVerified", u.isVerified)
                    put("isVip", u.isVip)
                    put("vipExpireMillis", u.vipExpireMillis)
                    put("licenseCode", u.licenseCode)
                }
            )
        }
        prefs.edit().putString("auth_users_v4", arr.toString()).commit()
    }

    fun getActiveUser(): RegisteredAuthUser? {
        val activeId = prefs.getString("active_identifier", null) ?: return null
        val cfg = loadRemoteAdminConfig()
        val user = loadRegisteredUsers().firstOrNull {
            it.identifier.equals(activeId, ignoreCase = true) ||
                it.gmail.equals(activeId, ignoreCase = true)
        } ?: return null
        return if (user.canDirectLoginWithoutCode(cfg.requireVerificationCode)) user else null
    }

    fun setActiveIdentifier(id: String?) {
        if (id.isNullOrBlank()) {
            prefs.edit().remove("active_identifier").commit()
        } else {
            prefs.edit().putString("active_identifier", id.trim()).commit()
        }
    }

    fun createPendingUserRequest(
        method: RegistrationAuthMethod,
        identifier: String,
        fullName: String,
        gmail: String,
        password: String,
        address: String
    ): RegisteredAuthUser {
        val cleanId = identifier.trim()
        val users = loadRegisteredUsers().toMutableList()
        val idx = users.indexOfFirst { it.identifier.equals(cleanId, ignoreCase = true) }
        val finalGmail = if (gmail.contains("@")) gmail.trim() else if (cleanId.contains("@")) cleanId else "$cleanId@gmail.com"
        val newObj = RegisteredAuthUser(
            identifier = cleanId,
            authMethod = method.name,
            fullName = fullName.trim().ifBlank { cleanId },
            gmail = finalGmail,
            passwordHash = sha256(password.trim()),
            address = address.trim(),
            isVerified = false,
            isVip = false,
            vipExpireMillis = 0L,
            licenseCode = ""
        )
        if (idx >= 0) {
            users[idx] = newObj
        } else {
            users.add(0, newObj)
        }
        saveRegisteredUsers(users)
        return newObj
    }

    fun verifyUserWithAdminCode(
        identifier: String,
        fullName: String,
        gmail: String,
        enteredCode: String,
        lang: ClientLanguage
    ): Result<RegisteredAuthUser> {
        val cleanId = identifier.trim()
        val (isValid, isVipCode) = verifyCodeFromAdmin(cleanId, fullName, gmail, enteredCode)
        if (!isValid) {
            return Result.failure(
                IllegalArgumentException(
                    tr(
                        lang,
                        "دەلىللەش كودى خاتا! باشقۇرغۇچى «$cleanId» ئۈچۈن ئەۋەتكەن توغرا كودنى كىرگۈزۈڭ.",
                        "رمز التفعيل غير صحيح! يرجى إدخال الرمز المرسل من المسؤول لحساب «$cleanId»."
                    )
                )
            )
        }
        val users = loadRegisteredUsers().toMutableList()
        val idx = users.indexOfFirst {
            it.identifier.equals(cleanId, ignoreCase = true) ||
                it.gmail.equals(cleanId, ignoreCase = true) ||
                it.fullName.equals(cleanId, ignoreCase = true)
        }
        val updatedUser = if (idx >= 0) {
            users[idx].copy(
                isVerified = true,
                isVip = isVipCode || users[idx].isVip,
                vipExpireMillis = if (isVipCode) System.currentTimeMillis() + 30L * 24L * 3600L * 1000L else users[idx].vipExpireMillis,
                licenseCode = enteredCode.trim().uppercase()
            ).also { users[idx] = it }
        } else {
            RegisteredAuthUser(
                identifier = cleanId,
                authMethod = if (cleanId.contains("@")) "BY_GMAIL" else "BY_NAME",
                fullName = fullName.ifBlank { cleanId },
                gmail = if (cleanId.contains("@")) cleanId else gmail.ifBlank { "$cleanId@gmail.com" },
                passwordHash = sha256("123456"),
                address = "",
                isVerified = true,
                isVip = isVipCode,
                vipExpireMillis = if (isVipCode) System.currentTimeMillis() + 30L * 24L * 3600L * 1000L else 0L,
                licenseCode = enteredCode.trim().uppercase()
            ).also { users.add(0, it) }
        }
        saveRegisteredUsers(users)
        setActiveIdentifier(updatedUser.identifier)
        return Result.success(updatedUser)
    }

    fun loginExistingUser(
        identifier: String,
        password: String,
        lang: ClientLanguage
    ): Result<RegisteredAuthUser> {
        val cleanId = identifier.trim()
        val matched = loadRegisteredUsers().firstOrNull {
            it.identifier.equals(cleanId, ignoreCase = true) ||
                it.gmail.equals(cleanId, ignoreCase = true) ||
                it.fullName.equals(cleanId, ignoreCase = true)
        } ?: return Result.failure(
            IllegalArgumentException(
                tr(
                    lang,
                    "بۇ ھېسابات تېپىلمىدى، ئالدى بىلەن تىزىملىتىپ باشقۇرغۇچىدىن كود ئېلىڭ",
                    "هذا الحساب غير موجود، يرجى التسجيل أولاً والحصول على رمز من المسؤول"
                )
            )
        )

        if (matched.passwordHash != sha256(password.trim())) {
            return Result.failure(
                IllegalArgumentException(tr(lang, "مەخپىي نومۇر خاتا!", "كلمة المرور غير صحيحة!"))
            )
        }

        val cfg = loadRemoteAdminConfig()
        if (!matched.canDirectLoginWithoutCode(cfg.requireVerificationCode)) {
            return Result.failure(IllegalStateException("NEED_CODE"))
        }

        setActiveIdentifier(matched.identifier)
        return Result.success(matched)
    }

    fun loadPersonAccounts(ownerIdentifier: String): List<PersonAccountItem> {
        val all = loadAllPersonAccountsInternal()
        return all.filter {
            it.ownerIdentifier.isBlank() ||
                it.ownerIdentifier.equals(ownerIdentifier, ignoreCase = true) ||
                it.ownerIdentifier == "ALL"
        }
    }

    fun savePersonAccountsForUser(ownerIdentifier: String, userAccounts: List<PersonAccountItem>) {
        val others = loadAllPersonAccountsInternal().filterNot {
            it.ownerIdentifier.isBlank() || it.ownerIdentifier.equals(ownerIdentifier, ignoreCase = true)
        }
        saveAllPersonAccountsInternal(userAccounts + others)
    }

    private fun loadAllPersonAccountsInternal(): List<PersonAccountItem> {
        val text = runCatching {
            if (dbFile.exists()) dbFile.readText(Charsets.UTF_8) else ""
        }.getOrDefault("").ifBlank {
            prefs.getString("person_accounts_v4", "[]") ?: "[]"
        }
        if (text.isBlank() || text == "[]") return emptyList()
        return try {
            val arr = JSONArray(text)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val mArr = o.optJSONArray("paidMonths")
                val months = List(12) { idx -> mArr?.optBoolean(idx, false) ?: false }
                PersonAccountItem(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    ownerIdentifier = o.optString("ownerIdentifier"),
                    fullName = o.optString("fullName"),
                    accountName = o.optString("accountName", o.optString("username")),
                    password = o.optString("password"),
                    streetName = o.optString("streetName"),
                    houseNumber = o.optString("houseNumber"),
                    flowAmount = o.optString("flowAmount").ifBlank { "100 GB" },
                    openDate = o.optString("openDate"),
                    notes = o.optString("notes"),
                    paidMonths = months
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAllPersonAccountsInternal(list: List<PersonAccountItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("ownerIdentifier", item.ownerIdentifier)
                    put("fullName", item.fullName)
                    put("accountName", item.accountName)
                    put("password", item.password)
                    put("streetName", item.streetName)
                    put("houseNumber", item.houseNumber)
                    put("flowAmount", item.flowAmount.ifBlank { "100 GB" })
                    put("openDate", item.openDate)
                    put("notes", item.notes)
                    put("paidMonths", JSONArray(item.paidMonths))
                }
            )
        }
        val str = arr.toString()
        prefs.edit().putString("person_accounts_v4", str).commit()
        runCatching { dbFile.writeText(str, Charsets.UTF_8) }
    }

    fun loadReminders(): List<AdminReminderNote> {
        val raw = prefs.getString("reminders_v4", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                AdminReminderNote(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    content = o.optString("content"),
                    targetUsername = o.optString("targetUsername", "ALL"),
                    timestamp = o.optString("timestamp")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadFeedbacks(): List<ClientFeedbackNote> {
        val raw = prefs.getString("feedback_v4", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ClientFeedbackNote(
                    id = o.optString("id"),
                    clientUsername = o.optString("clientUsername"),
                    clientName = o.optString("clientName"),
                    message = o.optString("message"),
                    adminReply = o.optString("adminReply"),
                    timestamp = o.optString("timestamp")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFeedbacks(list: List<ClientFeedbackNote>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("clientUsername", f.clientUsername)
                    put("clientName", f.clientName)
                    put("message", f.message)
                    put("adminReply", f.adminReply)
                    put("timestamp", f.timestamp)
                }
            )
        }
        prefs.edit().putString("feedback_v4", arr.toString()).commit()
    }

    fun loadRemoteAdminUpdate(): RemoteAdminUpdateInfo? {
        val ver = prefs.getString("remote_upd_version", "") ?: ""
        val url = prefs.getString("remote_upd_url", "") ?: ""
        if (ver.isBlank() || ver == CURRENT_CLIENT_VERSION) return null
        return RemoteAdminUpdateInfo(
            versionName = ver,
            apkDownloadUrl = url,
            releaseNotes = prefs.getString("remote_upd_notes", "") ?: "",
            forceUpdate = prefs.getBoolean("remote_upd_force", false),
            targetAccount = prefs.getString("remote_upd_target", "ALL") ?: "ALL"
        )
    }

    fun loadRemoteAdminConfig(): RemoteAdminConfig {
        return RemoteAdminConfig(
            allowNewRegistration = prefs.getBoolean("remote_cfg_allow_reg", true),
            requireVerificationCode = prefs.getBoolean("remote_cfg_req_code", true),
            remoteMaintenanceLock = prefs.getBoolean("remote_cfg_lock", false),
            announcementBanner = prefs.getString("remote_cfg_banner", "") ?: ""
        )
    }

    suspend fun syncWithAdminCloud(lang: ClientLanguage): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = getCloudSyncUrl().trim()
            if (endpoint.isEmpty()) {
                return@runCatching tr(
                    lang,
                    "بۇلۇت ئادرېسى تەڭشەلمىگەن، يەرلىك ئامبار ئىشلىتىلىۋاتىدۇ",
                    "لم يتم تعيين رابط السحابة، يتم استخدام التخزين المحلي"
                )
            }
            val targetUrl = if (endpoint.endsWith(".json")) endpoint else "$endpoint/hesabat_sync.json"
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            importAdminPayloadJson(text)
            tr(
                lang,
                "باشقۇرغۇچى ئەپى بىلەن مۇۋەپپەقىيەتلىك ماس قەدەملەندى!",
                "تمت المزامنة بنجاح مع تطبيق المسؤول!"
            )
        }
    }

    fun importAdminPayloadJson(jsonText: String): Result<Int> {
        return runCatching {
            val root = JSONObject(jsonText)

            // 1. باشقۇرغۇچى ئەۋەتكەن يېڭى نەشر ئۇچۇرىنى قوبۇل قىلىش
            root.optJSONObject("appUpdate")?.let { upd ->
                prefs.edit()
                    .putString("remote_upd_version", upd.optString("versionName"))
                    .putString("remote_upd_url", upd.optString("apkDownloadUrl"))
                    .putString("remote_upd_notes", upd.optString("releaseNotes"))
                    .putBoolean("remote_upd_force", upd.optBoolean("forceUpdate", false))
                    .putString("remote_upd_target", upd.optString("targetAccount", "ALL"))
                    .commit()
            }

            // 2. باشقۇرغۇچى ئەۋەتكەن يىراقتىن باشقۇرۇش تەڭشەكلىرىنى قوبۇل قىلىش
            root.optJSONObject("clientConfig")?.let { cfg ->
                prefs.edit()
                    .putBoolean("remote_cfg_allow_reg", cfg.optBoolean("allowNewRegistration", true))
                    .putBoolean("remote_cfg_req_code", cfg.optBoolean("requireVerificationCode", true))
                    .putBoolean("remote_cfg_lock", cfg.optBoolean("remoteMaintenanceLock", false))
                    .putString("remote_cfg_banner", cfg.optString("announcementBanner", ""))
                    .commit()
            }

            root.optJSONArray("reminders")?.let {
                prefs.edit().putString("reminders_v4", it.toString()).commit()
            }
            root.optJSONArray("feedback")?.let {
                prefs.edit().putString("feedback_v4", it.toString()).commit()
            }

            // 3. باشقۇرغۇچى ئېچىپ بەرگەن ھېساباتلارنى قوبۇل قىلىش
            val accArr = root.optJSONArray("accounts") ?: return@runCatching 0
            val authUsers = loadRegisteredUsers().toMutableList()

            for (i in 0 until accArr.length()) {
                val o = accArr.optJSONObject(i) ?: continue
                val uname = o.optString("username").ifBlank { o.optString("gmail") }
                val gmail = o.optString("gmail", "$uname@gmail.com")
                val fullName = o.optString("fullName", uname)
                val pwd = o.optString("password", "123456")
                val isVip = o.optBoolean("isVip", false)
                val vipExp = o.optLong("vipExpireEpochMillis", 0L)
                val code = o.optString("licenseCode")
                val method = o.optString("openMethod", "BY_GMAIL")

                val idx = authUsers.indexOfFirst {
                    it.identifier.equals(uname, ignoreCase = true) ||
                        it.gmail.equals(gmail, ignoreCase = true)
                }
                val syncedUser = RegisteredAuthUser(
                    identifier = uname,
                    authMethod = method,
                    fullName = fullName,
                    gmail = gmail,
                    passwordHash = sha256(pwd),
                    address = o.optString("address"),
                    isVerified = true,
                    isVip = isVip,
                    vipExpireMillis = vipExp,
                    licenseCode = code
                )
                if (idx >= 0) {
                    authUsers[idx] = syncedUser
                } else if (uname.isNotBlank()) {
                    authUsers.add(syncedUser)
                }
            }
            saveRegisteredUsers(authUsers)
            accArr.length()
        }
    }

    fun clearAllClientData() {
        prefs.edit()
            .remove("person_accounts_v4")
            .remove("reminders_v4")
            .remove("feedback_v4")
            .commit()
        runCatching { if (dbFile.exists()) dbFile.delete() }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

enum class HomeOverlayView {
    NONE,
    SEARCH_RESULTS,
    ALL_ACCOUNTS,
    PAID_ACCOUNTS,
    DEBT_ACCOUNTS
}

@Composable
fun ClientBrandLogo(
    customAvatar: ImageBitmap?,
    sizeDp: Int = 52
) {
    if (customAvatar != null) {
        Image(
            bitmap = customAvatar,
            contentDescription = "Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .border(2.dp, RoyalGold, CircleShape)
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.ic_client_logo),
            contentDescription = "Royal Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .border(2.dp, RoyalGold, CircleShape)
        )
    }
}

@Composable
fun ClientRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val vault = remember { ClientVaultManager(context.applicationContext) }

    var lang by remember { mutableStateOf(vault.getLanguage()) }
    var isDark by remember { mutableStateOf(vault.isDarkMode()) }
    var activeUser by remember { mutableStateOf(vault.getActiveUser()) }
    var registeredUsers by remember { mutableStateOf(vault.loadRegisteredUsers()) }

    var customBgBitmap by remember { mutableStateOf(vault.loadCustomBackgroundBitmap()) }
    var customAvatarBitmap by remember { mutableStateOf(vault.loadCustomAvatarBitmap()) }

    var selectedTab by remember { mutableStateOf(ClientBottomTab.HOME) }
    var homeOverlay by remember { mutableStateOf(HomeOverlayView.NONE) }
    var searchQuery by remember { mutableStateOf("") }

    var accounts by remember(activeUser?.identifier) {
        mutableStateOf(vault.loadPersonAccounts(activeUser?.identifier ?: ""))
    }
    var reminders by remember { mutableStateOf(vault.loadReminders()) }
    var feedbacks by remember { mutableStateOf(vault.loadFeedbacks()) }
    var remoteUpdate by remember { mutableStateOf(vault.loadRemoteAdminUpdate()) }
    var remoteConfig by remember { mutableStateOf(vault.loadRemoteAdminConfig()) }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<PersonAccountItem?>(null) }

    // ھەقىقىي، مۇقىم، خالىغانچە جۆيلىمەيدىغان تور ئېقىمى ۋە Fast.com ئۆلچىگۈچ ھالىتى
    var isNetworkConnected by remember { mutableStateOf(false) }
    var connectionTypeLabel by remember { mutableStateOf("") }
    var liveDownKbps by remember { mutableFloatStateOf(0f) }
    var liveUpKbps by remember { mutableFloatStateOf(0f) }
    var sessionUsedMb by remember { mutableFloatStateOf(0f) }

    var isRunningFastTest by remember { mutableStateOf(false) }
    var fastLiveGaugeMbps by remember { mutableFloatStateOf(0f) }
    var lastFastResult by remember { mutableStateOf<FastSpeedTestResult?>(null) }

    LaunchedEffect(lang) {
        val initialRx = TrafficStats.getTotalRxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
        val initialTx = TrafficStats.getTotalTxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
        val startTotal = initialRx + initialTx

        var prevRx = initialRx
        var prevTx = initialTx
        var prevTimeMs = SystemClock.elapsedRealtime()

        while (true) {
            delay(1000L)
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val caps = if (net != null) cm.getNetworkCapabilities(net) else null
            val connected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            isNetworkConnected = connected

            connectionTypeLabel = when {
                !connected -> tr(lang, "تور ئۇلانمىغان", "غير متصل بالشبكة")
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> tr(lang, "Wi-Fi سىمسىز تور", "شبكة Wi-Fi لاسلكية")
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> tr(lang, "كۆچمە تور (4G/5G)", "بيانات الهاتف (4G/5G)")
                else -> tr(lang, "تور ئۇلانغان", "متصل بالإنترنت")
            }

            val nowRx = TrafficStats.getTotalRxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: prevRx
            val nowTx = TrafficStats.getTotalTxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: prevTx
            val nowTimeMs = SystemClock.elapsedRealtime()
            val dtSec = ((nowTimeMs - prevTimeMs).coerceAtLeast(500L)) / 1000f

            if (!connected) {
                liveDownKbps = 0f
                liveUpKbps = 0f
            } else {
                val dRx = (nowRx - prevRx).coerceAtLeast(0L)
                val dTx = (nowTx - prevTx).coerceAtLeast(0L)
                // تۇيۇقسىز سەكرەشنىڭ ئالدىنى ئېلىش ئۈچۈن سىلىقلاشتۇرۇلغان ھەقىقىي قىممەت
                val instantDown = (dRx / 1024f) / dtSec
                val instantUp = (dTx / 1024f) / dtSec
                liveDownKbps = if (instantDown < 0.2f) 0f else (liveDownKbps * 0.35f + instantDown * 0.65f)
                liveUpKbps = if (instantUp < 0.2f) 0f else (liveUpKbps * 0.35f + instantUp * 0.65f)
            }

            prevRx = nowRx
            prevTx = nowTx
            prevTimeMs = nowTimeMs

            val usedBytes = ((nowRx + nowTx) - startTotal).coerceAtLeast(0L)
            sessionUsedMb = usedBytes / (1024f * 1024f)
        }
    }

    val notifyAdminRegistration: (RegistrationAuthMethod, String, String, String, String) -> Unit = { method, idText, fullName, address, gmail ->
        runCatching {
            val methodTitle = tr(lang, method.ugLabel, method.arLabel)
            val subject = "Hesabat يېڭى ھېسابات ئىلتىماسى: $idText"
            val body = buildString {
                appendLine("ئەسسالامۇئەلەيكۇم باشقۇرغۇچى ($ADMIN_MASTER_EMAIL)،")
                appendLine("يېڭى ئىشلەتكۈچى ئەپكە تىزىملاپ كىرمەكچى:")
                appendLine("• تىزىملاتقان ئۇسۇل: $methodTitle")
                appendLine("• ھېسابات / كىملىك: $idText")
                appendLine("• تولۇق ئىسمى: ${fullName.ifBlank { idText }}")
                appendLine("• Gmail ئادرېسى: ${gmail.ifBlank { "$idText@gmail.com" }}")
                appendLine("• ئورنى / ئادرېسى: ${address.ifBlank { "يېزىلمىغان" }}")
                appendLine("\nباشقۇرغۇچى ئەپىدىن «$idText» ئۈچۈن دەلىللەش كودى ئەۋەتىپ بېرىڭ.")
            }
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$ADMIN_MASTER_EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(Intent.createChooser(intent, tr(lang, "باشقۇرغۇچىغا ئۇچۇر يوللاش", "إرسال طلب للمسؤول")))
        }
    }

    val saveUserAccounts: (List<PersonAccountItem>) -> Unit = { updated ->
        val owner = activeUser?.identifier ?: ""
        accounts = updated
        vault.savePersonAccountsForUser(owner, updated)
    }

    val bgColor = if (isDark) ClientDarkBg else Color(0xFFF8FAFC)
    val cardColor = if (isDark) ClientCardBg else Color(0xF2FFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (isDark) {
                darkColorScheme(
                    primary = RoyalGold,
                    secondary = ClientTurquoise,
                    background = bgColor,
                    surface = cardColor,
                    onSurface = textColor,
                    onBackground = textColor
                )
            } else {
                lightColorScheme(
                    primary = RoyalCrimson,
                    secondary = ClientTurquoise,
                    background = bgColor,
                    surface = cardColor,
                    onSurface = textColor,
                    onBackground = textColor
                )
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (customBgBitmap != null) {
                    Image(
                        bitmap = customBgBitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isDark) Color(0xAA050A18) else Color(0x88FFFFFF))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    if (isDark) listOf(Color(0xFF14070A), Color(0xFF0B1328), Color(0xFF060A14))
                                    else listOf(Color(0xFFFEF3C7), Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                                )
                            )
                    )
                }

                // ئەگەر باشقۇرغۇچى ئەپنى يىراقتىن قۇلۇپلىغان بولسا
                if (remoteConfig.remoteMaintenanceLock) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = cardColor,
                            border = BorderStroke(1.5.dp, ClientRose)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = ClientRose, modifier = Modifier.size(48.dp))
                                Text(
                                    text = tr(lang, "ئەپ باشقۇرغۇچى تەرىپىدىن ۋاقىتلىق قۇلۇپلاندى", "تم قفل التطبيق مؤقتاً من قبل المسؤول"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textColor,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = ADMIN_MASTER_EMAIL,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = RoyalGold
                                )
                            }
                        }
                    }
                } else if (activeUser == null) {
                    ClientStructuredAuthScreen(
                        lang = lang,
                        cardColor = cardColor,
                        textColor = textColor,
                        customAvatar = customAvatarBitmap,
                        registeredUsers = registeredUsers,
                        allowRegistration = remoteConfig.allowNewRegistration,
                        onSwitchLang = { newLang ->
                            lang = newLang
                            vault.setLanguage(newLang)
                        },
                        onRequestAdminApproval = { method, identifier, fullName, gmail, pwd, address ->
                            vault.createPendingUserRequest(method, identifier, fullName, gmail, pwd, address)
                            registeredUsers = vault.loadRegisteredUsers()
                            notifyAdminRegistration(method, identifier, fullName, address, gmail)
                        },
                        onVerifyCodeAndEnter = { identifier, fullName, gmail, code ->
                            val res = vault.verifyUserWithAdminCode(identifier, fullName, gmail, code, lang)
                            res.onSuccess { verifiedUser ->
                                registeredUsers = vault.loadRegisteredUsers()
                                activeUser = verifiedUser
                                accounts = vault.loadPersonAccounts(verifiedUser.identifier)
                            }
                            res.map { Unit }
                        },
                        onDirectLogin = { identifier, pwd ->
                            val res = vault.loginExistingUser(identifier, pwd, lang)
                            res.onSuccess { user ->
                                activeUser = user
                                accounts = vault.loadPersonAccounts(user.identifier)
                            }
                            res.map { Unit }
                        }
                    )
                } else {
                    val currentUser = activeUser!!

                    if (homeOverlay != HomeOverlayView.NONE) {
                        BackHandler { homeOverlay = HomeOverlayView.NONE }
                        ClientDedicatedOverlayPage(
                            lang = lang,
                            overlayType = homeOverlay,
                            allAccounts = accounts,
                            searchQuery = searchQuery,
                            cardColor = cardColor,
                            textColor = textColor,
                            onSearchQueryChange = { searchQuery = it },
                            onBack = { homeOverlay = HomeOverlayView.NONE },
                            onToggleMonth = { item, mIdx ->
                                val newMonths = item.paidMonths.toMutableList().also { it[mIdx] = !it[mIdx] }
                                saveUserAccounts(
                                    accounts.map { if (it.id == item.id) it.copy(paidMonths = newMonths) else it }
                                )
                            },
                            onEdit = { item ->
                                editingPerson = item
                                showAddEditDialog = true
                            },
                            onDelete = { item ->
                                saveUserAccounts(accounts.filterNot { it.id == item.id })
                                scope.launch {
                                    snackbarHostState.showSnackbar(tr(lang, "«${item.fullName}» ئۆچۈرۈلدى", "تم حذف «${item.fullName}»"))
                                }
                            },
                            onCopy = { label, text ->
                                clipboard.setText(AnnotatedString(text))
                                scope.launch {
                                    snackbarHostState.showSnackbar("$label: $text")
                                }
                            }
                        )
                    } else {
                        Scaffold(
                            containerColor = Color.Transparent,
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            bottomBar = {
                                NavigationBar(
                                    containerColor = if (isDark) Color(0xEE0B1224) else Color(0xF2FFFFFF),
                                    tonalElevation = 10.dp
                                ) {
                                    ClientBottomTab.entries.forEach { tab ->
                                        NavigationBarItem(
                                            selected = selectedTab == tab,
                                            onClick = { selectedTab = tab },
                                            icon = { Icon(tab.icon, contentDescription = null) },
                                            label = {
                                                Text(
                                                    text = tr(lang, tab.ugTitle, tab.arTitle),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (selectedTab == tab) FontWeight.ExtraBold else FontWeight.Normal
                                                )
                                            }
                                        )
                                    }
                                }
                            },
                            floatingActionButton = {
                                if (selectedTab == ClientBottomTab.ACCOUNTS) {
                                    ExtendedFloatingActionButton(
                                        onClick = {
                                            editingPerson = null
                                            showAddEditDialog = true
                                        },
                                        containerColor = RoyalGold,
                                        contentColor = Color.Black,
                                        icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                                        text = {
                                            Text(
                                                text = tr(lang, "يېڭى ھېسابات قوشۇش", "إضافة حساب جديد"),
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(horizontal = 16.dp)
                            ) {
                                // ئۈستى قىسىم: لوگو / باش سۈرەت، ھېسابات ئىسمى، VIP بەلگىسى
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 10.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = cardColor,
                                    border = BorderStroke(1.2.dp, RoyalGold.copy(alpha = 0.55f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            ClientBrandLogo(customAvatar = customAvatarBitmap, sizeDp = 48)
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = currentUser.fullName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = textColor
                                                    )
                                                    if (currentUser.isVip) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = RoyalGold
                                                        ) {
                                                            Text(
                                                                text = "VIP",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color.Black,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = currentUser.gmail,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ClientTurquoise
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                vault.setActiveIdentifier(null)
                                                activeUser = null
                                            }
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = ClientRose)
                                        }
                                    }
                                }

                                // باشقۇرغۇچى ئەۋەتكەن يېڭى نەشر ئۇقتۇرۇشى بولسا كۆرسىتىش
                                remoteUpdate?.let { upd ->
                                    if (upd.targetAccount == "ALL" || upd.targetAccount.equals(currentUser.identifier, ignoreCase = true)) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = RoyalGold.copy(alpha = 0.2f),
                                            border = BorderStroke(1.5.dp, RoyalGold),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = tr(lang, "يېڭى نەشر تارقىتىلدى: ${upd.versionName}", "يتوفر إصدار جديد: ${upd.versionName}"),
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = textColor
                                                    )
                                                    if (upd.releaseNotes.isNotBlank()) {
                                                        Text(upd.releaseNotes, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.8f))
                                                    }
                                                }
                                                if (upd.apkDownloadUrl.isNotBlank()) {
                                                    Button(
                                                        onClick = {
                                                            runCatching {
                                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(upd.apkDownloadUrl)))
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = RoyalGold)
                                                    ) {
                                                        Text(tr(lang, "يېڭىلاش", "تحديث"), color = Color.Black, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                when (selectedTab) {
                                    ClientBottomTab.HOME -> {
                                        ClientHomeScreen(
                                            lang = lang,
                                            accounts = accounts,
                                            searchQuery = searchQuery,
                                            announcementBanner = remoteConfig.announcementBanner,
                                            isNetworkConnected = isNetworkConnected,
                                            connectionTypeLabel = connectionTypeLabel,
                                            liveDownloadKbps = liveDownKbps,
                                            liveUploadKbps = liveUpKbps,
                                            sessionUsedMb = sessionUsedMb,
                                            isRunningSpeedTest = isRunningFastTest,
                                            fastLiveGaugeMbps = fastLiveGaugeMbps,
                                            lastFastResult = lastFastResult,
                                            cardColor = cardColor,
                                            textColor = textColor,
                                            onSearchQueryChange = { searchQuery = it },
                                            onTriggerSearch = { homeOverlay = HomeOverlayView.SEARCH_RESULTS },
                                            onOpenCategoryOverlay = { targetOverlay -> homeOverlay = targetOverlay },
                                            onRunFastSpeedTest = {
                                                if (!isRunningFastTest) {
                                                    if (!isNetworkConnected) {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(
                                                                tr(lang, "تور ئۇلانمىغان! ئالدى بىلەن Wi-Fi ياكى كۆچمە تورنى ئۇلاڭ.", "الشبكة غير متصلة! يرجى الاتصال بـ Wi-Fi أو بيانات الهاتف أولاً.")
                                                            )
                                                        }
                                                    } else {
                                                        scope.launch {
                                                            isRunningFastTest = true
                                                            fastLiveGaugeMbps = 0f
                                                            val result = runCalibratedFastSpeedTest { instantMbps ->
                                                                fastLiveGaugeMbps = instantMbps
                                                            }
                                                            lastFastResult = result
                                                            isRunningFastTest = false
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    ClientBottomTab.SETTINGS -> {
                                        ClientSettingsScreen(
                                            lang = lang,
                                            isDark = isDark,
                                            vault = vault,
                                            cardColor = cardColor,
                                            textColor = textColor,
                                            onLanguageChange = { newLang ->
                                                lang = newLang
                                                vault.setLanguage(newLang)
                                            },
                                            onDarkChange = { dark ->
                                                isDark = dark
                                                vault.setDarkMode(dark)
                                            },
                                            onBackgroundPicked = { uri ->
                                                if (vault.saveCustomBackgroundFromUri(uri)) {
                                                    customBgBitmap = vault.loadCustomBackgroundBitmap()
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(tr(lang, "تەگلىك رەسىم ئالماشتۇرۇلدى!", "تم تغيير صورة الخلفية!"))
                                                    }
                                                }
                                            },
                                            onResetBackground = {
                                                vault.clearCustomBackground()
                                                customBgBitmap = null
                                            },
                                            onAvatarPicked = { uri ->
                                                if (vault.saveCustomAvatarFromUri(uri)) {
                                                    customAvatarBitmap = vault.loadCustomAvatarBitmap()
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(tr(lang, "باش سۈرەت / لوگو توغرىلاندى!", "تم تحديث الصورة الشخصية / الشعار!"))
                                                    }
                                                }
                                            },
                                            onSyncCompleted = { msg ->
                                                accounts = vault.loadPersonAccounts(currentUser.identifier)
                                                reminders = vault.loadReminders()
                                                feedbacks = vault.loadFeedbacks()
                                                remoteUpdate = vault.loadRemoteAdminUpdate()
                                                remoteConfig = vault.loadRemoteAdminConfig()
                                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                            },
                                            onClearVault = {
                                                vault.clearAllClientData()
                                                accounts = emptyList()
                                                reminders = emptyList()
                                                feedbacks = emptyList()
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(tr(lang, "ئەپ ئىچىدىكى مەلۇماتلار تازىلاندى!", "تم مسح بيانات التطبيق!"))
                                                }
                                            }
                                        )
                                    }

                                    ClientBottomTab.ACCOUNTS -> {
                                        ClientAccountsScreen(
                                            lang = lang,
                                            accounts = accounts,
                                            cardColor = cardColor,
                                            textColor = textColor,
                                            onSearchClick = {
                                                searchQuery = ""
                                                homeOverlay = HomeOverlayView.SEARCH_RESULTS
                                            },
                                            onToggleMonth = { item, mIdx ->
                                                val newMonths = item.paidMonths.toMutableList().also { it[mIdx] = !it[mIdx] }
                                                saveUserAccounts(
                                                    accounts.map { if (it.id == item.id) it.copy(paidMonths = newMonths) else it }
                                                )
                                            },
                                            onEdit = { item ->
                                                editingPerson = item
                                                showAddEditDialog = true
                                            },
                                            onDelete = { item ->
                                                saveUserAccounts(accounts.filterNot { it.id == item.id })
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(tr(lang, "«${item.fullName}» ئۆچۈرۈلدى", "تم حذف «${item.fullName}»"))
                                                }
                                            },
                                            onCopy = { label, value ->
                                                clipboard.setText(AnnotatedString(value))
                                                scope.launch { snackbarHostState.showSnackbar("$label: $value") }
                                            }
                                        )
                                    }

                                    ClientBottomTab.REMINDERS -> {
                                        ClientRemindersAndFeedbackScreen(
                                            lang = lang,
                                            currentUser = currentUser,
                                            reminders = reminders,
                                            feedbacks = feedbacks,
                                            cardColor = cardColor,
                                            textColor = textColor,
                                            onSendFeedback = { msgText ->
                                                val newFb = ClientFeedbackNote(
                                                    id = UUID.randomUUID().toString(),
                                                    clientUsername = currentUser.identifier,
                                                    clientName = currentUser.fullName,
                                                    message = msgText,
                                                    adminReply = "",
                                                    timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                                                )
                                                val updated = listOf(newFb) + feedbacks
                                                feedbacks = updated
                                                vault.saveFeedbacks(updated)

                                                runCatching {
                                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                        data = Uri.parse("mailto:$ADMIN_MASTER_EMAIL")
                                                        putExtra(Intent.EXTRA_SUBJECT, "Hesabat ئىنكاس (${currentUser.identifier})")
                                                        putExtra(Intent.EXTRA_TEXT, msgText)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, tr(lang, "باشقۇرغۇچىغا يوللاش", "إرسال للمسؤول")))
                                                }
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(tr(lang, "ئىنكاسىڭىز باشقۇرغۇچىغا يوللاندى!", "تم إرسال رسالتك إلى المسؤول!"))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddEditDialog && activeUser != null) {
        PersonAccountFormDialog(
            lang = lang,
            ownerIdentifier = activeUser!!.identifier,
            initial = editingPerson,
            onDismiss = {
                showAddEditDialog = false
                editingPerson = null
            },
            onSave = { item ->
                val exists = accounts.any { it.id == item.id }
                val updated = if (exists) {
                    accounts.map { if (it.id == item.id) item else it }
                } else {
                    listOf(item) + accounts
                }
                saveUserAccounts(updated)
                showAddEditDialog = false
                editingPerson = null
                scope.launch {
                    snackbarHostState.showSnackbar(tr(lang, "ھېسابات ۋە ئېقىم مىقدارى ساقلاندى!", "تم حفظ الحساب وحصة البيانات!"))
                }
            }
        )
    }
}

/**
 * تىزىملىتىپ كىرىش ئېكرانى:
 * ئېنىق قىلىپ «1. Gmail ئارقىلىق تىزىملىتىش» / «2. ئىسىم ئارقىلىق تىزىملىتىش» / «3. ھېسابات نامى ئارقىلىق كىرىش» تىزىملىكى بىلەن چىقىدۇ
 */
@Composable
fun ClientStructuredAuthScreen(
    lang: ClientLanguage,
    cardColor: Color,
    textColor: Color,
    customAvatar: ImageBitmap?,
    registeredUsers: List<RegisteredAuthUser>,
    allowRegistration: Boolean,
    onSwitchLang: (ClientLanguage) -> Unit,
    onRequestAdminApproval: (RegistrationAuthMethod, String, String, String, String, String) -> Unit,
    onVerifyCodeAndEnter: (String, String, String, String) -> Result<Unit>,
    onDirectLogin: (String, String) -> Result<Unit>
) {
    var selectedMethod by remember { mutableStateOf(RegistrationAuthMethod.BY_GMAIL) }
    var isRegisterMode by remember { mutableStateOf(registeredUsers.isEmpty()) }
    var awaitingCodeStep by remember { mutableStateOf(false) }

    var gmailInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var accountIdInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var infoMsg by remember { mutableStateOf<String?>(null) }

    val activeIdentifier = when (selectedMethod) {
        RegistrationAuthMethod.BY_GMAIL -> gmailInput.trim()
        RegistrationAuthMethod.BY_NAME -> nameInput.trim()
        RegistrationAuthMethod.BY_ACCOUNT_ID -> accountIdInput.trim()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 470.dp),
            shape = RoundedCornerShape(30.dp),
            color = cardColor,
            border = BorderStroke(1.8.dp, RoyalGold)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // تىل تاللاش (ئۇيغۇرچە / العربية)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ClientLanguage.entries.forEach { l ->
                        FilterChip(
                            selected = lang == l,
                            onClick = { onSwitchLang(l) },
                            label = { Text(l.title) },
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                ClientBrandLogo(customAvatar = customAvatar, sizeDp = 82)

                Text(
                    text = tr(lang, "Hesabat ھېسابات سىستېمىسى", "نظام حسابات Hesabat"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                    textAlign = TextAlign.Center
                )

                // كىرىش / تىزىملىتىش / كود دەلىللەش باسقۇچى
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isRegisterMode && !awaitingCodeStep,
                        onClick = {
                            isRegisterMode = false
                            awaitingCodeStep = false
                            errorMsg = null
                        },
                        label = { Text(tr(lang, "كىرىش", "دخول")) },
                        modifier = Modifier.weight(1f)
                    )
                    if (allowRegistration) {
                        FilterChip(
                            selected = isRegisterMode && !awaitingCodeStep,
                            onClick = {
                                isRegisterMode = true
                                awaitingCodeStep = false
                                errorMsg = null
                            },
                            label = { Text(tr(lang, "تىزىملىتىش", "تسجيل جديد")) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    FilterChip(
                        selected = awaitingCodeStep,
                        onClick = {
                            awaitingCodeStep = true
                            errorMsg = null
                        },
                        label = { Text(tr(lang, "كود دەلىللەش", "تأكيد الرمز")) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // ئېنىق قىلىپ Gmail ئارقىلىق تىزىملىتىش / ئىسىم ئارقىلىق تىزىملىتىش تىزىملىكلىرى
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = tr(lang, "تىزىملىتىش ۋە كىرىش ئۇسۇلىنى تاللاڭ:", "اختر طريقة التسجيل أو الدخول:"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoyalGold
                    )
                    RegistrationAuthMethod.entries.forEach { method ->
                        val isSelected = selectedMethod == method
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) RoyalGold.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(1.2.dp, if (isSelected) RoyalGold else Color.Gray.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMethod = method
                                    errorMsg = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(method.icon, contentDescription = null, tint = if (isSelected) RoyalGold else ClientTurquoise)
                                Text(
                                    text = tr(lang, method.ugLabel, method.arLabel),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textColor,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = RoyalGold)
                                }
                            }
                        }
                    }
                }

                // ساقلانغان ھېساباتلار بولسا تېز تاللاش
                if (registeredUsers.isNotEmpty() && !awaitingCodeStep) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        registeredUsers.forEach { u ->
                            FilterChip(
                                selected = activeIdentifier.equals(u.identifier, ignoreCase = true),
                                onClick = {
                                    when (u.authMethod) {
                                        "BY_GMAIL" -> {
                                            selectedMethod = RegistrationAuthMethod.BY_GMAIL
                                            gmailInput = u.identifier
                                        }
                                        "BY_NAME" -> {
                                            selectedMethod = RegistrationAuthMethod.BY_NAME
                                            nameInput = u.identifier
                                        }
                                        else -> {
                                            selectedMethod = RegistrationAuthMethod.BY_ACCOUNT_ID
                                            accountIdInput = u.identifier
                                        }
                                    }
                                    isRegisterMode = false
                                    errorMsg = null
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (u.isVip) Icons.Filled.WorkspacePremium else Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = if (u.isVip) RoyalGold else ClientEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(u.identifier) }
                            )
                        }
                    }
                }

                // تاللانغان ئۇسۇلغا ماس كىرگۈزۈش رامكىلىرى
                when (selectedMethod) {
                    RegistrationAuthMethod.BY_GMAIL -> {
                        OutlinedTextField(
                            value = gmailInput,
                            onValueChange = {
                                gmailInput = it
                                errorMsg = null
                            },
                            label = { Text(tr(lang, "Gmail ئادرېسىڭىز *", "عنوان Gmail الخاص بك *")) },
                            placeholder = { Text("example@gmail.com") },
                            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = RoyalGold) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isRegisterMode && !awaitingCodeStep) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text(tr(lang, "ئىسمىڭىز", "الاسم الكامل")) },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = ClientTurquoise) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    RegistrationAuthMethod.BY_NAME -> {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = {
                                nameInput = it
                                errorMsg = null
                            },
                            label = { Text(tr(lang, "ئىسمىڭىز (باشقۇرغۇچى بەرگەن ياكى شەخسىي ئىسىم) *", "الاسم الكامل *")) },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = RoyalGold) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isRegisterMode && !awaitingCodeStep) {
                            OutlinedTextField(
                                value = gmailInput,
                                onValueChange = { gmailInput = it },
                                label = { Text(tr(lang, "Gmail ئادرېسىڭىز (ئىختىيارىي)", "عنوان Gmail (اختياري)")) },
                                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = ClientTurquoise) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    RegistrationAuthMethod.BY_ACCOUNT_ID -> {
                        OutlinedTextField(
                            value = accountIdInput,
                            onValueChange = {
                                accountIdInput = it
                                errorMsg = null
                            },
                            label = { Text(tr(lang, "ھېسابات نامى (ID) *", "معرف الحساب (ID) *")) },
                            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = RoyalGold) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (!awaitingCodeStep) {
                    if (isRegisterMode) {
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text(tr(lang, "ئادرېسىڭىز / كوچا ۋە ئۆي نومۇرى *", "العنوان / الشارع ورقم المنزل *")) },
                            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ClientRose) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMsg = null
                        },
                        label = { Text(tr(lang, "مەخپىي نومۇر *", "كلمة المرور *")) },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = ClientTurquoise) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ClientEmerald.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ClientEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tr(
                                lang,
                                "باشقۇرغۇچى ($ADMIN_MASTER_EMAIL) «$activeIdentifier» ئۈچۈن ئەۋەتكەن دەلىللەش كودىنى (HSB-XXXX-XXXX ياكى VIP-XXXX-XXXX) كىرگۈزۈڭ:",
                                "أدخل رمز التفعيل (HSB-XXXX-XXXX أو VIP-XXXX-XXXX) المرسل من المسؤول لحساب «$activeIdentifier»:"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = {
                            verificationCode = it.uppercase()
                            errorMsg = null
                        },
                        label = { Text(tr(lang, "باشقۇرغۇچى دەلىللەش كودى *", "رمز تفعيل المسؤول *")) },
                        placeholder = { Text("HSB-XXXX-XXXX") },
                        leadingIcon = { Icon(Icons.Filled.Security, contentDescription = null, tint = ClientEmerald) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (infoMsg != null) {
                    Text(infoMsg.orEmpty(), color = ClientEmerald, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                if (errorMsg != null) {
                    Text(errorMsg.orEmpty(), color = ClientRose, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                if (!awaitingCodeStep) {
                    Button(
                        onClick = {
                            if (activeIdentifier.isBlank() || password.isBlank()) {
                                errorMsg = tr(lang, "ھېسابات ئۇچۇرى ۋە مەخپىي نومۇرنى تولۇق كىرگۈزۈڭ!", "يرجى إدخال بيانات الحساب وكلمة المرور بالكامل!")
                                return@Button
                            }
                            if (isRegisterMode) {
                                onRequestAdminApproval(
                                    selectedMethod,
                                    activeIdentifier,
                                    nameInput.ifBlank { activeIdentifier },
                                    gmailInput.ifBlank { activeIdentifier },
                                    password,
                                    address
                                )
                                awaitingCodeStep = true
                                infoMsg = tr(
                                    lang,
                                    "باشقۇرغۇچىغا تىزىملىتىش ئۇچۇرى يوللاندى! ئەمدى باشقۇرغۇچى ئەۋەتكەن دەلىللەش كودىنى كىرگۈزۈڭ.",
                                    "تم إرسال طلب التسجيل للمسؤول! أدخل الآن رمز التفعيل المرسل من المسؤول."
                                )
                            } else {
                                val res = onDirectLogin(activeIdentifier, password)
                                res.onFailure { err ->
                                    if (err.message == "NEED_CODE") {
                                        awaitingCodeStep = true
                                        infoMsg = tr(
                                            lang,
                                            "بۇ ھېساباتنى ئېچىش ئۈچۈن باشقۇرغۇچى ئەۋەتكەن دەلىللەش كودىنى كىرگۈزۈڭ:",
                                            "للدخول إلى هذا الحساب، يرجى إدخال رمز التفعيل المرسل من المسؤول:"
                                        )
                                    } else {
                                        errorMsg = err.message
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalGold),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = if (isRegisterMode) Icons.AutoMirrored.Filled.Send else Icons.Filled.Login,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRegisterMode) {
                                tr(lang, "تىزىملىتىپ باشقۇرغۇچىغا يوللاش", "تسجيل وإرسال الطلب للمسؤول")
                            } else {
                                tr(lang, "ھېساباتقا كىرىش", "تسجيل الدخول")
                            },
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val res = onVerifyCodeAndEnter(
                                activeIdentifier,
                                nameInput,
                                gmailInput,
                                verificationCode
                            )
                            res.onFailure { err -> errorMsg = err.message }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClientEmerald),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr(lang, "كودنى دەلىللەپ ئەپكە كىرىش", "تأكيد الرمز والدخول للتطبيق"),
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1-بەت: باش بەت + سىنبەلگىلىك كارتىلار + دەل ۋە مۇقىم ئىشلەيدىغان Fast.com تور ئۆلچىگۈچ
 */
@Composable
fun ClientHomeScreen(
    lang: ClientLanguage,
    accounts: List<PersonAccountItem>,
    searchQuery: String,
    announcementBanner: String,
    isNetworkConnected: Boolean,
    connectionTypeLabel: String,
    liveDownloadKbps: Float,
    liveUploadKbps: Float,
    sessionUsedMb: Float,
    isRunningSpeedTest: Boolean,
    fastLiveGaugeMbps: Float,
    lastFastResult: FastSpeedTestResult?,
    cardColor: Color,
    textColor: Color,
    onSearchQueryChange: (String) -> Unit,
    onTriggerSearch: () -> Unit,
    onOpenCategoryOverlay: (HomeOverlayView) -> Unit,
    onRunFastSpeedTest: () -> Unit
) {
    val totalCount = accounts.size
    val paidCount = accounts.count { it.paidCount() == 12 }
    val debtCount = accounts.count { it.paidCount() < 12 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (announcementBanner.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = RoyalGold.copy(alpha = 0.18f),
                border = BorderStroke(1.2.dp, RoyalGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, tint = RoyalGold)
                    Text(announcementBanner, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = textColor)
                }
            }
        }

        // 1. باش بەت ئىزدەش رامكىسى
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            border = BorderStroke(1.dp, RoyalGold.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(tr(lang, "ئىسىم، كوچا، ئادرېس ياكى ئېقىم بويىچە ئىزدەش...", "البحث بالاسم أو الشارع أو العنوان أو الحصة..."))
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = RoyalGold) },
                    trailingIcon = {
                        Button(
                            onClick = onTriggerSearch,
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalGold),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(tr(lang, "ئىزدەش", "بحث"), color = Color.Black, fontWeight = FontWeight.ExtraBold)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onTriggerSearch() }),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 2. سىنبەلگە بىلەنلا كۆرۈنىدىغان 3 بۆلۈم (ئىچىدىكى مەزمۇنى يوشۇرۇن، چەككەندە ئېچىلىدۇ)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeIconOnlyCard(
                title = tr(lang, "جەمئىي ھېساباتلار", "إجمالي الحسابات"),
                subtitle = tr(lang, "چېكىپ كۆرۈش ($totalCount)", "اضغط للعرض ($totalCount)"),
                icon = Icons.Filled.Group,
                accent = ClientIndigo,
                cardColor = cardColor,
                textColor = textColor,
                modifier = Modifier.weight(1f),
                onClick = { onOpenCategoryOverlay(HomeOverlayView.ALL_ACCOUNTS) }
            )
            HomeIconOnlyCard(
                title = tr(lang, "ھەق تاپشۇرغانلار", "الدافعون بالكامل"),
                subtitle = tr(lang, "چېكىپ كۆرۈش ($paidCount)", "اضغط للعرض ($paidCount)"),
                icon = Icons.Filled.CheckCircle,
                accent = ClientEmerald,
                cardColor = cardColor,
                textColor = textColor,
                modifier = Modifier.weight(1f),
                onClick = { onOpenCategoryOverlay(HomeOverlayView.PAID_ACCOUNTS) }
            )
        }

        HomeIconOnlyCard(
            title = tr(lang, "قەرزگە ئاچقۇزغانلار", "الحسابات المفتوحة بالدين"),
            subtitle = tr(lang, "چېكىپ تەپسىلاتىنى كۆرۈش ($debtCount)", "اضغط لعرض التفاصيل ($debtCount)"),
            icon = Icons.Filled.WarningAmber,
            accent = ClientRose,
            cardColor = cardColor,
            textColor = textColor,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpenCategoryOverlay(HomeOverlayView.DEBT_ACCOUNTS) }
        )

        // 3. تۈزىتىلگەن ھەقىقىي FAST.com تور ئېقىم مىقدارى ۋە تېزلىك ئۆلچىگۈچ
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = cardColor,
            border = BorderStroke(1.5.dp, if (isNetworkConnected) ClientTurquoise.copy(alpha = 0.6f) else ClientRose.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (isNetworkConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                            contentDescription = null,
                            tint = if (isNetworkConnected) ClientTurquoise else ClientRose,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = tr(lang, "FAST سىمسىز تور ئېقىم ئۆلچىگۈچ", "مقياس سرعة واستهلاك الشبكة FAST"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = textColor
                            )
                            Text(
                                text = connectionTypeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isNetworkConnected) ClientEmerald else ClientRose
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = RoyalGold.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f MB", sessionUsedMb),
                            style = MaterialTheme.typography.labelMedium,
                            color = RoyalGold,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // چوڭ FAST.com سائەت ئېكرانى
                val activeMbpsDisplay = when {
                    isRunningSpeedTest -> fastLiveGaugeMbps
                    lastFastResult != null -> lastFastResult.downloadMbps
                    else -> (liveDownloadKbps * 8f) / 1024f
                }

                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .clip(CircleShape)
                        .border(
                            4.dp,
                            Brush.sweepGradient(listOf(RoyalGold, ClientEmerald, ClientTurquoise, RoyalGold)),
                            CircleShape
                        )
                        .background(Color(0xFF091020)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format(Locale.US, "%.2f", activeMbpsDisplay),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isNetworkConnected) ClientEmerald else ClientRose
                        )
                        Text(
                            text = "Mbps",
                            style = MaterialTheme.typography.labelMedium,
                            color = RoyalGold,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (lastFastResult != null && !isRunningSpeedTest) {
                            Text(
                                text = String.format(Locale.US, "(%.2f MB/s • %d ms)", lastFastResult.downloadMBps, lastFastResult.pingMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = ClientTurquoise
                            )
                        }
                    }
                }

                // شۇ ۋاقىتتىكى ھەقىقىي ئېقىم (جۆيلىمەي پەقەت ئەمەلىي ئېقىمنى كۆرسىتىدۇ)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr(lang, "ھازىرقى چۈشۈش ئېقىمى", "التحميل الفوري"), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
                        Text(
                            text = if (liveDownloadKbps >= 1024f) {
                                String.format(Locale.US, "%.2f MB/s", liveDownloadKbps / 1024f)
                            } else {
                                String.format(Locale.US, "%.1f KB/s", liveDownloadKbps)
                            },
                            fontWeight = FontWeight.ExtraBold,
                            color = ClientEmerald
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr(lang, "ھازىرقى يوللاش ئېقىمى", "الرفع الفوري"), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
                        Text(
                            text = if (liveUploadKbps >= 1024f) {
                                String.format(Locale.US, "%.2f MB/s", liveUploadKbps / 1024f)
                            } else {
                                String.format(Locale.US, "%.1f KB/s", liveUploadKbps)
                            },
                            fontWeight = FontWeight.ExtraBold,
                            color = ClientTurquoise
                        )
                    }
                }

                Button(
                    onClick = onRunFastSpeedTest,
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalGold),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRunningSpeedTest) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr(lang, "FAST تور تېزلىكى ئەمەلىي ئۆلچىنىۋاتىدۇ...", "جاري قياس سرعة الشبكة الفعلي..."),
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold
                        )
                    } else {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr(lang, "FAST تور تېزلىكىنى ئۆلچەش", "بدء اختبار سرعة الشبكة FAST"),
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun HomeIconOnlyCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    cardColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        color = cardColor,
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 20.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(30.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                textAlign = TextAlign.Center
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.18f)
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * 2-بەت: تەڭشەكلەر (تەگلىك، باش سۈرەت/لوگو، ئۇيغۇرچە/ئەرەبچە تىل، كېچە-كۈندۈزلۈك ھالەت، باشقۇرغۇچى ئەپىگە باغلاش)
 */
@Composable
fun ClientSettingsScreen(
    lang: ClientLanguage,
    isDark: Boolean,
    vault: ClientVaultManager,
    cardColor: Color,
    textColor: Color,
    onLanguageChange: (ClientLanguage) -> Unit,
    onDarkChange: (Boolean) -> Unit,
    onBackgroundPicked: (Uri) -> Unit,
    onResetBackground: () -> Unit,
    onAvatarPicked: (Uri) -> Unit,
    onSyncCompleted: (String) -> Unit,
    onClearVault: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cloudUrl by remember { mutableStateOf(vault.getCloudSyncUrl()) }

    val bgPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onBackgroundPicked(uri)
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onAvatarPicked(uri)
    }

    val jsonImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                vault.importAdminPayloadJson(text).onSuccess { count ->
                    onSyncCompleted(tr(lang, "باشقۇرغۇچىدىن $count دانە ھېسابات ماس قەدەملەندى!", "تمت مزامنة $count حساب من المسؤول!"))
                }
            }
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 84.dp)
    ) {
        // 1. تىل (ئۇيغۇرچە / العربية) ۋە كېچە-كۈندۈزلۈك تەڭشەك
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardColor,
                border = BorderStroke(1.dp, RoyalGold.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Language, contentDescription = null, tint = RoyalGold)
                        Text(
                            text = tr(lang, "تىل تەڭشىكى (ئۇيغۇرچە / العربية)", "إعدادات اللغة (ئۇيغۇرچە / العربية)"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ClientLanguage.entries.forEach { l ->
                            FilterChip(
                                selected = lang == l,
                                onClick = { onLanguageChange(l) },
                                label = { Text(l.title, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr(lang, "كېچىلىك قاراڭغۇ ھالەت", "الوضع الليلي الداكن"), color = textColor, fontWeight = FontWeight.Bold)
                        Switch(checked = isDark, onCheckedChange = onDarkChange)
                    }
                }
            }
        }

        // 2. تەگلىك رەسىم ۋە باش سۈرەت / لوگو تەڭشىكى
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardColor,
                border = BorderStroke(1.dp, ClientTurquoise.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = ClientTurquoise)
                        Text(
                            text = tr(lang, "تەگلىك ۋە باش سۈرەت (لوگو) تەڭشىكى", "إعدادات الخلفية والصورة الشخصية (الشعار)"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                    }

                    Button(
                        onClick = { bgPickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = ClientIndigo),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr(lang, "تېلېفون سۈرەت ئامبىرىدىن تەگلىك تاللاش", "اختيار صورة خلفية من المعرض"), color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onResetBackground,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr(lang, "ئەسلىدىكى تەگلىككە قايتۇرۇش", "استعادة الخلفية الأصلية"))
                    }

                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.25f))

                    Button(
                        onClick = { avatarPickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalGold),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr(lang, "سۈرەت ئامبىرىدىن باش سۈرەت / لوگو تاللاش", "اختيار الصورة الشخصية / الشعار من المعرض"), color = Color.Black, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // 3. باشقۇرغۇچى ئەپىگە باغلاش ۋە ئامبارنى تازىلاش
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardColor,
                border = BorderStroke(1.dp, ClientEmerald.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, tint = ClientEmerald)
                        Text(
                            text = tr(lang, "باشقۇرغۇچى ئەپى بىلەن باغلىنىش", "الربط والمزامنة مع تطبيق المسؤول"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                    }

                    OutlinedTextField(
                        value = cloudUrl,
                        onValueChange = {
                            cloudUrl = it
                            vault.setCloudSyncUrl(it)
                        },
                        label = { Text(tr(lang, "باشقۇرغۇچى بۇلۇت ئادرېسى (Cloud Sync URL)", "رابط سحابة المسؤول")) },
                        placeholder = { Text("https://your-project.firebaseio.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                val res = vault.syncWithAdminCloud(lang)
                                onSyncCompleted(res.getOrElse { tr(lang, "ماس قەدەملەشتە خاتالىق كۆرۈلدى", "حدث خطأ أثناء المزامنة") })
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClientEmerald),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr(lang, "باشقۇرغۇچى ئەپىدىن يېڭى نەشر ۋە مەلۇمات تارتىش", "جلب البيانات والتحديثات من تطبيق المسؤول"), color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { jsonImportLauncher.launch(arrayOf("application/json", "*/*")) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr(lang, "باشقۇرغۇچى ھۆججىتىنى ئەكىرىش (JSON)", "استيراد ملف المسؤول (JSON)"))
                    }

                    Button(
                        onClick = onClearVault,
                        colors = ButtonDefaults.buttonColors(containerColor = ClientRose),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr(lang, "ئەپ ئىچىدىكى بارلىق مەلۇماتلارنى تازىلاش", "مسح جميع بيانات التطبيق"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 3-بەت: ھېساباتلار (ئىسمى، ھېسابات ئىسمى، مەخپىي نومۇرى، ئېقىم مىقدارى، تىزىملاتقان ۋاقتى، پۇل تۆلىگەن ئايلار)
 */
@Composable
fun ClientAccountsScreen(
    lang: ClientLanguage,
    accounts: List<PersonAccountItem>,
    cardColor: Color,
    textColor: Color,
    onSearchClick: () -> Unit,
    onToggleMonth: (PersonAccountItem, Int) -> Unit,
    onEdit: (PersonAccountItem) -> Unit,
    onDelete: (PersonAccountItem) -> Unit,
    onCopy: (String, String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 84.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr(lang, "بارلىق ساقلانغان ھېساباتلار (${accounts.size})", "جميع الحسابات المحفوظة (${accounts.size})"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor
                )
                OutlinedButton(
                    onClick = onSearchClick,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RoyalGold)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr(lang, "ئىزدەش", "بحث"), color = RoyalGold)
                }
            }
        }

        if (accounts.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = tr(lang, "تېخى ھېسابات خاتىرىلەنمىگەن. ئاستىدىكى «يېڭى ھېسابات قوشۇش» كۇنۇپكىسىنى بېسىپ قوشۇڭ.", "لا توجد حسابات مسجلة بعد. اضغط على زر إضافة حساب جديد."),
                        modifier = Modifier.padding(28.dp),
                        textAlign = TextAlign.Center,
                        color = textColor
                    )
                }
            }
        } else {
            items(accounts, key = { it.id }) { item ->
                ClientPersonAccountCard(
                    lang = lang,
                    item = item,
                    cardColor = cardColor,
                    textColor = textColor,
                    onToggleMonth = { mIdx -> onToggleMonth(item, mIdx) },
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) },
                    onCopy = onCopy
                )
            }
        }
    }
}

@Composable
fun ClientPersonAccountCard(
    lang: ClientLanguage,
    item: PersonAccountItem,
    cardColor: Color,
    textColor: Color,
    onToggleMonth: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    var pwdVisible by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cardColor,
        border = BorderStroke(1.2.dp, RoyalGold.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(RoyalCrimson, RoyalGold))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
                    }
                    Column {
                        Text(
                            text = "${tr(lang, "ئىسمى", "الاسم")}: ${item.fullName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.DateRange, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(14.dp))
                            Text(
                                text = "${tr(lang, "تىزىملاتقان ۋاقتى", "تاريخ التسجيل")}: ${item.openDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                // ئېقىم مىقدارى بەلگىسى (كارتا ئۈستىدە ئېنىق كۆرۈنىدۇ)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ClientTurquoise.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, ClientTurquoise)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.DataUsage, contentDescription = null, tint = ClientTurquoise, modifier = Modifier.size(15.dp))
                        Text(
                            text = item.flowAmount.ifBlank { "100 GB" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = ClientTurquoise
                        )
                    }
                }
            }

            // ھېسابات ئىسمى، مەخپىي نومۇرى، ئېقىم مىقدارى ۋە ئادرېسى
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Badge, contentDescription = null, tint = ClientTurquoise, modifier = Modifier.size(18.dp))
                            Text("${tr(lang, "ھېسابات ئىسمى", "اسم الحساب")}: ${item.accountName}", fontWeight = FontWeight.Bold, color = textColor)
                        }
                        IconButton(onClick = { onCopy(tr(lang, "ھېسابات ئىسمى", "اسم الحساب"), item.accountName) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = ClientTurquoise, modifier = Modifier.size(16.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(18.dp))
                            Text(
                                text = "${tr(lang, "مەخپىي نومۇرى", "كلمة المرور")}: ${if (pwdVisible) item.password else "••••••••"}",
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                        Row {
                            IconButton(onClick = { pwdVisible = !pwdVisible }, modifier = Modifier.size(28.dp)) {
                                Icon(if (pwdVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onCopy(tr(lang, "مەخپىي نومۇر", "كلمة المرور"), item.password) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.DataUsage, contentDescription = null, tint = ClientEmerald, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${tr(lang, "ئېقىم مىقدارى", "حصة البيانات")}: ${item.flowAmount.ifBlank { "100 GB" }}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = ClientEmerald
                        )
                    }

                    if (item.streetName.isNotBlank() || item.houseNumber.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ClientRose, modifier = Modifier.size(16.dp))
                            Text("${tr(lang, "ئادرېس", "العنوان")}: ${item.streetName} ${item.houseNumber}", style = MaterialTheme.typography.bodySmall, color = textColor)
                        }
                    }
                }
            }

            // پۇل تۆلىگەن ئايلار (12 ئاي)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tr(lang, "پۇل تۆلىگەن ئايلار", "الأشهر المدفوعة")} (${item.paidCount()} / 12):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = ClientTurquoise, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = ClientRose, modifier = Modifier.size(18.dp))
                    }
                }
            }

            val rows = (0 until 12).chunked(4)
            rows.forEach { rIndices ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rIndices.forEach { mIdx ->
                        val paid = item.paidMonths.getOrElse(mIdx) { false }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (paid) ClientEmerald.copy(alpha = 0.22f) else ClientRose.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (paid) ClientEmerald else ClientRose.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onToggleMonth(mIdx) }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = tr(lang, "${mIdx + 1}-ئاي", "شهر ${mIdx + 1}"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Text(
                                    text = if (paid) tr(lang, "تۆلەندى", "مدفوع") else tr(lang, "تۆلەنمىدى", "غير مدفوع"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (paid) ClientEmerald else ClientRose,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4-بەت: ئەسلەتمىلەر
 */
@Composable
fun ClientRemindersAndFeedbackScreen(
    lang: ClientLanguage,
    currentUser: RegisteredAuthUser,
    reminders: List<AdminReminderNote>,
    feedbacks: List<ClientFeedbackNote>,
    cardColor: Color,
    textColor: Color,
    onSendFeedback: (String) -> Unit
) {
    var feedbackText by remember { mutableStateOf("") }

    val myReminders = remember(reminders, currentUser.identifier) {
        reminders.filter {
            it.targetUsername == "ALL" || it.targetUsername.equals(currentUser.identifier, ignoreCase = true)
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 84.dp)
    ) {
        item {
            Text(
                text = tr(lang, "باشقۇرغۇچى ئەسلەتمىلىرى (${myReminders.size}):", "تذكيرات المسؤول (${myReminders.size}):"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
        }

        items(myReminders, key = { it.id }) { rem ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = cardColor,
                border = BorderStroke(1.2.dp, RoyalGold.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, tint = RoyalGold)
                        Text(rem.title, fontWeight = FontWeight.ExtraBold, color = textColor)
                    }
                    Text(rem.content, style = MaterialTheme.typography.bodyMedium, color = textColor)
                    Text(rem.timestamp, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.65f))
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardColor,
                border = BorderStroke(1.dp, ClientTurquoise.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(lang, "باشقۇرغۇچىغا ئىنكاس يوللاش:", "إرسال رسالة أو ملاحظة للمسؤول:"),
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = { Text(tr(lang, "ئىنكاس مەزمۇنىنى يېزىڭ...", "اكتب رسالتك هنا...")) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (feedbackText.isNotBlank()) {
                                onSendFeedback(feedbackText)
                                feedbackText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalGold),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr(lang, "باشقۇرغۇچىغا يوللاش", "إرسال للمسؤول"), color = Color.Black, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        items(feedbacks, key = { it.id }) { fb ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardColor,
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${tr(lang, "مېنىڭ ئىنكاسىم", "رسالتي")}: ${fb.message}", color = textColor)
                    if (fb.adminReply.isNotBlank()) {
                        Text(
                            text = "${tr(lang, "باشقۇرغۇچى جاۋابى", "رد المسؤول")}: ${fb.adminReply}",
                            color = ClientEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClientDedicatedOverlayPage(
    lang: ClientLanguage,
    overlayType: HomeOverlayView,
    allAccounts: List<PersonAccountItem>,
    searchQuery: String,
    cardColor: Color,
    textColor: Color,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onToggleMonth: (PersonAccountItem, Int) -> Unit,
    onEdit: (PersonAccountItem) -> Unit,
    onDelete: (PersonAccountItem) -> Unit,
    onCopy: (String, String) -> Unit
) {
    val cleanQ = searchQuery.trim().lowercase()
    val filteredList = remember(allAccounts, overlayType, cleanQ) {
        val base = when (overlayType) {
            HomeOverlayView.PAID_ACCOUNTS -> allAccounts.filter { it.paidCount() == 12 }
            HomeOverlayView.DEBT_ACCOUNTS -> allAccounts.filter { it.paidCount() < 12 }
            else -> allAccounts
        }
        if (cleanQ.isEmpty()) base
        else base.filter { item ->
            item.fullName.lowercase().contains(cleanQ) ||
                item.accountName.lowercase().contains(cleanQ) ||
                item.streetName.lowercase().contains(cleanQ) ||
                item.houseNumber.lowercase().contains(cleanQ) ||
                item.flowAmount.lowercase().contains(cleanQ) ||
                item.notes.lowercase().contains(cleanQ)
        }
    }

    val pageTitle = when (overlayType) {
        HomeOverlayView.ALL_ACCOUNTS -> tr(lang, "جەمئىي ھېساباتلار (${filteredList.size})", "إجمالي الحسابات (${filteredList.size})")
        HomeOverlayView.PAID_ACCOUNTS -> tr(lang, "ھەق تاپشۇرغانلار (${filteredList.size})", "الدافعون بالكامل (${filteredList.size})")
        HomeOverlayView.DEBT_ACCOUNTS -> tr(lang, "قەرزگە ئاچقۇزغانلار (${filteredList.size})", "الحسابات بالدين (${filteredList.size})")
        else -> tr(lang, "ئىزدەش نەتىجىسى (${filteredList.size})", "نتائج البحث (${filteredList.size})")
    }

    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = cardColor,
                    border = BorderStroke(1.2.dp, RoyalGold)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalGold),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(tr(lang, "قايتىش", "رجوع"), color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Text(pageTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = textColor)
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text(tr(lang, "ئىسىم، كوچا، ئادرېس ياكى ئېقىم مىقدارىنى يېزىڭ...", "ابحث بالاسم أو الشارع أو الحصة...")) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = RoyalGold) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = null)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (filteredList.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.SearchOff, contentDescription = null, tint = ClientRose, modifier = Modifier.size(44.dp))
                            Text(tr(lang, "ماس كېلىدىغان ھېسابات تېپىلمىدى", "لم يتم العثور على حساب مطابق"), fontWeight = FontWeight.Bold, color = textColor)
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { item ->
                    ClientPersonAccountCard(
                        lang = lang,
                        item = item,
                        cardColor = cardColor,
                        textColor = textColor,
                        onToggleMonth = { mIdx -> onToggleMonth(item, mIdx) },
                        onEdit = { onEdit(item) },
                        onDelete = { onDelete(item) },
                        onCopy = onCopy
                    )
                }
            }
        }
    }
}

/**
 * يېڭى ھېسابات قۇرۇش ۋە تەھرىرلەش دىئالوگى:
 * ئېقىم مىقدارى (GB) كىرگۈزۈش رامكىسى ۋە تېز تاللاش كۇنۇپكىلىرى تولۇق قوشۇلدى!
 */
@Composable
fun PersonAccountFormDialog(
    lang: ClientLanguage,
    ownerIdentifier: String,
    initial: PersonAccountItem?,
    onDismiss: () -> Unit,
    onSave: (PersonAccountItem) -> Unit
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var fullName by remember { mutableStateOf(initial?.fullName.orEmpty()) }
    var accountName by remember { mutableStateOf(initial?.accountName.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var flowAmount by remember { mutableStateOf(initial?.flowAmount ?: "100 GB") }
    var streetName by remember { mutableStateOf(initial?.streetName.orEmpty()) }
    var houseNumber by remember { mutableStateOf(initial?.houseNumber.orEmpty()) }
    var openDate by remember { mutableStateOf(initial?.openDate ?: today) }

    val quickFlowOptions = listOf(
        "50 GB",
        "100 GB",
        "200 GB",
        "500 GB",
        tr(lang, "چەكسىز", "غير محدود")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) tr(lang, "يېڭى ھېسابات قوشۇش", "إضافة حساب جديد")
                else tr(lang, "ھېساباتنى تەھرىرلەش", "تعديل الحساب"),
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(tr(lang, "كىشىنىڭ ئىسمى *", "اسم الشخص *")) },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = RoyalGold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text(tr(lang, "ھېسابات ئىسمى *", "اسم الحساب *")) },
                    leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = ClientTurquoise) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(tr(lang, "مەخپىي نومۇرى *", "كلمة المرور *")) },
                    leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null, tint = ClientEmerald) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ئېقىم مىقدارى (GB) كىرگۈزۈش رامكىسى + تېز تاللاش بەلگىلىرى
                OutlinedTextField(
                    value = flowAmount,
                    onValueChange = { flowAmount = it },
                    label = { Text(tr(lang, "ئېقىم مىقدارى (GB) *", "حصة البيانات (GB) *")) },
                    placeholder = { Text("100 GB") },
                    leadingIcon = { Icon(Icons.Filled.DataUsage, contentDescription = null, tint = RoyalGold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickFlowOptions.forEach { preset ->
                        FilterChip(
                            selected = flowAmount == preset,
                            onClick = { flowAmount = preset },
                            label = { Text(preset) }
                        )
                    }
                }

                OutlinedTextField(
                    value = streetName,
                    onValueChange = { streetName = it },
                    label = { Text(tr(lang, "كوچا ئىسمى / ئادرېس", "اسم الشارع / العنوان")) },
                    leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ClientRose) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = houseNumber,
                    onValueChange = { houseNumber = it },
                    label = { Text(tr(lang, "ئۆي نومۇرى", "رقم المنزل")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openDate,
                    onValueChange = { openDate = it },
                    label = { Text(tr(lang, "تىزىملاتقان ۋاقتى", "تاريخ التسجيل")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = fullName.trim().ifEmpty { accountName.trim() }
                    if (cleanName.isNotEmpty()) {
                        onSave(
                            PersonAccountItem(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                ownerIdentifier = ownerIdentifier,
                                fullName = cleanName,
                                accountName = accountName.trim().ifEmpty { cleanName },
                                password = password.trim().ifEmpty { "123456" },
                                streetName = streetName.trim(),
                                houseNumber = houseNumber.trim(),
                                flowAmount = flowAmount.trim().ifEmpty { "100 GB" },
                                openDate = openDate.trim(),
                                notes = initial?.notes.orEmpty(),
                                paidMonths = initial?.paidMonths ?: List(12) { false }
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClientEmerald)
            ) {
                Text(tr(lang, "ساقلاش", "حفظ"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(lang, "بىكار قىلىش", "إلغاء"))
            }
        }
    )
}

/**
 * دەل ۋە مۇقىم ئىشلەيدىغان FAST.com تور تېزلىكى ئۆلچىگۈچ (ئۆزى خالىغانچە جۆيلىمەيدۇ، ئەمەلىي چۈشكەن بايت ۋە Ping نى ئۆلچەيدۇ)
 */
suspend fun runCalibratedFastSpeedTest(
    onProgressMbps: (Float) -> Unit
): FastSpeedTestResult = withContext(Dispatchers.IO) {
    val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    try {
        // 1. Ping (كېچىكىش ۋاقتى) نى ئۆلچەش
        val pingStart = SystemClock.elapsedRealtime()
        val pingConn = (URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            useCaches = false
        }
        pingConn.responseCode
        pingConn.disconnect()
        val pingMs = (SystemClock.elapsedRealtime() - pingStart).toInt().coerceIn(5, 999)

        // 2. ھەقىقىي ئېقىم چۈشۈرۈپ سۈرئەت ئۆلچەش
        val testUrl = URL("https://speed.cloudflare.com/__down?bytes=4000000")
        val conn = (testUrl.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }

        val buffer = ByteArray(16384)
        var totalBytesRead = 0L
        val downloadStartMs = SystemClock.elapsedRealtime()
        var lastEmitMs = downloadStartMs

        conn.inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                totalBytesRead += read
                val nowMs = SystemClock.elapsedRealtime()
                val elapsedMs = (nowMs - downloadStartMs).coerceAtLeast(100L)

                if (nowMs - lastEmitMs >= 200L) {
                    val currentBps = (totalBytesRead * 8f * 1000f) / elapsedMs
                    val currentMbps = (currentBps / (1024f * 1024f)).coerceAtLeast(0.1f)
                    withContext(Dispatchers.Main) {
                        onProgressMbps(currentMbps)
                    }
                    lastEmitMs = nowMs
                }
                if (elapsedMs >= 4000L) break
            }
        }
        conn.disconnect()

        val totalElapsedSec = ((SystemClock.elapsedRealtime() - downloadStartMs).coerceAtLeast(250L)) / 1000f
        val finalMBps = (totalBytesRead / (1024f * 1024f)) / totalElapsedSec
        val finalMbps = (finalMBps * 8f).coerceAtLeast(0.1f)

        withContext(Dispatchers.Main) {
            onProgressMbps(finalMbps)
        }

        FastSpeedTestResult(
            downloadMbps = finalMbps,
            downloadMBps = finalMBps,
            pingMs = pingMs,
            testedAt = stamp
        )
    } catch (_: Exception) {
        FastSpeedTestResult(
            downloadMbps = 0f,
            downloadMBps = 0f,
            pingMs = 0,
            testedAt = stamp
        )
    }
}
