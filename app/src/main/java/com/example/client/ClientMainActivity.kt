package com.example.client

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.TrafficStats
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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

// باشقۇرغۇچى ئەپى بىلەن ئورتاق شىفىرلىق ئاچقۇچ ۋە باشقۇرغۇچى ئېلخەت ئادرېسى
const val ADMIN_MASTER_EMAIL = "uyghur162142@gmail.com"
const val SHARED_CRYPTO_SALT = "HESABAT_SYNC_2026_SECRET"

val ClientIndigo = Color(0xFF6366F1)
val ClientTurquoise = Color(0xFF06B6D4)
val ClientEmerald = Color(0xFF10B981)
val ClientRose = Color(0xFFF43F5E)
val ClientAmber = Color(0xFFF59E0B)
val ClientDarkBg = Color(0xFF070D1D)
val ClientCardBg = Color(0xDD111B33)

class ClientMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ئىز قوغلاش ۋە ئېكران رەسىم تارتىشتىن قوغداش
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.decorView.filterTouchesWhenObscured = true

        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ClientIndigo,
                    secondary = ClientTurquoise,
                    background = ClientDarkBg,
                    surface = ClientCardBg,
                    onSurface = Color.White,
                    onBackground = Color.White
                )
            ) {
                ClientRootApp()
            }
        }
    }
}

enum class AppLanguage(val code: String, val displayName: String, val isRtl: Boolean) {
    UG("ug", "ئۇيغۇرچە", true),
    EN("en", "English", false)
}

enum class ClientBottomTab(val ugTitle: String, val enTitle: String, val icon: ImageVector) {
    HOME("باش بەت", "Home", Icons.Filled.Home),
    SETTINGS("تەڭشەكلەر", "Settings", Icons.Filled.Settings),
    ACCOUNTS("ھېساباتلار", "Accounts", Icons.Filled.Group),
    REMINDERS("ئەسلەتمىلەر", "Reminders", Icons.Filled.Notifications)
}

data class RegisteredAuthUser(
    val username: String,
    val fullName: String,
    val passwordHash: String,
    val address: String,
    val email: String,
    val isVerified: Boolean,
    val isVip: Boolean,
    val vipExpireMillis: Long,
    val licenseCode: String
) {
    fun canDirectLoginWithoutCode(): Boolean {
        val vipValid = isVip && (vipExpireMillis == 0L || vipExpireMillis > System.currentTimeMillis())
        return isVerified || vipValid
    }
}

data class PersonAccountItem(
    val id: String,
    val ownerUsername: String,
    val fullName: String,
    val accountName: String,
    val password: String,
    val streetName: String,
    val houseNumber: String,
    val flowAmount: String,
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

/**
 * خېرىدارلار ئەپىنىڭ مەڭگۈلۈك ساقلاش ئامبىرى ۋە باشقۇرغۇچى ئەپى بىلەن باغلىنىش ماتورى
 */
class ClientVaultManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("hesabat_client_permanent_vault_v2", Context.MODE_PRIVATE)
    private val dbFile: File
        get() = File(context.filesDir, "hesabat_client_records.json")
    private val bgFile: File
        get() = File(context.filesDir, "custom_background_img.jpg")
    private val avatarFile: File
        get() = File(context.filesDir, "custom_avatar_img.jpg")

    /**
     * باشقۇرغۇچى ئەپىدە ياسالغان كودنى دەلىللەش فۇنكسىيەسى.
     * باشقۇرغۇچى ئەپى بىلەن ئوپئوخشاش SHARED_CRYPTO_SALT ئىشلىتىلگەن بولغاچقا،
     * باشقۇرغۇچى چىقىرىپ بەرگەن كود 100% توغرا تونۇلىدۇ.
     */
    fun expectedStandardCode(username: String): String {
        val clean = username.trim().lowercase()
        if (clean.isEmpty()) return ""
        val raw = sha256("$SHARED_CRYPTO_SALT::$clean").uppercase()
        return "HSB-${raw.substring(0, 4)}-${raw.substring(4, 8)}"
    }

    fun expectedVipCode(username: String): String {
        val clean = username.trim().lowercase()
        if (clean.isEmpty()) return ""
        val raw = sha256("$SHARED_CRYPTO_SALT::$clean").uppercase()
        return "VIP-${raw.substring(0, 4)}-${raw.substring(4, 8)}"
    }

    fun verifyCodeFromAdmin(username: String, inputCode: String): Pair<Boolean, Boolean> {
        val cleanInput = inputCode.trim().uppercase().replace(" ", "")
        if (cleanInput.isEmpty()) return false to false

        val std = expectedStandardCode(username)
        val vip = expectedVipCode(username)

        val compactInput = cleanInput.replace("-", "")
        val isStdMatch = cleanInput == std || compactInput == std.replace("-", "")
        val isVipMatch = cleanInput == vip || compactInput == vip.replace("-", "")

        return (isStdMatch || isVipMatch) to isVipMatch
    }

    fun getLanguage(): AppLanguage {
        val code = prefs.getString("app_lang", "ug") ?: "ug"
        return if (code == "en") AppLanguage.EN else AppLanguage.UG
    }

    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putString("app_lang", lang.code).commit()
    }

    fun getCloudSyncUrl(): String = prefs.getString("cloud_sync_url", "") ?: ""

    fun setCloudSyncUrl(url: String) {
        prefs.edit().putString("cloud_sync_url", url.trim()).commit()
    }

    fun saveCustomBackgroundFromUri(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                bgFile.outputStream().use { output ->
                    input.copyTo(output)
                }
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
            if (bgFile.exists()) {
                BitmapFactory.decodeFile(bgFile.absolutePath)?.asImageBitmap()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun saveCustomAvatarFromUri(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                avatarFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadCustomAvatarBitmap(): ImageBitmap? {
        return try {
            if (avatarFile.exists()) {
                BitmapFactory.decodeFile(avatarFile.absolutePath)?.asImageBitmap()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun loadRegisteredUsers(): List<RegisteredAuthUser> {
        val raw = prefs.getString("auth_users_json", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                RegisteredAuthUser(
                    username = o.optString("username"),
                    fullName = o.optString("fullName"),
                    passwordHash = o.optString("passwordHash"),
                    address = o.optString("address"),
                    email = o.optString("email"),
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
                    put("username", u.username)
                    put("fullName", u.fullName)
                    put("passwordHash", u.passwordHash)
                    put("address", u.address)
                    put("email", u.email)
                    put("isVerified", u.isVerified)
                    put("isVip", u.isVip)
                    put("vipExpireMillis", u.vipExpireMillis)
                    put("licenseCode", u.licenseCode)
                }
            )
        }
        prefs.edit().putString("auth_users_json", arr.toString()).commit()
    }

    fun getActiveUser(): RegisteredAuthUser? {
        val activeName = prefs.getString("active_username", null) ?: return null
        val user = loadRegisteredUsers().firstOrNull { it.username.equals(activeName, ignoreCase = true) } ?: return null
        return if (user.canDirectLoginWithoutCode()) user else null
    }

    fun setActiveUsername(username: String?) {
        if (username.isNullOrBlank()) {
            prefs.edit().remove("active_username").commit()
        } else {
            prefs.edit().putString("active_username", username.trim()).commit()
        }
    }

    fun createPendingUserRequest(
        username: String,
        fullName: String,
        password: String,
        address: String,
        email: String
    ): RegisteredAuthUser {
        val cleanUser = username.trim()
        val users = loadRegisteredUsers().toMutableList()
        val existingIdx = users.indexOfFirst { it.username.equals(cleanUser, ignoreCase = true) }
        val newObj = RegisteredAuthUser(
            username = cleanUser,
            fullName = fullName.trim().ifBlank { cleanUser },
            passwordHash = sha256(password.trim()),
            address = address.trim(),
            email = email.trim().ifBlank { "$cleanUser@gmail.com" },
            isVerified = false,
            isVip = false,
            vipExpireMillis = 0L,
            licenseCode = ""
        )
        if (existingIdx >= 0) {
            users[existingIdx] = newObj
        } else {
            users.add(0, newObj)
        }
        saveRegisteredUsers(users)
        return newObj
    }

    fun verifyUserWithAdminCode(username: String, enteredCode: String): Result<RegisteredAuthUser> {
        val cleanUser = username.trim()
        val (isValid, isVipCode) = verifyCodeFromAdmin(cleanUser, enteredCode)
        if (!isValid) {
            return Result.failure(
                IllegalArgumentException("دەلىللەش كودى خاتا! باشقۇرغۇچى «$cleanUser» ئۈچۈن ئەۋەتكەن توغرا كودنى كىرگۈزۈڭ.")
            )
        }
        val users = loadRegisteredUsers().toMutableList()
        val idx = users.indexOfFirst { it.username.equals(cleanUser, ignoreCase = true) }
        val updatedUser = if (idx >= 0) {
            users[idx].copy(
                isVerified = true,
                isVip = isVipCode || users[idx].isVip,
                vipExpireMillis = if (isVipCode) System.currentTimeMillis() + 30L * 24L * 3600L * 1000L else users[idx].vipExpireMillis,
                licenseCode = enteredCode.trim().uppercase()
            ).also { users[idx] = it }
        } else {
            RegisteredAuthUser(
                username = cleanUser,
                fullName = cleanUser,
                passwordHash = sha256("123456"),
                address = "",
                email = "$cleanUser@gmail.com",
                isVerified = true,
                isVip = isVipCode,
                vipExpireMillis = if (isVipCode) System.currentTimeMillis() + 30L * 24L * 3600L * 1000L else 0L,
                licenseCode = enteredCode.trim().uppercase()
            ).also { users.add(0, it) }
        }
        saveRegisteredUsers(users)
        setActiveUsername(updatedUser.username)
        return Result.success(updatedUser)
    }

    fun loginExistingUser(username: String, password: String): Result<RegisteredAuthUser> {
        val cleanUser = username.trim()
        val matched = loadRegisteredUsers().firstOrNull { it.username.equals(cleanUser, ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("بۇ ھېسابات تېپىلمىدى، ئالدى بىلەن تىزىملىتىپ باشقۇرغۇچىدىن كود ئېلىڭ"))

        if (matched.passwordHash != sha256(password.trim())) {
            return Result.failure(IllegalArgumentException("مەخپىي نومۇر خاتا!"))
        }

        if (!matched.canDirectLoginWithoutCode()) {
            return Result.failure(IllegalStateException("NEED_CODE"))
        }

        setActiveUsername(matched.username)
        return Result.success(matched)
    }

    fun loadPersonAccounts(ownerUsername: String): List<PersonAccountItem> {
        val all = loadAllPersonAccountsInternal()
        val filtered = all.filter {
            it.ownerUsername.isBlank() ||
                it.ownerUsername.equals(ownerUsername, ignoreCase = true) ||
                it.ownerUsername == "ALL"
        }
        if (filtered.isEmpty() && all.isEmpty()) {
            val sample = listOf(
                PersonAccountItem(
                    id = UUID.randomUUID().toString(),
                    ownerUsername = ownerUsername,
                    fullName = "ئالىمجان مەمەت",
                    accountName = "alim01",
                    password = "123456",
                    streetName = "ئىتتىپاق كوچىسى",
                    houseNumber = "12-ئۆي",
                    flowAmount = "100 GB",
                    openDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                    notes = "نورمال ھېسابات",
                    paidMonths = List(12) { it < 4 }
                )
            )
            saveAllPersonAccountsInternal(sample)
            return sample
        }
        return filtered
    }

    fun savePersonAccountsForUser(ownerUsername: String, userAccounts: List<PersonAccountItem>) {
        val others = loadAllPersonAccountsInternal().filterNot {
            it.ownerUsername.isBlank() || it.ownerUsername.equals(ownerUsername, ignoreCase = true)
        }
        saveAllPersonAccountsInternal(userAccounts + others)
    }

    private fun loadAllPersonAccountsInternal(): List<PersonAccountItem> {
        val text = runCatching {
            if (dbFile.exists()) dbFile.readText(Charsets.UTF_8) else ""
        }.getOrDefault("").ifBlank {
            prefs.getString("person_accounts_json", "") ?: ""
        }
        if (text.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(text)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val mArr = o.optJSONArray("paidMonths")
                val months = List(12) { idx -> mArr?.optBoolean(idx, false) ?: false }
                PersonAccountItem(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    ownerUsername = o.optString("ownerUsername"),
                    fullName = o.optString("fullName"),
                    accountName = o.optString("accountName", o.optString("username")),
                    password = o.optString("password"),
                    streetName = o.optString("streetName"),
                    houseNumber = o.optString("houseNumber"),
                    flowAmount = o.optString("flowAmount", "100 GB"),
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
                    put("ownerUsername", item.ownerUsername)
                    put("fullName", item.fullName)
                    put("accountName", item.accountName)
                    put("password", item.password)
                    put("streetName", item.streetName)
                    put("houseNumber", item.houseNumber)
                    put("flowAmount", item.flowAmount)
                    put("openDate", item.openDate)
                    put("notes", item.notes)
                    put("paidMonths", JSONArray(item.paidMonths))
                }
            )
        }
        val str = arr.toString()
        prefs.edit().putString("person_accounts_json", str).commit()
        runCatching { dbFile.writeText(str, Charsets.UTF_8) }
    }

    fun loadReminders(): List<AdminReminderNote> {
        val raw = prefs.getString("reminders_json", "") ?: ""
        if (raw.isBlank()) {
            return listOf(
                AdminReminderNote(
                    id = "rem_default_1",
                    title = "باشقۇرغۇچىدىن ئەسكەرتىش",
                    content = "ھۆرمەتلىك خېرىدار، ئايلىق تور ھەققىنى ۋاقتىدا تاپشۇرۇشنى ئۇنتۇپ قالماڭ. مەسىلە بولسا ئىنكاس يېزىڭ.",
                    targetUsername = "ALL",
                    timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                )
            )
        }
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
        val raw = prefs.getString("feedback_json", "[]") ?: "[]"
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
        prefs.edit().putString("feedback_json", arr.toString()).commit()
    }

    /**
     * باشقۇرغۇچى ئەپى چىقارغان JSON ياكى بۇلۇت مەلۇماتى بىلەن ماس قەدەملەش
     */
    suspend fun pullFromAdminCloud(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = getCloudSyncUrl().trim()
            if (endpoint.isEmpty()) {
                return@runCatching "بۇلۇت ئادرېسى تەڭشەلمىگەن، يەرلىك ساقلاش ئامبىرى ئىشلىتىلىۋاتىدۇ"
            }
            val targetUrl = if (endpoint.endsWith(".json")) endpoint else "$endpoint/hesabat_sync.json"
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            importAdminBackupJson(text)
            "باشقۇرغۇچى مەركىزى بىلەن مۇۋەپپەقىيەتلىك ماس قەدەملەندى!"
        }
    }

    fun importAdminBackupJson(jsonText: String): Result<Int> {
        return runCatching {
            val root = JSONObject(jsonText)
            root.optJSONArray("reminders")?.let {
                prefs.edit().putString("reminders_json", it.toString()).commit()
            }
            root.optJSONArray("feedback")?.let {
                prefs.edit().putString("feedback_json", it.toString()).commit()
            }
            val accArr = root.optJSONArray("accounts") ?: return@runCatching 0
            val importedItems = mutableListOf<PersonAccountItem>()
            val authUsers = loadRegisteredUsers().toMutableList()

            for (i in 0 until accArr.length()) {
                val o = accArr.optJSONObject(i) ?: continue
                val uname = o.optString("username", o.optString("accountName"))
                val isVip = o.optBoolean("isVip", false)
                val vipExp = o.optLong("vipExpireEpochMillis", 0L)
                val pwd = o.optString("password", "123456")
                val mArr = o.optJSONArray("paidMonths")
                val months = List(12) { idx -> mArr?.optBoolean(idx, false) ?: false }

                importedItems.add(
                    PersonAccountItem(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        ownerUsername = uname,
                        fullName = o.optString("fullName"),
                        accountName = uname,
                        password = pwd,
                        streetName = o.optString("streetName"),
                        houseNumber = o.optString("houseNumber"),
                        flowAmount = o.optString("flowAmount", "100 GB"),
                        openDate = o.optString("openDate"),
                        notes = o.optString("notes"),
                        paidMonths = months
                    )
                )

                val existingIdx = authUsers.indexOfFirst { it.username.equals(uname, ignoreCase = true) }
                if (existingIdx >= 0) {
                    authUsers[existingIdx] = authUsers[existingIdx].copy(
                        isVip = isVip,
                        vipExpireMillis = vipExp,
                        isVerified = true
                    )
                } else if (uname.isNotBlank()) {
                    authUsers.add(
                        RegisteredAuthUser(
                            username = uname,
                            fullName = o.optString("fullName", uname),
                            passwordHash = sha256(pwd),
                            address = "${o.optString("streetName")} ${o.optString("houseNumber")}".trim(),
                            email = o.optString("email", "$uname@gmail.com"),
                            isVerified = true,
                            isVip = isVip,
                            vipExpireMillis = vipExp,
                            licenseCode = o.optString("licenseCode")
                        )
                    )
                }
            }
            saveRegisteredUsers(authUsers)
            if (importedItems.isNotEmpty()) {
                saveAllPersonAccountsInternal(importedItems)
            }
            importedItems.size
        }
    }

    fun clearAllClientData() {
        prefs.edit()
            .remove("person_accounts_json")
            .remove("reminders_json")
            .remove("feedback_json")
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
fun ClientRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val vault = remember { ClientVaultManager(context.applicationContext) }

    var language by remember { mutableStateOf(vault.getLanguage()) }
    var activeUser by remember { mutableStateOf(vault.getActiveUser()) }
    var registeredUsers by remember { mutableStateOf(vault.loadRegisteredUsers()) }

    var customBgBitmap by remember { mutableStateOf(vault.loadCustomBackgroundBitmap()) }
    var customAvatarBitmap by remember { mutableStateOf(vault.loadCustomAvatarBitmap()) }

    var selectedTab by remember { mutableStateOf(ClientBottomTab.HOME) }
    var homeOverlay by remember { mutableStateOf(HomeOverlayView.NONE) }
    var searchQuery by remember { mutableStateOf("") }

    var accounts by remember(activeUser?.username) {
        mutableStateOf(vault.loadPersonAccounts(activeUser?.username ?: ""))
    }
    var reminders by remember { mutableStateOf(vault.loadReminders()) }
    var feedbacks by remember { mutableStateOf(vault.loadFeedbacks()) }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingPerson by remember { mutableStateOf<PersonAccountItem?>(null) }

    // ھەقىقىي ۋاقىتلىق سىمسىز تور ئېقىم مىقدارى ۋە Fast.com تېزلىك ئۆلچىگۈچ
    var liveDownloadKbps by remember { mutableStateOf(0f) }
    var liveUploadKbps by remember { mutableStateOf(0f) }
    var sessionUsedMb by remember { mutableStateOf(0f) }
    var isRunningSpeedTest by remember { mutableStateOf(false) }
    var speedTestResultMbps by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Unit) {
        val startTotal = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
        var prevRx = TrafficStats.getTotalRxBytes()
        var prevTx = TrafficStats.getTotalTxBytes()
        while (true) {
            delay(1000L)
            val nowRx = TrafficStats.getTotalRxBytes()
            val nowTx = TrafficStats.getTotalTxBytes()
            val dRx = (nowRx - prevRx).coerceAtLeast(0L)
            val dTx = (nowTx - prevTx).coerceAtLeast(0L)
            prevRx = nowRx
            prevTx = nowTx
            liveDownloadKbps = dRx / 1024f
            liveUploadKbps = dTx / 1024f
            val totalUsed = ((nowRx + nowTx) - startTotal).coerceAtLeast(0L)
            sessionUsedMb = totalUsed / (1024f * 1024f)
        }
    }

    val notifyAdminRegistration: (String, String, String, String) -> Unit = { uname, fullName, address, email ->
        runCatching {
            val subject = "Hesabat يېڭى ھېسابات ئېچىش ئىلتىماسى: $uname"
            val body = buildString {
                appendLine("ئەسسالامۇئەلەيكۇم باشقۇرغۇچى،")
                appendLine("تۆۋەندىكى ئورۇندا بىر كىشى يېڭى ھېسابات تىزىملاپ كىرمەكچى:")
                appendLine("• ھېسابات ئىسمى: $uname")
                appendLine("• تولۇق ئىسمى: $fullName")
                appendLine("• ئادرېسى / كوچىسى: ${address.ifBlank { "يېزىلمىغان" }}")
                appendLine("• ئېلخەت: ${email.ifBlank { "$uname@gmail.com" }}")
                appendLine("\nباشقۇرغۇچى ئەپىدىن «$uname» ئۈچۈن دەلىللەش كودى ئەۋەتىپ بېرىڭ.")
            }
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$ADMIN_MASTER_EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(Intent.createChooser(intent, "باشقۇرغۇچىغا ئۇچۇر يوللاش"))
        }
    }

    val saveUserAccounts: (List<PersonAccountItem>) -> Unit = { updated ->
        val owner = activeUser?.username ?: ""
        accounts = updated
        vault.savePersonAccountsForUser(owner, updated)
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // تەگلىك رەسىم (سۈرەت ئامبىرىدىن تاللانغان رەسىم ياكى زامانىۋى گىرادىيېنت)
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
                        .background(Color(0x99060C1A))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF070E22), Color(0xFF0F1E3D), Color(0xFF060B18))
                            )
                        )
                )
            }

            if (activeUser == null) {
                ClientAuthRegistrationScreen(
                    registeredUsers = registeredUsers,
                    onRequestAdminApproval = { uname, fullName, pwd, address, email ->
                        vault.createPendingUserRequest(uname, fullName, pwd, address, email)
                        registeredUsers = vault.loadRegisteredUsers()
                        notifyAdminRegistration(uname, fullName, address, email)
                    },
                    onVerifyCodeAndEnter = { uname, code ->
                        val res = vault.verifyUserWithAdminCode(uname, code)
                        res.onSuccess { verifiedUser ->
                            registeredUsers = vault.loadRegisteredUsers()
                            activeUser = verifiedUser
                            accounts = vault.loadPersonAccounts(verifiedUser.username)
                        }
                        res.map { Unit }
                    },
                    onDirectLogin = { uname, pwd ->
                        val res = vault.loginExistingUser(uname, pwd)
                        res.onSuccess { user ->
                            activeUser = user
                            accounts = vault.loadPersonAccounts(user.username)
                        }
                        res.map { Unit }
                    }
                )
            } else {
                val currentUser = activeUser!!

                if (homeOverlay != HomeOverlayView.NONE) {
                    BackHandler { homeOverlay = HomeOverlayView.NONE }
                    ClientDedicatedOverlayPage(
                        overlayType = homeOverlay,
                        allAccounts = accounts,
                        searchQuery = searchQuery,
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
                            scope.launch { snackbarHostState.showSnackbar("«${item.fullName}» ئۆچۈرۈلدى") }
                        },
                        onCopy = { label, text ->
                            clipboard.setText(AnnotatedString(text))
                            scope.launch { snackbarHostState.showSnackbar("$label كۆچۈرۈلدى") }
                        }
                    )
                } else {
                    Scaffold(
                        containerColor = Color.Transparent,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            NavigationBar(
                                containerColor = Color(0xEE0B1326),
                                tonalElevation = 10.dp
                            ) {
                                ClientBottomTab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = selectedTab == tab,
                                        onClick = { selectedTab = tab },
                                        icon = { Icon(tab.icon, contentDescription = null) },
                                        label = {
                                            Text(
                                                text = if (language == AppLanguage.UG) tab.ugTitle else tab.enTitle,
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
                                    containerColor = ClientIndigo,
                                    contentColor = Color.White,
                                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                                    text = {
                                        Text(
                                            text = if (language == AppLanguage.UG) "يېڭى ھېسابات قوشۇش" else "Add Account",
                                            fontWeight = FontWeight.Bold
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
                            // ئۈستى قىسىم: باش سۈرەت، لوگو، ئىشلەتكۈچى ئىسمى، VIP بەلگىسى
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 10.dp),
                                shape = RoundedCornerShape(24.dp),
                                color = ClientCardBg,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
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
                                        if (customAvatarBitmap != null) {
                                            Image(
                                                bitmap = customAvatarBitmap!!,
                                                contentDescription = "Avatar",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .border(2.dp, ClientTurquoise, CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(Brush.linearGradient(listOf(ClientIndigo, ClientTurquoise))),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                                            }
                                        }

                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = currentUser.fullName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                if (currentUser.isVip) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = ClientAmber
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
                                                text = "@${currentUser.username}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ClientTurquoise
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            vault.setActiveUsername(null)
                                            activeUser = null
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout", tint = ClientRose)
                                    }
                                }
                            }

                            // ئاستىدىكى 4 بەتنىڭ مەزمۇنى
                            when (selectedTab) {
                                ClientBottomTab.HOME -> {
                                    ClientHomeScreen(
                                        language = language,
                                        accounts = accounts,
                                        searchQuery = searchQuery,
                                        liveDownloadKbps = liveDownloadKbps,
                                        liveUploadKbps = liveUploadKbps,
                                        sessionUsedMb = sessionUsedMb,
                                        isRunningSpeedTest = isRunningSpeedTest,
                                        speedTestResultMbps = speedTestResultMbps,
                                        onSearchQueryChange = { searchQuery = it },
                                        onTriggerSearch = {
                                            homeOverlay = HomeOverlayView.SEARCH_RESULTS
                                        },
                                        onOpenCategoryOverlay = { targetOverlay ->
                                            homeOverlay = targetOverlay
                                        },
                                        onRunFastSpeedTest = {
                                            if (!isRunningSpeedTest) {
                                                scope.launch {
                                                    isRunningSpeedTest = true
                                                    speedTestResultMbps = null
                                                    val measured = measureRealNetworkSpeedMbps()
                                                    speedTestResultMbps = measured
                                                    isRunningSpeedTest = false
                                                }
                                            }
                                        }
                                    )
                                }

                                ClientBottomTab.SETTINGS -> {
                                    ClientSettingsScreen(
                                        language = language,
                                        vault = vault,
                                        onLanguageChange = { newLang ->
                                            language = newLang
                                            vault.setLanguage(newLang)
                                        },
                                        onBackgroundPicked = { uri ->
                                            if (vault.saveCustomBackgroundFromUri(uri)) {
                                                customBgBitmap = vault.loadCustomBackgroundBitmap()
                                                scope.launch { snackbarHostState.showSnackbar("تەگلىك رەسىم مۇۋەپپەقىيەتلىك ئالماشتۇرۇلدى!") }
                                            }
                                        },
                                        onResetBackground = {
                                            vault.clearCustomBackground()
                                            customBgBitmap = null
                                        },
                                        onAvatarPicked = { uri ->
                                            if (vault.saveCustomAvatarFromUri(uri)) {
                                                customAvatarBitmap = vault.loadCustomAvatarBitmap()
                                                scope.launch { snackbarHostState.showSnackbar("باش سۈرەت مۇۋەپپەقىيەتلىك توغرىلاندى!") }
                                            }
                                        },
                                        onSyncCompleted = { msg ->
                                            accounts = vault.loadPersonAccounts(currentUser.username)
                                            reminders = vault.loadReminders()
                                            feedbacks = vault.loadFeedbacks()
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                        },
                                        onClearVault = {
                                            vault.clearAllClientData()
                                            accounts = emptyList()
                                            reminders = emptyList()
                                            feedbacks = emptyList()
                                            scope.launch { snackbarHostState.showSnackbar("ئەپ ئىچىدىكى مەلۇماتلار تازىلاندى!") }
                                        }
                                    )
                                }

                                ClientBottomTab.ACCOUNTS -> {
                                    ClientAccountsScreen(
                                        accounts = accounts,
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
                                            scope.launch { snackbarHostState.showSnackbar("«${item.fullName}» ئۆچۈرۈلدى") }
                                        },
                                        onCopy = { label, value ->
                                            clipboard.setText(AnnotatedString(value))
                                            scope.launch { snackbarHostState.showSnackbar("$label كۆچۈرۈلدى") }
                                        }
                                    )
                                }

                                ClientBottomTab.REMINDERS -> {
                                    ClientRemindersAndFeedbackScreen(
                                        currentUser = currentUser,
                                        reminders = reminders,
                                        feedbacks = feedbacks,
                                        onSendFeedback = { msgText ->
                                            val newFb = ClientFeedbackNote(
                                                id = UUID.randomUUID().toString(),
                                                clientUsername = currentUser.username,
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
                                                    putExtra(Intent.EXTRA_SUBJECT, "Hesabat ئىنكاس (${currentUser.username})")
                                                    putExtra(Intent.EXTRA_TEXT, msgText)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "باشقۇرغۇچىغا ئىنكاس يوللاش"))
                                            }
                                            scope.launch { snackbarHostState.showSnackbar("ئىنكاسىڭىز باشقۇرغۇچىغا يوللاندى!") }
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

    if (showAddEditDialog && activeUser != null) {
        PersonAccountFormDialog(
            ownerUsername = activeUser!!.username,
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
                scope.launch { snackbarHostState.showSnackbar("ھېسابات مەلۇماتى ساقلاندى!") }
            }
        )
    }
}

/**
 * تىزىملىتىش، باشقۇرغۇچىغا تەستىق ئۇچۇرى ئەۋەتىش ۋە باشقۇرغۇچى ئەۋەتكەن دەلىللەش كودى بىلەن كىرىش ئېكرانى
 */
@Composable
fun ClientAuthRegistrationScreen(
    registeredUsers: List<RegisteredAuthUser>,
    onRequestAdminApproval: (String, String, String, String, String) -> Unit,
    onVerifyCodeAndEnter: (String, String) -> Result<Unit>,
    onDirectLogin: (String, String) -> Result<Unit>
) {
    var isRegisterMode by remember { mutableStateOf(registeredUsers.isEmpty()) }
    var awaitingCodeStep by remember { mutableStateOf(false) }

    var username by remember { mutableStateOf(registeredUsers.firstOrNull()?.username.orEmpty()) }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var infoMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(30.dp),
            color = ClientCardBg,
            border = BorderStroke(1.5.dp, ClientTurquoise.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(ClientIndigo, ClientTurquoise))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
                }

                Text(
                    text = "Hesabat ھېسابات سىستېمىسى",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

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
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("كىرىش") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isRegisterMode && !awaitingCodeStep,
                        onClick = {
                            isRegisterMode = true
                            awaitingCodeStep = false
                            errorMsg = null
                        },
                        leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("تىزىملىتىش") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = awaitingCodeStep,
                        onClick = {
                            awaitingCodeStep = true
                            errorMsg = null
                        },
                        leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("كود دەلىللەش") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (registeredUsers.isNotEmpty() && !awaitingCodeStep) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        registeredUsers.forEach { u ->
                            FilterChip(
                                selected = username.equals(u.username, ignoreCase = true),
                                onClick = {
                                    username = u.username
                                    isRegisterMode = false
                                    errorMsg = null
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (u.isVip) Icons.Filled.WorkspacePremium else Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = if (u.isVip) ClientAmber else ClientEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(u.username) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMsg = null
                    },
                    label = { Text("ھېسابات ئىسمى (باشقۇرغۇچى بەرگەن ياكى شەخسىي ئىسىم) *") },
                    leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = ClientTurquoise) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (!awaitingCodeStep) {
                    if (isRegisterMode) {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("تولۇق ئىسمىڭىز") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = ClientIndigo) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("ئادرېسىڭىز / كوچا ۋە ئۆي نومۇرى *") },
                            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ClientRose) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("ئېلخەت ئادرېسىڭىز (Gmail)") },
                            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = ClientAmber) },
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
                        label = { Text("مەخپىي نومۇر *") },
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
                        color = ClientEmerald.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ClientEmerald.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "باشقۇرغۇچى «$username» ھېساباتى ئۈچۈن ئەۋەتىپ بەرگەن دەلىللەش كودىنى (HSB-XXXX-XXXX ياكى VIP-XXXX-XXXX) كىرگۈزۈڭ:",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = {
                            verificationCode = it.uppercase()
                            errorMsg = null
                        },
                        label = { Text("باشقۇرغۇچى دەلىللەش كودى *") },
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
                            if (username.isBlank() || password.isBlank()) {
                                errorMsg = "ھېسابات ئىسمى ۋە مەخپىي نومۇرنى تولۇق كىرگۈزۈڭ!"
                                return@Button
                            }
                            if (isRegisterMode) {
                                onRequestAdminApproval(username, fullName, password, address, email)
                                awaitingCodeStep = true
                                infoMsg = "باشقۇرغۇچىغا تىزىملىتىش ئۇچۇرى يوللاندى! ئەمدى باشقۇرغۇچى ئەۋەتكەن كودنى كىرگۈزۈڭ."
                            } else {
                                val res = onDirectLogin(username, password)
                                res.onFailure { err ->
                                    if (err.message == "NEED_CODE") {
                                        awaitingCodeStep = true
                                        infoMsg = "بۇ ھېساباتنى تۇنجى قېتىم ئېچىش ئۈچۈن باشقۇرغۇچى ئەۋەتكەن دەلىللەش كودىنى كىرگۈزۈڭ:"
                                    } else {
                                        errorMsg = err.message
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClientIndigo),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = if (isRegisterMode) Icons.AutoMirrored.Filled.Send else Icons.Filled.LockOpen,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRegisterMode) "تىزىملىتىپ باشقۇرغۇچىغا ئۇچۇر يوللاش" else "ھېساباتقا كىرىش",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            val res = onVerifyCodeAndEnter(username, verificationCode)
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
                        Text("كودنى دەلىللەپ ئەپكە كىرىش", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * 1-بەت: باش بەت
 * ئىزدەش رامكىسى + پەقەت سىنبەلگە بىلەنلا كۆرۈنىدىغان 3 كارتا (ئىچىدىكى مەزمۇن چەككەندە ئېچىلىدۇ) + Fast.com تور تېزلىك ئۆلچىگۈچى
 */
@Composable
fun ClientHomeScreen(
    language: AppLanguage,
    accounts: List<PersonAccountItem>,
    searchQuery: String,
    liveDownloadKbps: Float,
    liveUploadKbps: Float,
    sessionUsedMb: Float,
    isRunningSpeedTest: Boolean,
    speedTestResultMbps: Float?,
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
        // 1. باش بەتتىكى ئىزدەش رامكىسى (ئىزدىگەندە يېڭى بەت ئېچىلىپ نەتىجە كۆرۈنىدۇ)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ClientCardBg,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            if (language == AppLanguage.UG) "ئىسىم، كوچا، ئادرېس ياكى ھېساباتنى ئىزدەڭ..."
                            else "Search name, street, or account..."
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = ClientTurquoise)
                    },
                    trailingIcon = {
                        Button(
                            onClick = onTriggerSearch,
                            colors = ButtonDefaults.buttonColors(containerColor = ClientIndigo),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(if (language == AppLanguage.UG) "ئىزدەش" else "Search", color = Color.White, fontWeight = FontWeight.Bold)
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

        // 2. سىنبەلگە بىلەنلا كۆرۈنىدىغان 3 مەخسۇس بۆلۈم (ئىچىدىكى مەزمۇن كۆرۈنمەيدۇ، چەككەندە ئېچىلىدۇ)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeIconOnlyCard(
                title = if (language == AppLanguage.UG) "جەمئىي ھېساباتلار" else "Total Accounts",
                badgeCount = totalCount,
                icon = Icons.Filled.Group,
                accent = ClientIndigo,
                modifier = Modifier.weight(1f),
                onClick = { onOpenCategoryOverlay(HomeOverlayView.ALL_ACCOUNTS) }
            )
            HomeIconOnlyCard(
                title = if (language == AppLanguage.UG) "ھەق تاپشۇرغانلار" else "Paid Accounts",
                badgeCount = paidCount,
                icon = Icons.Filled.CheckCircle,
                accent = ClientEmerald,
                modifier = Modifier.weight(1f),
                onClick = { onOpenCategoryOverlay(HomeOverlayView.PAID_ACCOUNTS) }
            )
        }

        HomeIconOnlyCard(
            title = if (language == AppLanguage.UG) "قەرزگە ئاچقۇزغانلار" else "Opened on Credit (Debt)",
            badgeCount = debtCount,
            icon = Icons.Filled.WarningAmber,
            accent = ClientRose,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpenCategoryOverlay(HomeOverlayView.DEBT_ACCOUNTS) }
        )

        // 3. ئاستىدىكى ھەقىقىي Fast.com ئۇسلۇبىدىكى سىمسىز تور ئېقىم مىقدارى ۋە تېزلىك ئۆلچىگۈچ
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = ClientCardBg,
            border = BorderStroke(1.5.dp, ClientTurquoise.copy(alpha = 0.5f)),
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
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = ClientTurquoise, modifier = Modifier.size(24.dp))
                        Text(
                            text = if (language == AppLanguage.UG) "FAST سىمسىز تور ئېقىم ئۆلچىگۈچ" else "FAST Live Network Speedometer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ClientEmerald.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f MB", sessionUsedMb),
                            style = MaterialTheme.typography.labelMedium,
                            color = ClientEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // چوڭ Fast.com تېزلىك كۆرسەتكۈچى
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .border(4.dp, Brush.sweepGradient(listOf(ClientTurquoise, ClientEmerald, ClientIndigo, ClientTurquoise)), CircleShape)
                        .background(Color(0xFF081022)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val displaySpeed = if (speedTestResultMbps != null) {
                            String.format(Locale.US, "%.1f", speedTestResultMbps)
                        } else if (liveDownloadKbps >= 1024f) {
                            String.format(Locale.US, "%.2f", liveDownloadKbps / 1024f)
                        } else {
                            String.format(Locale.US, "%.1f", liveDownloadKbps)
                        }
                        val unitLabel = if (speedTestResultMbps != null) {
                            "Mbps"
                        } else if (liveDownloadKbps >= 1024f) {
                            "MB/s"
                        } else {
                            "KB/s"
                        }

                        Text(
                            text = displaySpeed,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = ClientEmerald
                        )
                        Text(
                            text = unitLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = ClientTurquoise,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("چۈشۈرۈش (Download)", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                        Text(
                            text = String.format(Locale.US, "%.1f KB/s", liveDownloadKbps),
                            fontWeight = FontWeight.Bold,
                            color = ClientEmerald
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("يوللاش (Upload)", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                        Text(
                            text = String.format(Locale.US, "%.1f KB/s", liveUploadKbps),
                            fontWeight = FontWeight.Bold,
                            color = ClientTurquoise
                        )
                    }
                }

                Button(
                    onClick = onRunFastSpeedTest,
                    colors = ButtonDefaults.buttonColors(containerColor = ClientTurquoise),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRunningSpeedTest) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تور تېزلىكى ئۆلچىنىۋاتىدۇ...", color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("FAST ھەقىقىي تور تېزلىكىنى ئۆلچەش", color = Color.White, fontWeight = FontWeight.Bold)
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
    badgeCount: Int,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        color = ClientCardBg,
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 20.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(32.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "چېكىپ كۆرۈش ($badgeCount)",
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
 * 2-بەت: تەڭشەكلەر (تەگلىك رەسىم، باش سۈرەت، تىل، باشقۇرغۇچى بۇلۇت ئۇلىنىشى ۋە تازىلاش)
 */
@Composable
fun ClientSettingsScreen(
    language: AppLanguage,
    vault: ClientVaultManager,
    onLanguageChange: (AppLanguage) -> Unit,
    onBackgroundPicked: (Uri) -> Unit,
    onResetBackground: () -> Unit,
    onAvatarPicked: (Uri) -> Unit,
    onSyncCompleted: (String) -> Unit,
    onClearVault: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cloudUrl by remember { mutableStateOf(vault.getCloudSyncUrl()) }

    val bgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onBackgroundPicked(uri)
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onAvatarPicked(uri)
    }

    val jsonImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                vault.importAdminBackupJson(text).onSuccess { count ->
                    onSyncCompleted("باشقۇرغۇچىدىن $count دانە مەلۇمات ماس قەدەملەندى!")
                }
            }
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 84.dp)
    ) {
        // 1. تەگلىك ۋە باش سۈرەت تەڭشىكى
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ClientCardBg,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = ClientTurquoise)
                        Text("تەگلىك رەسىم ۋە باش سۈرەت تەڭشىكى", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    }

                    Button(
                        onClick = { bgPickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = ClientIndigo),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تېلېفون سۈرەت ئامبىرىدىن تەگلىك رەسىم تاللاش", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onResetBackground,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ئەسلىدىكى تەگلىككە قايتۇرۇش")
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    Button(
                        onClick = { avatarPickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = ClientTurquoise),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تېلېفون سۈرەت ئامبىرىدىن باش سۈرەت توغرىلاش", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. تىل تاللاش
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ClientCardBg,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Language, contentDescription = null, tint = ClientEmerald)
                        Text("تىل تەڭشىكى (Language)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = language == lang,
                                onClick = { onLanguageChange(lang) },
                                label = { Text(lang.displayName) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 3. باشقۇرغۇچى ئەپى بىلەن ئۇلاش ۋە ساقلاش ئامبىرىنى تازىلاش
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ClientCardBg,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, tint = ClientAmber)
                        Text("باشقۇرغۇچى ئەپى بىلەن باغلىنىش ۋە ئامبار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    }

                    OutlinedTextField(
                        value = cloudUrl,
                        onValueChange = {
                            cloudUrl = it
                            vault.setCloudSyncUrl(it)
                        },
                        label = { Text("باشقۇرغۇچى بۇلۇت ئادرېسى (Cloud Sync URL)") },
                        placeholder = { Text("https://your-project.firebaseio.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                val res = vault.pullFromAdminCloud()
                                onSyncCompleted(res.getOrElse { "ماس قەدەملەشتە خاتالىق كۆرۈلدى" })
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClientEmerald),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("باشقۇرغۇچى ئەپىدىن مەلۇمات ۋە ئەسلەتمىلەرنى تارتىش", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { jsonImportLauncher.launch(arrayOf("application/json", "*/*")) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("باشقۇرغۇچى ئەۋەتكەن ھۆججەتنى ئەكىرىش")
                    }

                    Button(
                        onClick = onClearVault,
                        colors = ButtonDefaults.buttonColors(containerColor = ClientRose),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ئەپ ئىچىدىكى مەلۇماتلارنى تازىلاش", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 3-بەت: ھېساباتلار (ئىسمى، ھېسابات ئىسمى، مەخپىي نومۇرى، تىزىملاتقان ۋاقتى، پۇل تۆلىگەن ئايلار)
 */
@Composable
fun ClientAccountsScreen(
    accounts: List<PersonAccountItem>,
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
                    text = "بارلىق ساقلانغان ھېساباتلار (${accounts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                OutlinedButton(
                    onClick = onSearchClick,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ClientTurquoise)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = ClientTurquoise, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ئىزدەش", color = ClientTurquoise)
                }
            }
        }

        items(accounts, key = { it.id }) { item ->
            ClientPersonAccountCard(
                item = item,
                onToggleMonth = { mIdx -> onToggleMonth(item, mIdx) },
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) },
                onCopy = onCopy
            )
        }
    }
}

@Composable
fun ClientPersonAccountCard(
    item: PersonAccountItem,
    onToggleMonth: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    var pwdVisible by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = ClientCardBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
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
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(ClientIndigo, ClientTurquoise))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
                    }
                    Column {
                        Text(
                            text = "ئىسمى: ${item.fullName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.DateRange, contentDescription = null, tint = ClientTurquoise, modifier = Modifier.size(14.dp))
                            Text(
                                text = "تىزىملاتقان ۋاقتى: ${item.openDate.ifBlank { "يېزىلمىغان" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.LightGray
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = ClientTurquoise)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = ClientRose)
                    }
                }
            }

            // ھېسابات ئىسمى ۋە مەخپىي نومۇرى
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.06f),
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
                            Text("ھېسابات ئىسمى: ${item.accountName}", fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { onCopy("ھېسابات ئىسمى", item.accountName) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = ClientTurquoise, modifier = Modifier.size(16.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = ClientAmber, modifier = Modifier.size(18.dp))
                            Text(
                                text = "مەخپىي نومۇرى: ${if (pwdVisible) item.password else "••••••••"}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row {
                            IconButton(onClick = { pwdVisible = !pwdVisible }, modifier = Modifier.size(28.dp)) {
                                Icon(if (pwdVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onCopy("مەخپىي نومۇر", item.password) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = ClientAmber, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (item.streetName.isNotBlank() || item.houseNumber.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = ClientRose, modifier = Modifier.size(16.dp))
                            Text("ئادرېس/كوچا: ${item.streetName} ${item.houseNumber}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // پۇل تۆلىگەن ئايلار (12 ئاي)
            Text(
                text = "پۇل تۆلىگەن ئايلار (${item.paidCount()} / 12):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

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
                                Text("${mIdx + 1}-ئاي", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (paid) "تۆلەندى" else "تۆلەنمىدى",
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
 * 4-بەت: ئەسلەتمىلەر (باشقۇرغۇچى ئەۋەتكەن ئەسكەرتمىلەر ۋە باشقۇرغۇچىغا ئىنكاس يېزىش)
 */
@Composable
fun ClientRemindersAndFeedbackScreen(
    currentUser: RegisteredAuthUser,
    reminders: List<AdminReminderNote>,
    feedbacks: List<ClientFeedbackNote>,
    onSendFeedback: (String) -> Unit
) {
    var feedbackText by remember { mutableStateOf("") }

    val myReminders = remember(reminders, currentUser.username) {
        reminders.filter {
            it.targetUsername == "ALL" || it.targetUsername.equals(currentUser.username, ignoreCase = true)
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 84.dp)
    ) {
        item {
            Text(
                text = "باشقۇرغۇچى تەرىپىدىن يوللانغان ئەسلەتمىلەر (${myReminders.size}):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        items(myReminders, key = { it.id }) { rem ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ClientCardBg,
                border = BorderStroke(1.2.dp, ClientAmber.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, tint = ClientAmber)
                        Text(rem.title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(rem.content, style = MaterialTheme.typography.bodyMedium)
                    Text(rem.timestamp, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ClientCardBg,
                border = BorderStroke(1.dp, ClientTurquoise.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("باشقۇرغۇچىغا ئىنكاس ياكى تەلەپ يوللاش:", fontWeight = FontWeight.ExtraBold)
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = { Text("باشقۇرغۇچىغا يوللايدىغان گېپىڭىزنى يېزىڭ...") },
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
                        colors = ButtonDefaults.buttonColors(containerColor = ClientIndigo),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("باشقۇرغۇچىغا يوللاش", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(feedbacks, key = { it.id }) { fb ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ClientCardBg,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("مېنىڭ ئىنكاسىم: ${fb.message}", style = MaterialTheme.typography.bodyMedium)
                    if (fb.adminReply.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ClientEmerald.copy(alpha = 0.16f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "باشقۇرغۇچى جاۋابى: ${fb.adminReply}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ClientEmerald,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Text("باشقۇرغۇچى تېخى جاۋاب قايتۇرمىدى", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                    }
                }
            }
        }
    }
}

/**
 * باش بەتتىكى سىنبەلگە كارتىلىرىنى چەككەندە ياكى ئىزدىگەندە ئېچىلىدىغان مەخسۇس يېڭى بەت
 */
@Composable
fun ClientDedicatedOverlayPage(
    overlayType: HomeOverlayView,
    allAccounts: List<PersonAccountItem>,
    searchQuery: String,
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
        if (cleanQ.isEmpty()) {
            base
        } else {
            base.filter { item ->
                item.fullName.lowercase().contains(cleanQ) ||
                    item.accountName.lowercase().contains(cleanQ) ||
                    item.streetName.lowercase().contains(cleanQ) ||
                    item.houseNumber.lowercase().contains(cleanQ) ||
                    item.notes.lowercase().contains(cleanQ)
            }
        }
    }

    val pageTitle = when (overlayType) {
        HomeOverlayView.ALL_ACCOUNTS -> "جەمئىي ھېساباتلار (${filteredList.size})"
        HomeOverlayView.PAID_ACCOUNTS -> "ھەق تاپشۇرغانلار (${filteredList.size})"
        HomeOverlayView.DEBT_ACCOUNTS -> "قەرزگە ئاچقۇزغانلار (${filteredList.size})"
        else -> "ئىزدەش نەتىجىسى (${filteredList.size})"
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
                    color = ClientCardBg,
                    border = BorderStroke(1.dp, ClientTurquoise.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = ClientIndigo),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("قايتىش", color = Color.White)
                            }
                            Text(pageTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("ئىسىم، كوچا، ئادرېس ياكى ھېسابات ئىسمىنى يېزىڭ...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ClientTurquoise) },
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
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ClientCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.SearchOff, contentDescription = null, tint = ClientRose, modifier = Modifier.size(44.dp))
                            Text("ماس كېلىدىغان ھېسابات تېپىلمىدى", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { item ->
                    ClientPersonAccountCard(
                        item = item,
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

@Composable
fun PersonAccountFormDialog(
    ownerUsername: String,
    initial: PersonAccountItem?,
    onDismiss: () -> Unit,
    onSave: (PersonAccountItem) -> Unit
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var fullName by remember { mutableStateOf(initial?.fullName.orEmpty()) }
    var accountName by remember { mutableStateOf(initial?.accountName.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var streetName by remember { mutableStateOf(initial?.streetName.orEmpty()) }
    var houseNumber by remember { mutableStateOf(initial?.houseNumber.orEmpty()) }
    var openDate by remember { mutableStateOf(initial?.openDate ?: today) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial == null) "يېڭى ھېسابات قوشۇش" else "ھېساباتنى تەھرىرلەش", fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("كىشىنىڭ ئىسمى *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text("ھېسابات ئىسمى *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("مەخپىي نومۇرى *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = streetName,
                    onValueChange = { streetName = it },
                    label = { Text("كوچا ئىسمى / ئادرېس") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = houseNumber,
                    onValueChange = { houseNumber = it },
                    label = { Text("ئۆي نومۇرى") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openDate,
                    onValueChange = { openDate = it },
                    label = { Text("تىزىملاتقان ۋاقتى") },
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
                                ownerUsername = ownerUsername,
                                fullName = cleanName,
                                accountName = accountName.trim().ifEmpty { cleanName },
                                password = password.trim().ifEmpty { "123456" },
                                streetName = streetName.trim(),
                                houseNumber = houseNumber.trim(),
                                flowAmount = initial?.flowAmount ?: "100 GB",
                                openDate = openDate.trim(),
                                notes = initial?.notes.orEmpty(),
                                paidMonths = initial?.paidMonths ?: List(12) { false }
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClientEmerald)
            ) {
                Text("ساقلاش", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("بىكار قىلىش")
            }
        }
    )
}

/**
 * ھەقىقىي تور سۈرئىتىنى ئۆلچەش فۇنكسىيەسى (Fast.com غا ئوخشاش ھەقىقىي ئېقىم تارتىپ ئۆلچەيدۇ)
 */
suspend fun measureRealNetworkSpeedMbps(): Float = withContext(Dispatchers.IO) {
    try {
        val startBytes = TrafficStats.getTotalRxBytes()
        val startTime = System.currentTimeMillis()
        val conn = (URL("https://speed.cloudflare.com/__down?bytes=2500000").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
        }
        val buffer = ByteArray(16384)
        var downloaded = 0L
        conn.inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                downloaded += read
                if (System.currentTimeMillis() - startTime > 3500L) break
            }
        }
        val elapsedSec = ((System.currentTimeMillis() - startTime).coerceAtLeast(200L)) / 1000f
        val rxDiff = (TrafficStats.getTotalRxBytes() - startBytes).coerceAtLeast(downloaded)
        val bitsPerSec = (rxDiff * 8f) / elapsedSec
        (bitsPerSec / (1000f * 1000f)).coerceAtLeast(0.5f)
    } catch (_: Exception) {
        0.0f
    }
}
