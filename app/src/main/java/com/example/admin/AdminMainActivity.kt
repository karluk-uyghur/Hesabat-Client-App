package com.example.admin

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

const val ADMIN_MASTER_EMAIL = "uyghur162142@gmail.com"
const val SHARED_CRYPTO_SALT = "HESABAT_SYNC_2026_SECRET"

val WolfGold = Color(0xFFF5B800)
val WolfBlack = Color(0xFF0B0B0B)
val AdminIndigo = Color(0xFF6366F1)
val AdminTurquoise = Color(0xFF06B6D4)
val AdminEmerald = Color(0xFF10B981)
val AdminRose = Color(0xFFF43F5E)

enum class AdminLanguage(val code: String, val title: String) {
    UG("ug", "ئۇيغۇرچە"),
    AR("ar", "العربية")
}

fun tr(lang: AdminLanguage, ug: String, ar: String): String {
    return if (lang == AdminLanguage.UG) ug else ar
}

enum class AccountOpenMethod(val ugLabel: String, val arLabel: String, val icon: ImageVector) {
    BY_GMAIL("Gmail ئارقىلىق ھېسابات ئېچىش", "فتح حساب عبر Gmail", Icons.Filled.Email),
    BY_NAME("ئىسىم ئارقىلىق ھېسابات ئېچىش", "فتح حساب عبر الاسم", Icons.Filled.Person),
    BY_ACCOUNT_ID("ھېسابات نامى ئارقىلىق ئېچىش", "فتح حساب عبر المعرف", Icons.Filled.Badge)
}

class AdminMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.decorView.filterTouchesWhenObscured = true

        enableEdgeToEdge()
        setContent {
            AdminRootScreen()
        }
    }
}

data class ManagedUserAccount(
    val id: String,
    val openMethod: String, // BY_GMAIL, BY_NAME, BY_ACCOUNT_ID
    val fullName: String,
    val username: String,
    val gmail: String,
    val password: String,
    val address: String,
    val licenseCode: String,
    val isVip: Boolean,
    val vipExpireEpochMillis: Long,
    val status: String, // PENDING, ACTIVE, VIP, FROZEN
    val createdAt: String
) {
    fun isVipActive(): Boolean = isVip && (vipExpireEpochMillis == 0L || vipExpireEpochMillis > System.currentTimeMillis())
    fun vipRemainingDays(): Long {
        if (!isVip) return 0L
        if (vipExpireEpochMillis == 0L) return 9999L
        val diff = vipExpireEpochMillis - System.currentTimeMillis()
        return if (diff <= 0L) 0L else (diff / (1000L * 60L * 60L * 24L)) + 1L
    }
}

data class AppVersionRelease(
    val versionName: String,
    val apkDownloadUrl: String,
    val releaseNotes: String,
    val forceUpdate: Boolean,
    val targetAccount: String, // ALL ياكى مەخسۇس ھېسابات
    val publishedAt: String
)

data class ClientAppControlConfig(
    val allowNewRegistration: Boolean,
    val requireVerificationCode: Boolean,
    val remoteMaintenanceLock: Boolean,
    val announcementBanner: String
)

class AdminStorageEngine(private val context: Context) {
    private val prefs = context.getSharedPreferences("hesabat_admin_v4_vault", Context.MODE_PRIVATE)
    private val dbFile: File
        get() = File(context.filesDir, "hesabat_admin_v4_accounts.json")
    private val bgFile: File
        get() = File(context.filesDir, "admin_custom_bg.jpg")
    private val logoFile: File
        get() = File(context.filesDir, "admin_custom_logo.jpg")

    init {
        // بۇرۇنقى ئۈلگە ھېساباتلارنى بىر قېتىم پاكىز تازىلاش
        if (!prefs.getBoolean("cleaned_sample_accounts_v4", false)) {
            prefs.edit()
                .remove("accounts_json")
                .putBoolean("cleaned_sample_accounts_v4", true)
                .commit()
            runCatching { if (dbFile.exists()) dbFile.delete() }
        }
    }

    fun verifyAdminPassword(input: String): Boolean {
        val savedHash = prefs.getString("admin_pwd_hash", null) ?: sha256("162142")
        return sha256(input.trim()) == savedHash
    }

    fun updateAdminPassword(newPwd: String): Boolean {
        val clean = newPwd.trim()
        if (clean.length < 4) return false
        prefs.edit().putString("admin_pwd_hash", sha256(clean)).commit()
        return true
    }

    fun getLanguage(): AdminLanguage {
        return if (prefs.getString("lang", "ug") == "ar") AdminLanguage.AR else AdminLanguage.UG
    }

    fun setLanguage(lang: AdminLanguage) {
        prefs.edit().putString("lang", lang.code).commit()
    }

    fun isDarkMode(): Boolean = prefs.getBoolean("dark_mode", true)

    fun setDarkMode(dark: Boolean) {
        prefs.edit().putBoolean("dark_mode", dark).commit()
    }

    fun getCloudSyncUrl(): String = prefs.getString("cloud_sync_url", "") ?: ""

    fun setCloudSyncUrl(url: String) {
        prefs.edit().putString("cloud_sync_url", url.trim()).commit()
    }

    fun generateVerificationCode(identifier: String, isVip: Boolean = false): String {
        val clean = identifier.trim().lowercase()
        if (clean.isEmpty()) return ""
        val raw = sha256("$SHARED_CRYPTO_SALT::$clean").uppercase()
        val prefix = if (isVip) "VIP" else "HSB"
        return "$prefix-${raw.substring(0, 4)}-${raw.substring(4, 8)}"
    }

    fun loadAccounts(): List<ManagedUserAccount> {
        val raw = runCatching {
            if (dbFile.exists()) dbFile.readText(Charsets.UTF_8) else ""
        }.getOrDefault("").ifBlank {
            prefs.getString("accounts_json", "[]") ?: "[]"
        }
        if (raw.isBlank() || raw == "[]") return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ManagedUserAccount(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    openMethod = o.optString("openMethod", "BY_GMAIL"),
                    fullName = o.optString("fullName"),
                    username = o.optString("username"),
                    gmail = o.optString("gmail"),
                    password = o.optString("password"),
                    address = o.optString("address"),
                    licenseCode = o.optString("licenseCode"),
                    isVip = o.optBoolean("isVip", false),
                    vipExpireEpochMillis = o.optLong("vipExpireEpochMillis", 0L),
                    status = o.optString("status", "ACTIVE"),
                    createdAt = o.optString("createdAt")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAccounts(list: List<ManagedUserAccount>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("openMethod", a.openMethod)
                    put("fullName", a.fullName)
                    put("username", a.username)
                    put("gmail", a.gmail)
                    put("password", a.password)
                    put("address", a.address)
                    put("licenseCode", a.licenseCode)
                    put("isVip", a.isVip)
                    put("vipExpireEpochMillis", a.vipExpireEpochMillis)
                    put("status", a.status)
                    put("createdAt", a.createdAt)
                }
            )
        }
        val text = arr.toString()
        prefs.edit().putString("accounts_json", text).commit()
        runCatching { dbFile.writeText(text, Charsets.UTF_8) }
    }

    fun loadLatestRelease(): AppVersionRelease {
        return AppVersionRelease(
            versionName = prefs.getString("rel_version", "1.1.0") ?: "1.1.0",
            apkDownloadUrl = prefs.getString("rel_url", "") ?: "",
            releaseNotes = prefs.getString("rel_notes", "") ?: "",
            forceUpdate = prefs.getBoolean("rel_force", false),
            targetAccount = prefs.getString("rel_target", "ALL") ?: "ALL",
            publishedAt = prefs.getString("rel_date", "") ?: ""
        )
    }

    fun saveLatestRelease(rel: AppVersionRelease) {
        prefs.edit()
            .putString("rel_version", rel.versionName)
            .putString("rel_url", rel.apkDownloadUrl)
            .putString("rel_notes", rel.releaseNotes)
            .putBoolean("rel_force", rel.forceUpdate)
            .putString("rel_target", rel.targetAccount)
            .putString("rel_date", rel.publishedAt)
            .commit()
    }

    fun loadClientControlConfig(): ClientAppControlConfig {
        return ClientAppControlConfig(
            allowNewRegistration = prefs.getBoolean("cfg_allow_reg", true),
            requireVerificationCode = prefs.getBoolean("cfg_req_code", true),
            remoteMaintenanceLock = prefs.getBoolean("cfg_remote_lock", false),
            announcementBanner = prefs.getString("cfg_banner", "") ?: ""
        )
    }

    fun saveClientControlConfig(cfg: ClientAppControlConfig) {
        prefs.edit()
            .putBoolean("cfg_allow_reg", cfg.allowNewRegistration)
            .putBoolean("cfg_req_code", cfg.requireVerificationCode)
            .putBoolean("cfg_remote_lock", cfg.remoteMaintenanceLock)
            .putString("cfg_banner", cfg.announcementBanner)
            .commit()
    }

    fun saveBackgroundFromUri(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                bgFile.outputStream().use { out -> input.copyTo(out) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearBackground() {
        runCatching { if (bgFile.exists()) bgFile.delete() }
    }

    fun loadBackgroundBitmap(): ImageBitmap? {
        return try {
            if (bgFile.exists()) BitmapFactory.decodeFile(bgFile.absolutePath)?.asImageBitmap() else null
        } catch (_: Exception) {
            null
        }
    }

    fun saveLogoFromUri(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                logoFile.outputStream().use { out -> input.copyTo(out) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadLogoBitmap(): ImageBitmap? {
        return try {
            if (logoFile.exists()) BitmapFactory.decodeFile(logoFile.absolutePath)?.asImageBitmap() else null
        } catch (_: Exception) {
            null
        }
    }

    fun buildSyncPayloadJson(): String {
        val rel = loadLatestRelease()
        val cfg = loadClientControlConfig()
        val accountsArr = JSONArray(prefs.getString("accounts_json", "[]") ?: "[]")
        return JSONObject().apply {
            put("adminMasterEmail", ADMIN_MASTER_EMAIL)
            put("syncedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            put("accounts", accountsArr)
            put(
                "appUpdate",
                JSONObject().apply {
                    put("versionName", rel.versionName)
                    put("apkDownloadUrl", rel.apkDownloadUrl)
                    put("releaseNotes", rel.releaseNotes)
                    put("forceUpdate", rel.forceUpdate)
                    put("targetAccount", rel.targetAccount)
                    put("publishedAt", rel.publishedAt)
                }
            )
            put(
                "clientConfig",
                JSONObject().apply {
                    put("allowNewRegistration", cfg.allowNewRegistration)
                    put("requireVerificationCode", cfg.requireVerificationCode)
                    put("remoteMaintenanceLock", cfg.remoteMaintenanceLock)
                    put("announcementBanner", cfg.announcementBanner)
                }
            )
        }.toString(2)
    }

    suspend fun pushSyncToCloud(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = getCloudSyncUrl().trim()
            if (endpoint.isEmpty()) {
                return@runCatching "LOCAL_SAVED"
            }
            val targetUrl = if (endpoint.endsWith(".json")) endpoint else "$endpoint/hesabat_sync.json"
            val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { out ->
                out.write(buildSyncPayloadJson().toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code in 200..299) "CLOUD_OK" else "HTTP_$code"
        }
    }

    fun clearAllData() {
        prefs.edit().remove("accounts_json").commit()
        runCatching { if (dbFile.exists()) dbFile.delete() }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

enum class AdminTab(val ugTitle: String, val arTitle: String, val icon: ImageVector) {
    HOME("باش بەت", "الرئيسية", Icons.Filled.Home),
    OPEN_ACCOUNT("ھېسابات ئېچىش", "فتح حساب", Icons.Filled.PersonAdd),
    VIP_CENTER("VIP ئەزالار", "أعضاء VIP", Icons.Filled.WorkspacePremium),
    UPDATE_PUSH("نەشر يوللاش", "تحديث التطبيق", Icons.Filled.SystemUpdate),
    SETTINGS("تەڭشەكلەر", "الإعدادات", Icons.Filled.Settings)
}

enum class HomeAccountListFilter {
    NONE,
    VIP_MEMBERS,
    ALL_OPENED_ACCOUNTS
}

@Composable
fun AdminBrandLogo(
    customLogo: ImageBitmap?,
    sizeDp: Int = 72
) {
    if (customLogo != null) {
        Image(
            bitmap = customLogo,
            contentDescription = "Admin Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape((sizeDp / 3).dp))
                .border(2.dp, WolfGold, RoundedCornerShape((sizeDp / 3).dp))
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.ic_admin_logo),
            contentDescription = "Wolf Admin Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(RoundedCornerShape((sizeDp / 3).dp))
                .border(2.dp, WolfGold, RoundedCornerShape((sizeDp / 3).dp))
        )
    }
}

@Composable
fun AdminRootScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val storage = remember { AdminStorageEngine(context.applicationContext) }

    var lang by remember { mutableStateOf(storage.getLanguage()) }
    var isDark by remember { mutableStateOf(storage.isDarkMode()) }
    var isAuthenticated by remember { mutableStateOf(false) }

    var currentTab by remember { mutableStateOf(AdminTab.HOME) }
    var homeFilterOverlay by remember { mutableStateOf(HomeAccountListFilter.NONE) }
    var searchQuery by remember { mutableStateOf("") }

    var accounts by remember { mutableStateOf(storage.loadAccounts()) }
    var latestRelease by remember { mutableStateOf(storage.loadLatestRelease()) }
    var clientConfig by remember { mutableStateOf(storage.loadClientControlConfig()) }

    var customBg by remember { mutableStateOf(storage.loadBackgroundBitmap()) }
    var customLogo by remember { mutableStateOf(storage.loadLogoBitmap()) }

    val bgColor = if (isDark) Color(0xFF070B16) else Color(0xFFF8FAFC)
    val cardColor = if (isDark) Color(0xE6111A2E) else Color(0xF2FFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)

    val saveAndSyncAccounts: (List<ManagedUserAccount>) -> Unit = { updated ->
        accounts = updated
        storage.saveAccounts(updated)
        scope.launch { storage.pushSyncToCloud() }
    }

    val sendViaMasterGmail: (String, String, String) -> Unit = { targetGmail, subject, body ->
        runCatching {
            val recipient = targetGmail.trim().ifBlank { ADMIN_MASTER_EMAIL }
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$recipient")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient, ADMIN_MASTER_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(Intent.createChooser(intent, tr(lang, "Gmail ئارقىلىق يوللاش", "إرسال عبر Gmail")))
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (isDark) {
                darkColorScheme(
                    primary = WolfGold,
                    secondary = AdminTurquoise,
                    background = bgColor,
                    surface = cardColor,
                    onSurface = textColor,
                    onBackground = textColor
                )
            } else {
                lightColorScheme(
                    primary = AdminIndigo,
                    secondary = AdminTurquoise,
                    background = bgColor,
                    surface = cardColor,
                    onSurface = textColor,
                    onBackground = textColor
                )
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (customBg != null) {
                    Image(
                        bitmap = customBg!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isDark) Color(0xAA050914) else Color(0x88FFFFFF))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    if (isDark) listOf(Color(0xFF070B16), Color(0xFF101935), Color(0xFF060913))
                                    else listOf(Color(0xFFFEF9C3), Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                                )
                            )
                    )
                }

                if (!isAuthenticated) {
                    // مەخپىي نومۇر ئەسكەرتىشى يوق پاكىز كىرىش بېتى
                    AdminCleanLoginScreen(
                        lang = lang,
                        cardColor = cardColor,
                        textColor = textColor,
                        customLogo = customLogo,
                        onSwitchLang = { newLang ->
                            lang = newLang
                            storage.setLanguage(newLang)
                        },
                        onVerify = { inputPwd ->
                            val ok = storage.verifyAdminPassword(inputPwd)
                            if (ok) isAuthenticated = true
                            ok
                        }
                    )
                } else {
                    if (homeFilterOverlay != HomeAccountListFilter.NONE) {
                        BackHandler { homeFilterOverlay = HomeAccountListFilter.NONE }
                        HomeFilteredAccountsPage(
                            lang = lang,
                            filter = homeFilterOverlay,
                            accounts = accounts,
                            searchQuery = searchQuery,
                            cardColor = cardColor,
                            textColor = textColor,
                            onSearchChange = { searchQuery = it },
                            onBack = { homeFilterOverlay = HomeAccountListFilter.NONE },
                            onCopyCode = { code ->
                                clipboard.setText(AnnotatedString(code))
                                scope.launch {
                                    snackbarHostState.showSnackbar(tr(lang, "كود كۆچۈرۈلدى: $code", "تم نسخ الرمز: $code"))
                                }
                            },
                            onSendGmail = { acc ->
                                sendViaMasterGmail(
                                    acc.gmail,
                                    tr(lang, "Hesabat ھېسابات دەلىللەش كودىڭىز", "رمز تفعيل حساب Hesabat"),
                                    buildString {
                                        appendLine(tr(lang, "ھۆرمەتلىك ${acc.fullName}،", "عزيزي ${acc.fullName}،"))
                                        appendLine(tr(lang, "باشقۇرغۇچى ($ADMIN_MASTER_EMAIL) تەرىپىدىن ھېساباتىڭىز ئېچىلدى:", "تم فتح حسابك بواسطة المسؤول ($ADMIN_MASTER_EMAIL):"))
                                        appendLine("• ${tr(lang, "ھېسابات نامى", "اسم الحساب")}: ${acc.username}")
                                        appendLine("• ${tr(lang, "مەخپىي نومۇر", "كلمة المرور")}: ${acc.password}")
                                        appendLine("• ${tr(lang, "دەلىللەش كودى", "رمز التفعيل")}: ${acc.licenseCode}")
                                    }
                                )
                            },
                            onDelete = { acc ->
                                saveAndSyncAccounts(accounts.filterNot { it.id == acc.id })
                            }
                        )
                    } else {
                        Scaffold(
                            containerColor = Color.Transparent,
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            bottomBar = {
                                NavigationBar(
                                    containerColor = if (isDark) Color(0xF00B1120) else Color(0xF0FFFFFF),
                                    tonalElevation = 10.dp
                                ) {
                                    AdminTab.entries.forEach { tab ->
                                        NavigationBarItem(
                                            selected = currentTab == tab,
                                            onClick = { currentTab = tab },
                                            icon = { Icon(tab.icon, contentDescription = null) },
                                            label = {
                                                Text(
                                                    text = tr(lang, tab.ugTitle, tab.arTitle),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (currentTab == tab) FontWeight.ExtraBold else FontWeight.Normal
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(horizontal = 16.dp)
                            ) {
                                // ئۈستى قىسىم: بۆرە لوگوسى، باشقۇرغۇچى Gmail ئادرېسى ۋە چېكىنىش
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 10.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = cardColor,
                                    border = BorderStroke(1.2.dp, WolfGold.copy(alpha = 0.55f))
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
                                            AdminBrandLogo(customLogo = customLogo, sizeDp = 46)
                                            Column {
                                                Text(
                                                    text = tr(lang, "باش باشقۇرغۇچى سۇپىسى", "لوحة تحكم المسؤول"),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = textColor
                                                )
                                                Text(
                                                    text = ADMIN_MASTER_EMAIL,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = WolfGold,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    isDark = !isDark
                                                    storage.setDarkMode(isDark)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                                    contentDescription = null,
                                                    tint = WolfGold
                                                )
                                            }
                                            IconButton(onClick = { isAuthenticated = false }) {
                                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = AdminRose)
                                            }
                                        }
                                    }
                                }

                                when (currentTab) {
                                    AdminTab.HOME -> {
                                        AdminCleanHomeTab(
                                            lang = lang,
                                            accounts = accounts,
                                            searchQuery = searchQuery,
                                            cardColor = cardColor,
                                            textColor = textColor,
                                            onSearchChange = { searchQuery = it },
                                            onTriggerSearch = {
                                                homeFilterOverlay = HomeAccountListFilter.ALL_OPENED_ACCOUNTS
                                            },
                                            onOpenVipMembers = {
                                                searchQuery = ""
                                                homeFilterOverlay = HomeAccountListFilter.VIP_MEMBERS
                                            },
                                            onOpenAllAccounts = {
                                                searchQuery = ""
                                                homeFilterOverlay = HomeAccountListFilter.ALL_OPENED_ACCOUNTS
                                            },
                                            onNavigateTab = { currentTab = it }
                                        )
                                    }

                                    AdminTab.OPEN_ACCOUNT -> {
                                        AdminOpenAccountTab(
                                            lang = lang,
                                            storage = storage,
                                            accounts = accounts,
                                            cardColor = cardColor,
                                            textColor = textColor,
                                            onAccountCreated = { newAcc, sendEmailNow ->
                                                saveAndSyncAccounts(listOf(newAcc) + accounts)
                                                clipboard.setText(AnnotatedString(newAcc.licenseCode))
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        tr(
                                                            lang,
                                                            "ھېسابات ئېچىلدى ۋە كودى كۆچۈرۈلدى: ${newAcc.licenseCode}",
                                                            "تم فتح الحساب ونسخ الرمز: ${newAcc.licenseCode}"
                                                        )
                                                    )
                                                }
                                                if (sendEmailNow) {
                                                    sendViaMasterGmail(
                                                        newAcc.gmail,
                                                        tr(lang, "Hesabat يېڭى ھېسابات ۋە دەلىللەش كودىڭىز", "حساب Hesabat الجديد ورمز التفعيل"),
                                                        buildString {
                                                            appendLine(tr(lang, "ئەسسالامۇئەلەيكۇم ${newAcc.fullName}،", "السلام عليكم ${newAcc.fullName}،"))
                                                            appendLine(tr(lang, "باشقۇرغۇچى ($ADMIN_MASTER_EMAIL) سىزگە ھېسابات ئېچىپ بەردى:", "قام المسؤول ($ADMIN_MASTER_EMAIL) بفتح حساب لك:"))
                                                            appendLine("• ${tr(lang, "ھېسابات نامى", "اسم الحساب")}: ${newAcc.username}")
                                                            appendLine("• ${tr(lang, "مەخپىي نومۇر", "كلمة المرور")}: ${newAcc.password}")
                                                            appendLine("• ${tr(lang, "دەلىللەش كودى", "رمز التفعيل")}: ${newAcc.licenseCode}")
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                    }

                                    AdminTab.VIP_CENTER -> {
                                        AdminVipDurationTab(
                                            lang = lang,
                                            accounts = accounts,
                                            storage = storage,
                                            cardColor = cardColor,
                                            textColor = textColor,
                                            onUpdateVip = { targetAcc, days ->
                                                val isVipNow = days > 0
                                                val expireMillis = if (isVipNow) System.currentTimeMillis() + days.toLong() * 24L * 3600L * 1000L else 0L
                                                val updatedCode = storage.generateVerificationCode(targetAcc.username, isVipNow)
                                                saveAndSyncAccounts(
                                                    accounts.map {
                                                        if (it.id == targetAcc.id) {
                                                            it.copy(
                                                                isVip = isVipNow,
                                                                vipExpireEpochMillis = expireMillis,
                                                                status = if (isVipNow) "VIP" else "ACTIVE",
                                                                licenseCode = updatedCode
                                                            )
                                                        } else it
                                                    }
                                                )
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        if (isVipNow) tr(lang, "VIP مۇددىتى بېكىتىلدى ($days كۈن)", "تم تحديد مدة VIP ($days يوم)")
                                                        else tr(lang, "VIP ھوقۇقى توختىتىلدى", "تم إيقاف VIP")
                                                    )
                                                }
                                            }
                                        )
                                    }

                                    AdminTab.UPDATE_PUSH -> {
                                        AdminVersionPushTab(
                                            lang = lang,
                                            accounts = accounts,
                                            latestRelease = latestRelease,
                                            cardColor = cardColor,
                                            textColor = textColor,
                                            onPublishUpdate = { newRel ->
                                                latestRelease = newRel
                                                storage.saveLatestRelease(newRel)
                                                scope.launch {
                                                    storage.pushSyncToCloud()
                                                    snackbarHostState.showSnackbar(
                                                        tr(lang, "يېڭى نەشر (${newRel.versionName}) بارلىق ھېساباتلارغا يوللاندى!", "تم إرسال الإصدار الجديد (${newRel.versionName}) لجميع الحسابات!")
                                                    )
                                                }
                                                val targetEmails = if (newRel.targetAccount == "ALL") {
                                                    accounts.mapNotNull { it.gmail.takeIf { g -> g.contains("@") } }.joinToString(",")
                                                } else {
                                                    accounts.firstOrNull { it.username == newRel.targetAccount }?.gmail.orEmpty()
                                                }
                                                sendViaMasterGmail(
                                                    targetEmails,
                                                    tr(lang, "Hesabat ئەپىنىڭ يېڭى نەشرى (${newRel.versionName}) چىقتى!", "تحديث جديد لتطبيق Hesabat (${newRel.versionName})"),
                                                    buildString {
                                                        appendLine(tr(lang, "ھۆرمەتلىك ئىشلەتكۈچى،", "عزيزي المستخدم،"))
                                                        appendLine(tr(lang, "ئەپنىڭ يېڭى نەشرى تارقىتىلدى:", "تم إصدار نسخة جديدة من التطبيق:"))
                                                        appendLine("• ${tr(lang, "نەشر نومۇرى", "رقم الإصدار")}: ${newRel.versionName}")
                                                        appendLine("• ${tr(lang, "چۈشۈرۈش ئادرېسى", "رابط التحميل")}: ${newRel.apkDownloadUrl}")
                                                        appendLine("• ${tr(lang, "يېڭىلىقلار", "التفاصيل")}: ${newRel.releaseNotes}")
                                                    }
                                                )
                                            }
                                        )
                                    }

                                    AdminTab.SETTINGS -> {
                                        AdminComprehensiveSettingsTab(
                                            lang = lang,
                                            isDark = isDark,
                                            storage = storage,
                                            clientConfig = clientConfig,
                                            cardColor = cardColor,
                                            textColor = textColor,
                                            onLangChange = { newLang ->
                                                lang = newLang
                                                storage.setLanguage(newLang)
                                            },
                                            onDarkChange = { dark ->
                                                isDark = dark
                                                storage.setDarkMode(dark)
                                            },
                                            onPickBackground = { uri ->
                                                if (storage.saveBackgroundFromUri(uri)) {
                                                    customBg = storage.loadBackgroundBitmap()
                                                }
                                            },
                                            onResetBackground = {
                                                storage.clearBackground()
                                                customBg = null
                                            },
                                            onPickLogo = { uri ->
                                                if (storage.saveLogoFromUri(uri)) {
                                                    customLogo = storage.loadLogoBitmap()
                                                }
                                            },
                                            onSaveClientConfig = { newCfg ->
                                                clientConfig = newCfg
                                                storage.saveClientControlConfig(newCfg)
                                                scope.launch {
                                                    storage.pushSyncToCloud()
                                                    snackbarHostState.showSnackbar(
                                                        tr(lang, "ھېسابات ئەپى تەڭشەكلىرى يوللاندى!", "تم حفظ وإرسال إعدادات تطبيق الحسابات!")
                                                    )
                                                }
                                            },
                                            onClearAllAccounts = {
                                                storage.clearAllData()
                                                accounts = emptyList()
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        tr(lang, "بارلىق ھېساباتلار پاكىز تازىلاندى!", "تم مسح جميع الحسابات!")
                                                    )
                                                }
                                            },
                                            onShowNotice = { msg ->
                                                scope.launch { snackbarHostState.showSnackbar(msg) }
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
}

/**
 * مەخپىي نومۇر ئەسكەرتىشى پۈتۈنلەي چىقىرىۋېتىلگەن كىرىش ئېكرانى
 */
@Composable
fun AdminCleanLoginScreen(
    lang: AdminLanguage,
    cardColor: Color,
    textColor: Color,
    customLogo: ImageBitmap?,
    onSwitchLang: (AdminLanguage) -> Unit,
    onVerify: (String) -> Boolean
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 430.dp),
            shape = RoundedCornerShape(30.dp),
            color = cardColor,
            border = BorderStroke(1.8.dp, WolfGold)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AdminLanguage.entries.forEach { l ->
                        FilterChip(
                            selected = lang == l,
                            onClick = { onSwitchLang(l) },
                            label = { Text(l.title) },
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                AdminBrandLogo(customLogo = customLogo, sizeDp = 88)

                Text(
                    text = tr(lang, "باشقۇرغۇچى مەركىزى", "مركز تحكم المسؤول"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor
                )

                Text(
                    text = ADMIN_MASTER_EMAIL,
                    style = MaterialTheme.typography.labelMedium,
                    color = WolfGold,
                    fontWeight = FontWeight.Bold
                )

                // ھېچقانداق مەخپىي نومۇر ئەسكەرتىشى يوق!
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorText = null
                    },
                    label = { Text(tr(lang, "باشقۇرغۇچى مەخپىي نومۇرى", "كلمة مرور المسؤول")) },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = WolfGold) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText != null) {
                    Text(
                        text = errorText.orEmpty(),
                        color = AdminRose,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        if (!onVerify(password)) {
                            errorText = tr(lang, "مەخپىي نومۇر خاتا!", "كلمة المرور غير صحيحة!")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WolfGold),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.Login, contentDescription = null, tint = WolfBlack)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr(lang, "دەلىللەپ كىرىش", "تسجيل الدخول"),
                        color = WolfBlack,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * باش بەت: پەقەت «VIP ئەزالار» ۋە «ھېسابات ئاچقانلار» سىنبەلگىلىك كارتىسى + ئىزدەش رامكىسى
 * (قەرز تۆلىگەنلەر ۋە ئايلىق ئاچقۇزغانلار چىقىرىۋېتىلدى)
 */
@Composable
fun AdminCleanHomeTab(
    lang: AdminLanguage,
    accounts: List<ManagedUserAccount>,
    searchQuery: String,
    cardColor: Color,
    textColor: Color,
    onSearchChange: (String) -> Unit,
    onTriggerSearch: () -> Unit,
    onOpenVipMembers: () -> Unit,
    onOpenAllAccounts: () -> Unit,
    onNavigateTab: (AdminTab) -> Unit
) {
    val vipCount = accounts.count { it.isVipActive() }
    val totalOpenedCount = accounts.size

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 84.dp)
    ) {
        // ئىزدەش رامكىسى
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardColor,
                border = BorderStroke(1.dp, WolfGold.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = {
                            Text(tr(lang, "Gmail، ئىسىم ياكى ھېسابات نامى بويىچە ئىزدەش...", "البحث عبر Gmail أو الاسم أو الحساب..."))
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = WolfGold) },
                        trailingIcon = {
                            Button(
                                onClick = onTriggerSearch,
                                colors = ButtonDefaults.buttonColors(containerColor = WolfGold),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(tr(lang, "ئىزدەش", "بحث"), color = WolfBlack, fontWeight = FontWeight.ExtraBold)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // سىنبەلگىلىك 2 ئاساسىي ھېسابات تىزىملىك كارتىسى: VIP ئەزالار ۋە ھېسابات ئاچقانلار
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeSymbolCard(
                    title = tr(lang, "VIP ئەزالار", "أعضاء VIP"),
                    subtitle = tr(lang, "چېكىپ تىزىملىكنى كۆرۈش", "اضغط لعرض القائمة"),
                    count = vipCount,
                    icon = Icons.Filled.WorkspacePremium,
                    accent = WolfGold,
                    cardColor = cardColor,
                    textColor = textColor,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenVipMembers
                )
                HomeSymbolCard(
                    title = tr(lang, "ھېسابات ئاچقانلار", "الحسابات المفتوحة"),
                    subtitle = tr(lang, "چېكىپ تىزىملىكنى كۆرۈش", "اضغط لعرض القائمة"),
                    count = totalOpenedCount,
                    icon = Icons.Filled.VerifiedUser,
                    accent = AdminTurquoise,
                    cardColor = cardColor,
                    textColor = textColor,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenAllAccounts
                )
            }
        }

        // تېز باشقۇرۇش كارتىلىرى (ھېسابات ئېچىپ بېرىش ۋە يېڭى نەشر يوللاش)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeSymbolCard(
                    title = tr(lang, "يېڭى ھېسابات ئېچىپ بېرىش", "فتح حساب جديد"),
                    subtitle = tr(lang, "Gmail / ئىسىم / ھېسابات", "Gmail / الاسم / المعرف"),
                    count = null,
                    icon = Icons.Filled.PersonAdd,
                    accent = AdminEmerald,
                    cardColor = cardColor,
                    textColor = textColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(AdminTab.OPEN_ACCOUNT) }
                )
                HomeSymbolCard(
                    title = tr(lang, "ئەپ يېڭى نەشرىنى يوللاش", "إرسال تحديث التطبيق"),
                    subtitle = tr(lang, "بارلىق ھېسابات ئەپلىرىگە", "إلى جميع تطبيقات الحسابات"),
                    count = null,
                    icon = Icons.Filled.SystemUpdate,
                    accent = AdminIndigo,
                    cardColor = cardColor,
                    textColor = textColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(AdminTab.UPDATE_PUSH) }
                )
            }
        }
    }
}

@Composable
fun HomeSymbolCard(
    title: String,
    subtitle: String,
    count: Int?,
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
            modifier = Modifier.padding(vertical = 22.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(34.dp))
            }
            if (count != null) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 2-بۆلۈم: باشقىلارغا ھېسابات ئېچىپ بېرىش مەركىزى
 * (1. Gmail ئارقىلىق ھېسابات ئېچىپ بېرىش / 2. ئىسىم ئارقىلىق ھېسابات ئېچىپ بېرىش / 3. ھېسابات نامى ئارقىلىق ئېچىپ بېرىش)
 */
@Composable
fun AdminOpenAccountTab(
    lang: AdminLanguage,
    storage: AdminStorageEngine,
    accounts: List<ManagedUserAccount>,
    cardColor: Color,
    textColor: Color,
    onAccountCreated: (ManagedUserAccount, Boolean) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(AccountOpenMethod.BY_GMAIL) }
    var gmailInput by remember { mutableStateOf("") }
    var fullNameInput by remember { mutableStateOf("") }
    var accountIdInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }
    var grantVip by remember { mutableStateOf(false) }
    var vipDays by remember { mutableIntStateOf(30) }

    val primaryIdentifier = when (selectedMethod) {
        AccountOpenMethod.BY_GMAIL -> gmailInput.trim()
        AccountOpenMethod.BY_NAME -> fullNameInput.trim()
        AccountOpenMethod.BY_ACCOUNT_ID -> accountIdInput.trim()
    }

    val liveGeneratedCode = remember(primaryIdentifier, grantVip) {
        storage.generateVerificationCode(primaryIdentifier, grantVip)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 84.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardColor,
                border = BorderStroke(1.5.dp, WolfGold.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = tr(lang, "باشقىلارغا ھېسابات ئېچىپ بېرىش تىزىملىكى", "قائمة فتح حساب للآخرين"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )

                    // 3 خىل ئېنىق ھېسابات ئېچىش ئۇسۇلى
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccountOpenMethod.entries.forEach { method ->
                            val isSelected = selectedMethod == method
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) WolfGold.copy(alpha = 0.22f) else Color.Transparent,
                                border = BorderStroke(1.2.dp, if (isSelected) WolfGold else Color.Gray.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMethod = method }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(method.icon, contentDescription = null, tint = if (isSelected) WolfGold else AdminTurquoise)
                                    Text(
                                        text = tr(lang, method.ugLabel, method.arLabel),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = WolfGold)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = WolfGold.copy(alpha = 0.25f))

                    when (selectedMethod) {
                        AccountOpenMethod.BY_GMAIL -> {
                            OutlinedTextField(
                                value = gmailInput,
                                onValueChange = { gmailInput = it },
                                label = { Text(tr(lang, "خېرىدارنىڭ Gmail ئادرېسى *", "عنوان Gmail للعميل *")) },
                                placeholder = { Text("user@gmail.com") },
                                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = WolfGold) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = fullNameInput,
                                onValueChange = { fullNameInput = it },
                                label = { Text(tr(lang, "خېرىدارنىڭ ئىسمى (ئىختىيارىي)", "اسم العميل (اختياري)")) },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = AdminTurquoise) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        AccountOpenMethod.BY_NAME -> {
                            OutlinedTextField(
                                value = fullNameInput,
                                onValueChange = { fullNameInput = it },
                                label = { Text(tr(lang, "خېرىدارنىڭ تولۇق ئىسمى *", "الاسم الكامل للعميل *")) },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = WolfGold) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = gmailInput,
                                onValueChange = { gmailInput = it },
                                label = { Text(tr(lang, "ئالاقىلىشىش Gmail ئادرېسى", "عنوان Gmail للتواصل")) },
                                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = AdminTurquoise) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        AccountOpenMethod.BY_ACCOUNT_ID -> {
                            OutlinedTextField(
                                value = accountIdInput,
                                onValueChange = { accountIdInput = it },
                                label = { Text(tr(lang, "مەخسۇس ھېسابات نامى (ID) *", "اسم/معرف الحساب *")) },
                                leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = WolfGold) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = fullNameInput,
                                onValueChange = { fullNameInput = it },
                                label = { Text(tr(lang, "خېرىدارنىڭ ئىسمى", "اسم العميل")) },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = AdminTurquoise) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = gmailInput,
                                onValueChange = { gmailInput = it },
                                label = { Text(tr(lang, "خېرىدارنىڭ Gmail ئادرېسى", "عنوان Gmail للعميل")) },
                                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = AdminEmerald) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(tr(lang, "ھېسابات مەخپىي نومۇرى *", "كلمة مرور الحساب *")) },
                        leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null, tint = AdminEmerald) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        label = { Text(tr(lang, "ئورنى / ئادرېسى", "الموقع / العنوان")) },
                        leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = AdminRose) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(lang, "VIP ھوقۇقى بىلەن ئېچىش (كودسىز بىۋاسىتە كىرىدۇ)", "فتح كعضو VIP (دخول مباشر بدون رمز)"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Switch(checked = grantVip, onCheckedChange = { grantVip = it })
                    }

                    if (liveGeneratedCode.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AdminEmerald.copy(alpha = 0.15f),
                            border = BorderStroke(1.2.dp, AdminEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = tr(lang, "بۇ ھېساباتنىڭ مەخسۇس دەلىللەش كودى:", "رمز التفعيل الخاص بهذا الحساب:"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor
                                )
                                Text(
                                    text = liveGeneratedCode,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AdminEmerald
                                )
                            }
                        }
                    }

                    val createRecord: (Boolean) -> Unit = { sendMail ->
                        if (primaryIdentifier.isNotBlank()) {
                            val today = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                            val finalUsername = when (selectedMethod) {
                                AccountOpenMethod.BY_GMAIL -> gmailInput.trim()
                                AccountOpenMethod.BY_NAME -> fullNameInput.trim()
                                AccountOpenMethod.BY_ACCOUNT_ID -> accountIdInput.trim()
                            }
                            val finalGmail = if (gmailInput.contains("@")) gmailInput.trim() else "$finalUsername@gmail.com"
                            val newAcc = ManagedUserAccount(
                                id = UUID.randomUUID().toString(),
                                openMethod = selectedMethod.name,
                                fullName = fullNameInput.trim().ifBlank { finalUsername },
                                username = finalUsername,
                                gmail = finalGmail,
                                password = passwordInput.trim().ifBlank { "123456" },
                                address = addressInput.trim(),
                                licenseCode = liveGeneratedCode,
                                isVip = grantVip,
                                vipExpireEpochMillis = if (grantVip) System.currentTimeMillis() + vipDays.toLong() * 24L * 3600L * 1000L else 0L,
                                status = if (grantVip) "VIP" else "ACTIVE",
                                createdAt = today
                            )
                            onAccountCreated(newAcc, sendMail)
                            gmailInput = ""
                            fullNameInput = ""
                            accountIdInput = ""
                            passwordInput = ""
                            addressInput = ""
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { createRecord(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = AdminEmerald),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr(lang, "ھېسابات ئېچىش", "فتح الحساب"), color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { createRecord(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = WolfGold),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = WolfBlack)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr(lang, "ئېچىپ Gmail غا يوللاش", "فتح وإرسال لـ Gmail"), color = WolfBlack, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3-بۆلۈم: VIP ئەزالار ۋە مۇددەت بەلگىلەش مەركىزى
 */
@Composable
fun AdminVipDurationTab(
    lang: AdminLanguage,
    accounts: List<ManagedUserAccount>,
    storage: AdminStorageEngine,
    cardColor: Color,
    textColor: Color,
    onUpdateVip: (ManagedUserAccount, Int) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 84.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardColor,
                border = BorderStroke(1.2.dp, WolfGold)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = WolfGold)
                        Text(
                            text = tr(lang, "VIP ئەزالار ۋە مۇددەت باشقۇرۇش", "إدارة أعضاء VIP والمدة"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                    }
                    Text(
                        text = tr(
                            lang,
                            "VIP ئېچىپ بېرىلگەن ھېساباتلار دەلىللەش كودى ئىشلەتمەي بىۋاسىتە ئەپكە كىرەلەيدۇ:",
                            "الحسابات التي تملك صلاحية VIP يمكنها الدخول مباشرة دون رمز تفعيل:"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.75f)
                    )
                }
            }
        }

        if (accounts.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = tr(lang, "ھازىرچە ئېچىلغان ھېسابات يوق. «ھېسابات ئېچىش» بۆلۈمىدىن قوشۇڭ.", "لا توجد حسابات حالياً. أضف حساباً من قسم فتح حساب."),
                        modifier = Modifier.padding(28.dp),
                        textAlign = TextAlign.Center,
                        color = textColor
                    )
                }
            }
        } else {
            items(accounts, key = { it.id }) { acc ->
                val isVip = acc.isVipActive()
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardColor,
                    border = BorderStroke(1.2.dp, if (isVip) WolfGold else Color.Gray.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(acc.fullName, fontWeight = FontWeight.ExtraBold, color = textColor)
                                Text(acc.gmail, style = MaterialTheme.typography.labelSmall, color = AdminTurquoise)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isVip) WolfGold else Color.Gray.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (isVip) "VIP (${acc.vipRemainingDays()} ${tr(lang, "كۈن", "يوم")})" else tr(lang, "ئادەتتىكى", "عادي"),
                                    color = if (isVip) WolfBlack else textColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                7 to tr(lang, "7 كۈن", "7 أيام"),
                                30 to tr(lang, "1 ئاي", "شهر واحد"),
                                90 to tr(lang, "3 ئاي", "3 أشهر"),
                                180 to tr(lang, "6 ئاي", "6 أشهر"),
                                365 to tr(lang, "1 يىل", "سنة واحدة")
                            ).forEach { (days, label) ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onUpdateVip(acc, days) },
                                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, tint = WolfGold, modifier = Modifier.size(15.dp)) },
                                    label = { Text(label) }
                                )
                            }
                            if (isVip) {
                                FilterChip(
                                    selected = false,
                                    onClick = { onUpdateVip(acc, 0) },
                                    label = { Text(tr(lang, "VIP توختىتىش", "إلغاء VIP"), color = AdminRose) }
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
 * 4-بۆلۈم: ئەپنىڭ يېڭى نەشرىنى ھەر بىر ھېسابات ئەپىگە يوللاش مەركىزى
 */
@Composable
fun AdminVersionPushTab(
    lang: AdminLanguage,
    accounts: List<ManagedUserAccount>,
    latestRelease: AppVersionRelease,
    cardColor: Color,
    textColor: Color,
    onPublishUpdate: (AppVersionRelease) -> Unit
) {
    var versionName by remember { mutableStateOf(latestRelease.versionName) }
    var apkUrl by remember { mutableStateOf(latestRelease.apkDownloadUrl) }
    var notes by remember { mutableStateOf(latestRelease.releaseNotes) }
    var forceUpdate by remember { mutableStateOf(latestRelease.forceUpdate) }
    var targetAccount by remember { mutableStateOf(latestRelease.targetAccount) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 84.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardColor,
                border = BorderStroke(1.5.dp, AdminIndigo.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = WolfGold, modifier = Modifier.size(28.dp))
                        Text(
                            text = tr(lang, "ئەپنىڭ يېڭى نەشرىنى ھېسابات ئەپلىرىگە يوللاش", "إرسال الإصدار الجديد لتطبيقات الحسابات"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                    }

                    OutlinedTextField(
                        value = versionName,
                        onValueChange = { versionName = it },
                        label = { Text(tr(lang, "يېڭى نەشر نومۇرى (مەسىلەن: 2.0.0) *", "رقم الإصدار الجديد *")) },
                        leadingIcon = { Icon(Icons.Filled.Verified, contentDescription = null, tint = WolfGold) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = apkUrl,
                        onValueChange = { apkUrl = it },
                        label = { Text(tr(lang, "يېڭى APK چۈشۈرۈش ئادرېسى (URL) *", "رابط تحميل APK الجديد *")) },
                        placeholder = { Text("https://github.com/.../Hesabat-Client.apk") },
                        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null, tint = AdminTurquoise) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(tr(lang, "يېڭى نەشر چۈشەندۈرۈشى", "ملاحظات الإصدار الجديد")) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = targetAccount,
                        onValueChange = { targetAccount = it },
                        label = { Text(tr(lang, "يوللىنىدىغان ھېسابات (ALL = بارلىق ھېساباتلار)", "الحساب المستهدف (ALL = الجميع)")) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(lang, "مەجبۇرىي يېڭىلاش (يېڭىلىمىسا ئەپ ئېچىلمايدۇ)", "تحديث إجباري لجميع الحسابات"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Switch(checked = forceUpdate, onCheckedChange = { forceUpdate = it })
                    }

                    Button(
                        onClick = {
                            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                            onPublishUpdate(
                                AppVersionRelease(
                                    versionName = versionName.trim().ifBlank { "2.0.0" },
                                    apkDownloadUrl = apkUrl.trim(),
                                    releaseNotes = notes.trim(),
                                    forceUpdate = forceUpdate,
                                    targetAccount = targetAccount.trim().ifBlank { "ALL" },
                                    publishedAt = now
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WolfGold),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = WolfBlack)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr(lang, "ھازىر بارلىق ھېسابات ئەپلىرىگە يوللاش", "إرسال التحديث لجميع الحسابات الآن"),
                            color = WolfBlack,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 5-بۆلۈم: تەڭشەكلەر (تىل، تەگلىك، كېچە-كۈندۈزلۈك تەڭشەك، ھېسابات ئەپىنى باشقۇرۇش تەڭشەكلىرى)
 */
@Composable
fun AdminComprehensiveSettingsTab(
    lang: AdminLanguage,
    isDark: Boolean,
    storage: AdminStorageEngine,
    clientConfig: ClientAppControlConfig,
    cardColor: Color,
    textColor: Color,
    onLangChange: (AdminLanguage) -> Unit,
    onDarkChange: (Boolean) -> Unit,
    onPickBackground: (Uri) -> Unit,
    onResetBackground: () -> Unit,
    onPickLogo: (Uri) -> Unit,
    onSaveClientConfig: (ClientAppControlConfig) -> Unit,
    onClearAllAccounts: () -> Unit,
    onShowNotice: (String) -> Unit
) {
    var allowReg by remember { mutableStateOf(clientConfig.allowNewRegistration) }
    var reqCode by remember { mutableStateOf(clientConfig.requireVerificationCode) }
    var remoteLock by remember { mutableStateOf(clientConfig.remoteMaintenanceLock) }
    var bannerText by remember { mutableStateOf(clientConfig.announcementBanner) }
    var cloudUrl by remember { mutableStateOf(storage.getCloudSyncUrl()) }
    var newAdminPwd by remember { mutableStateOf("") }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPickBackground(uri)
    }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPickLogo(uri)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 84.dp)
    ) {
        // 1. تىل ۋە كېچە-كۈندۈزلۈك تەڭشەك
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = cardColor, border = BorderStroke(1.dp, WolfGold.copy(alpha = 0.4f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(lang, "تىل ۋە كېچە-كۈندۈزلۈك كۆرۈنۈش", "اللغة والوضع الليلي/النهاري"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        AdminLanguage.entries.forEach { l ->
                            FilterChip(
                                selected = lang == l,
                                onClick = { onLangChange(l) },
                                label = { Text(l.title) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr(lang, "كېچىلىك قاراڭغۇ ھالەت (Dark Mode)", "الوضع الليلي الداكن"), color = textColor, fontWeight = FontWeight.Bold)
                        Switch(checked = isDark, onCheckedChange = onDarkChange)
                    }
                }
            }
        }

        // 2. تەگلىك ۋە لوگو رەسىم تەڭشىكى
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = cardColor, border = BorderStroke(1.dp, AdminTurquoise.copy(alpha = 0.4f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(lang, "تەگلىك ۋە لوگو رەسىم تەڭشىكى", "إعدادات الخلفية والشعار"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                    Button(
                        onClick = { bgPicker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = AdminIndigo),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr(lang, "تېلېفوندىن تەگلىك رەسىم تاللاش", "اختيار صورة خلفية من الهاتف"), color = Color.White)
                    }
                    OutlinedButton(
                        onClick = onResetBackground,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr(lang, "ئەسلى تەگلىككە قايتۇرۇش", "استعادة الخلفية الأصلية"))
                    }
                    Button(
                        onClick = { logoPicker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = WolfGold),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = WolfBlack)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr(lang, "لوگو رەسىمىنى گالېرېيەدىن تاللاش", "اختيار صورة الشعار من المعرض"), color = WolfBlack, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // 3. ھېسابات ئەپىنى باشقۇرۇش تەڭشەكلىرى
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = cardColor, border = BorderStroke(1.2.dp, AdminEmerald.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tr(lang, "ھېسابات ئەپىنى يىراقتىن باشقۇرۇش تەڭشەكلىرى", "إعدادات التحكم بتطبيق الحسابات"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(lang, "يېڭى ھېسابات تىزىملىتىشقا رۇخسەت", "السماح بتسجيل حسابات جديدة"), color = textColor)
                        Switch(checked = allowReg, onCheckedChange = { allowReg = it })
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(lang, "دەلىللەش كودى مەجبۇرىي بولسۇن", "إلزامية رمز التفعيل"), color = textColor)
                        Switch(checked = reqCode, onCheckedChange = { reqCode = it })
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(lang, "ھېسابات ئەپىنى يىراقتىن قۇلۇپلاش", "قفل تطبيق الحسابات عن بُعد"), color = AdminRose, fontWeight = FontWeight.Bold)
                        Switch(checked = remoteLock, onCheckedChange = { remoteLock = it })
                    }

                    OutlinedTextField(
                        value = bannerText,
                        onValueChange = { bannerText = it },
                        label = { Text(tr(lang, "ھېسابات ئەپى باش بېتىدىكى ئۇقتۇرۇش خېتى", "رسالة الإعلان في تطبيق الحسابات")) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = cloudUrl,
                        onValueChange = {
                            cloudUrl = it
                            storage.setCloudSyncUrl(it)
                        },
                        label = { Text(tr(lang, "بۇلۇت باغلىنىش ئادرېسى (Cloud Sync URL)", "رابط المزامنة السحابية")) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onSaveClientConfig(
                                ClientAppControlConfig(
                                    allowNewRegistration = allowReg,
                                    requireVerificationCode = reqCode,
                                    remoteMaintenanceLock = remoteLock,
                                    announcementBanner = bannerText
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AdminEmerald),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr(lang, "ھېسابات ئەپى تەڭشەكلىرىنى ساقلاش ۋە يوللاش", "حفظ وإرسال إعدادات تطبيق الحسابات"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. باشقۇرغۇچى مەخپىي نومۇرىنى يېڭىلاش ۋە تازىلاش
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = cardColor, border = BorderStroke(1.dp, AdminRose.copy(alpha = 0.4f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newAdminPwd,
                        onValueChange = { newAdminPwd = it },
                        label = { Text(tr(lang, "يېڭى باشقۇرغۇچى مەخپىي نومۇرى", "كلمة مرور المسؤول الجديدة")) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (storage.updateAdminPassword(newAdminPwd)) {
                                newAdminPwd = ""
                                onShowNotice(tr(lang, "مەخپىي نومۇر يېڭىلاندى!", "تم تحديث كلمة المرور!"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WolfGold),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr(lang, "مەخپىي نومۇرنى يېڭىلاش", "تحديث كلمة المرور"), color = WolfBlack, fontWeight = FontWeight.ExtraBold)
                    }

                    Button(
                        onClick = onClearAllAccounts,
                        colors = ButtonDefaults.buttonColors(containerColor = AdminRose),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr(lang, "ئەپ ئىچىدىكى بارلىق ھېساباتلارنى تازىلاش", "مسح جميع الحسابات في التطبيق"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * باش بەتتىكى «VIP ئەزالار» ياكى «ھېسابات ئاچقانلار» سىنبەلگىسىنى چەككەندە ئېچىلىدىغان مەخسۇس بەت
 */
@Composable
fun HomeFilteredAccountsPage(
    lang: AdminLanguage,
    filter: HomeAccountListFilter,
    accounts: List<ManagedUserAccount>,
    searchQuery: String,
    cardColor: Color,
    textColor: Color,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    onCopyCode: (String) -> Unit,
    onSendGmail: (ManagedUserAccount) -> Unit,
    onDelete: (ManagedUserAccount) -> Unit
) {
    val q = searchQuery.trim().lowercase()
    val list = remember(accounts, filter, q) {
        val base = if (filter == HomeAccountListFilter.VIP_MEMBERS) {
            accounts.filter { it.isVipActive() }
        } else {
            accounts
        }
        if (q.isEmpty()) base
        else base.filter {
            it.fullName.lowercase().contains(q) ||
                it.username.lowercase().contains(q) ||
                it.gmail.lowercase().contains(q) ||
                it.address.lowercase().contains(q) ||
                it.licenseCode.lowercase().contains(q)
        }
    }

    val title = if (filter == HomeAccountListFilter.VIP_MEMBERS) {
        tr(lang, "VIP ئەزالار تىزىملىكى (${list.size})", "قائمة أعضاء VIP (${list.size})")
    } else {
        tr(lang, "ھېسابات ئاچقانلار تىزىملىكى (${list.size})", "قائمة الحسابات المفتوحة (${list.size})")
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
                Surface(shape = RoundedCornerShape(22.dp), color = cardColor, border = BorderStroke(1.2.dp, WolfGold)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = WolfGold),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = WolfBlack)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(tr(lang, "قايتىش", "رجوع"), color = WolfBlack, fontWeight = FontWeight.Bold)
                            }
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = textColor)
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = { Text(tr(lang, "ئىسىم، Gmail ياكى ئورۇن ئىزدەش...", "بحث بالاسم أو Gmail أو الموقع...")) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = WolfGold) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (list.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = tr(lang, "تېخى ھېسابات قوشۇلمىغان", "لا توجد حسابات مضافة بعد"),
                            modifier = Modifier.padding(32.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            } else {
                items(list, key = { it.id }) { acc ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cardColor,
                        border = BorderStroke(1.dp, if (acc.isVipActive()) WolfGold else AdminTurquoise.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(acc.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = textColor)
                                    Text("Gmail: ${acc.gmail}", style = MaterialTheme.typography.bodySmall, color = AdminTurquoise)
                                    Text("${tr(lang, "ھېسابات نامى", "المعرف")}: ${acc.username} | ${tr(lang, "مەخپىي نومۇر", "كلمة المرور")}: ${acc.password}", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                                    if (acc.address.isNotBlank()) {
                                        Text("${tr(lang, "ئادرېس", "العنوان")}: ${acc.address}", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
                                    }
                                }
                                IconButton(onClick = { onDelete(acc) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = AdminRose)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AdminEmerald.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(acc.licenseCode, fontWeight = FontWeight.ExtraBold, color = AdminEmerald)
                                    Row {
                                        IconButton(onClick = { onCopyCode(acc.licenseCode) }) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = WolfGold)
                                        }
                                        IconButton(onClick = { onSendGmail(acc) }) {
                                            Icon(Icons.Filled.Email, contentDescription = null, tint = AdminTurquoise)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
