package com.example.virustotal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // --- Переменные для сканера ---
    private var apiKey: String = ""
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var apiKeyInput: EditText
    private val client = OkHttpClient()

    // --- Переменные для игры ---
    private var coins: Long = 0
    private var clickPower: Long = 1
    private var autoIncome: Long = 0
    private var upgradeLevel: Int = 0
    
    private lateinit var coinText: TextView
    private lateinit var clickPowerText: TextView
    private lateinit var autoIncomeText: TextView
    private lateinit var shopButton: Button
    private lateinit var clickButton: Button
    
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunning = false

    // --- Инициализация выбора файла ---
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val tempFile = copyUriToFile(uri)
            if (tempFile != null) scanFile(tempFile) else updateUI("Error loading file", Color.RED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadGameData()
        showMainMenu()
    }

    // ==========================================
    // ЭКРАН 1: ГЛАВНОЕ МЕНЮ (Выбор)
    // ==========================================
    private fun showMainMenu() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val logoView = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            layoutParams = LinearLayout.LayoutParams(250, 250)
        }

        val title = TextView(this).apply {
            text = "VirusTotal"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 48)
        }

        val scanButton = Button(this).apply {
            text = "🔍 ANTIVIRUS SCANNER"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#3B82F6"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
        }

        val gameButton = Button(this).apply {
            text = "💰 COIN CLICKER MINI-GAME"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#F59E0B"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        layout.addView(logoView)
        layout.addView(title)
        layout.addView(scanButton)
        layout.addView(gameButton)
        setContentView(layout)

        scanButton.setOnClickListener { showScannerScreen() }
        gameButton.setOnClickListener { showGameScreen() }
    }

    // ==========================================
    // ЭКРАН 2: СКАНЕР (Твой старый интерфейс)
    // ==========================================
    private fun showScannerScreen() {
        val prefs = getSharedPreferences("VirusTotalPrefs", Context.MODE_PRIVATE)
        apiKey = prefs.getString("api_key", "") ?: ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val backBtn = Button(this).apply { text = "< BACK"; setBackgroundColor(Color.TRANSPARENT); setTextColor(Color.WHITE) }
        backBtn.setOnClickListener { showMainMenu() }

        val title = TextView(this).apply { text = "Antivirus Scanner"; textSize = 24f; setTextColor(Color.WHITE); setPadding(0, 16, 0, 16) }

        apiKeyInput = EditText(this).apply {
            hint = "Enter API Key"
            setText(apiKey)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val saveKeyButton = Button(this).apply { text = "SAVE KEY"; setBackgroundColor(Color.GRAY); setTextColor(Color.WHITE) }
        val scanFileButton = Button(this).apply { text = "SELECT & SCAN FILE"; setBackgroundColor(Color.parseColor("#3B82F6")); setTextColor(Color.WHITE) }

        progressBar = ProgressBar(this).apply { visibility = View.GONE }
        resultText = TextView(this).apply { text = "Ready to scan..."; setTextColor(Color.LTGRAY); setPadding(0, 16, 0, 16) }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(resultText)
        }

        layout.addView(backBtn)
        layout.addView(title)
        layout.addView(apiKeyInput)
        layout.addView(saveKeyButton)
        layout.addView(scanFileButton)
        layout.addView(progressBar)
        layout.addView(scrollView)
        setContentView(layout)

        saveKeyButton.setOnClickListener {
            apiKey = apiKeyInput.text.toString().trim()
            prefs.edit().putString("api_key", apiKey).apply()
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        }
        scanFileButton.setOnClickListener { if (apiKey.isNotEmpty()) getContent.launch("*/*") else Toast.makeText(this, "Enter Key", Toast.LENGTH_SHORT).show() }
    }

    // ==========================================
    // ЭКРАН 3: ИГРА КЛИКЕР
    // ==========================================
    private fun showGameScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        val backBtn = Button(this).apply { text = "< BACK TO MENU"; setBackgroundColor(Color.TRANSPARENT); setTextColor(Color.WHITE) }
        backBtn.setOnClickListener { showMainMenu() }

        val title = TextView(this).apply { text = "💰 COIN CLICKER"; textSize = 28f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#F59E0B")); setPadding(0, 16, 0, 32) }

        coinText = TextView(this).apply { text = "Coins: $coins"; textSize = 32f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        clickPowerText = TextView(this).apply { text = "Tap Power: +$clickPower"; textSize = 16f; setTextColor(Color.GRAY); gravity = Gravity.CENTER; setPadding(0, 8, 0, 8) }
        autoIncomeText = TextView(this).apply { text = "Auto Income: +$autoIncome / 5 min"; textSize = 16f; setTextColor(Color.GRAY); gravity = Gravity.CENTER; setPadding(0, 0, 0, 32) }

        clickButton = Button(this).apply {
            text = "TAP ME!"
            textSize = 24f
            setBackgroundColor(Color.parseColor("#10B981"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(400, 400) // Круглая большая кнопка
            // Делаем её овальной (эмуляция круга)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#10B981"))
            }
        }

        shopButton = Button(this).apply {
            text = "🛒 OPEN SHOP"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#8B5CF6"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 48, 0, 0) }
        }

        layout.addView(backBtn)
        layout.addView(title)
        layout.addView(coinText)
        layout.addView(clickPowerText)
        layout.addView(autoIncomeText)
        layout.addView(clickButton)
        layout.addView(shopButton)
        setContentView(layout)

        clickButton.setOnClickListener {
            coins += clickPower
            updateGameUI()
            saveGameData()
        }

        shopButton.setOnClickListener { showShopScreen() }

        startAutoIncomeTimer()
    }

    // ==========================================
    // ЭКРАН 4: МАГАЗИН
    // ==========================================
    private fun showShopScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        val backBtn = Button(this).apply { text = "< BACK TO GAME"; setBackgroundColor(Color.TRANSPARENT); setTextColor(Color.WHITE) }
        backBtn.setOnClickListener { showGameScreen() }

        val title = TextView(this).apply { text = "🛒 SHOP"; textSize = 28f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); setPadding(0, 16, 0, 32) }

        val shopScroll = ScrollView(this)
        val shopLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Генерация предметов магазина
        val items = listOf(
            Triple("Tap Power +1", 100L, "CLICK"),
            Triple("Tap Power +10", 1000L, "CLICK"),
            Triple("Tap Power x2", 5000L, "MULTIPLY_CLICK"),
            Triple("Auto Income +20/5m", 100L, "AUTO"),
            Triple("Auto Income +100/5m", 1000L, "AUTO"),
            Triple("Auto Income x2", 5000L, "MULTIPLY_AUTO")
        )

        for ((index, item) in items.withIndex()) {
            val (name, baseCost, type) = item
            // Цена растет в 2 раза за каждую покупку
            val cost = baseCost * (1L shl upgradeLevel) // 1L shl n = умножение на 2^n
            
            val itemBtn = Button(this).apply {
                text = "$name\nCost: $cost coins"
                setTextColor(Color.WHITE)
                setBackgroundColor(if (coins >= cost) Color.parseColor("#10B981") else Color.parseColor("#4B5563"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
            }

            itemBtn.setOnClickListener {
                if (coins >= cost) {
                    coins -= cost
                    applyUpgrade(type)
                    upgradeLevel++
                    updateGameUI()
                    saveGameData()
                    showShopScreen() // Обновляем экран магазина
                } else {
                    Toast.makeText(this, "Not enough coins!", Toast.LENGTH_SHORT).show()
                }
            }
            shopLayout.addView(itemBtn)
        }

        shopScroll.addView(shopLayout)
        layout.addView(backBtn)
        layout.addView(title)
        layout.addView(shopScroll)
        setContentView(layout)
    }

    private fun applyUpgrade(type: String) {
        when (type) {
            "CLICK" -> clickPower += 1
            "MULTIPLY_CLICK" -> clickPower *= 2
            "AUTO" -> autoIncome += 20
            "MULTIPLY_AUTO" -> autoIncome *= 2
        }
    }

    // ==========================================
    // ЛОГИКА ИГРЫ (Таймер и Сохранения)
    // ==========================================
    private fun startAutoIncomeTimer() {
        if (timerRunning) return
        timerRunning = true
        
        // Каждые 5 минут (300 000 мс) начисляем автодоход
        // Для теста можешь поставить 10000 (10 секунд)
        val interval = 300000L 
        
        object : CountDownTimer(interval, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Здесь можно обновлять текст "До следующего дохода: X сек"
            }
            override fun onFinish() {
                if (autoIncome > 0) {
                    coins += autoIncome
                    updateGameUI()
                    saveGameData()
                    Toast.makeText(this@MainActivity, "Auto Income: +$autoIncome coins!", Toast.LENGTH_SHORT).show()
                }
                startAutoIncomeTimer() // Запускаем заново
            }
        }.start()
    }

    private fun updateGameUI() {
        coinText.text = "Coins: $coins"
        clickPowerText.text = "Tap Power: +$clickPower"
        autoIncomeText.text = "Auto Income: +$autoIncome / 5 min"
    }

    private fun saveGameData() {
        val prefs = getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("coins", coins)
            putLong("clickPower", clickPower)
            putLong("autoIncome", autoIncome)
            putInt("upgradeLevel", upgradeLevel)
            apply()
        }
    }

    private fun loadGameData() {
        val prefs = getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        coins = prefs.getLong("coins", 0)
        clickPower = prefs.getLong("clickPower", 1)
        autoIncome = prefs.getLong("autoIncome", 0)
        upgradeLevel = prefs.getInt("upgradeLevel", 0)
    }

    // ==========================================
    // СЛУЖЕБНЫЕ ФУНКЦИИ ДЛЯ СКАНЕРА
    // ==========================================
    private fun copyUriToFile(uri: Uri): File? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val tempFile = File(cacheDir, "temp_scan_file")
            val outputStream = FileOutputStream(tempFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            tempFile
        } catch (e: Exception) { null }
    }

    private fun scanFile(file: File) {
        progressBar.visibility = View.VISIBLE
        val hash = getFileHash(file)
        if (hash == null) {
            progressBar.visibility = View.GONE
            updateUI("Error reading file", Color.RED)
            return
        }

        updateUI("Querying VirusTotal...", Color.WHITE)
        val url = "https://www.virustotal.com/api/v3/files/$hash"
        val request = Request.Builder().url(url).addHeader("x-apikey", apiKey).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { progressBar.visibility = View.GONE; updateUI("Network error", Color.RED) }
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && responseBody != null) {
                        parseResponse(responseBody)
                    } else if (response.code == 404) {
                        updateUI("File not in DB (might be clean)", Color.YELLOW)
                    } else {
                        updateUI("API Error: ${response.code}", Color.RED)
                    }
                }
            }
        })
    }

    private fun parseResponse(json: String) {
        try {
            val jsonObject = JSONObject(json)
            val attributes = jsonObject.getJSONObject("data").getJSONObject("attributes")
            val stats = attributes.getJSONObject("last_analysis_stats")
            val malicious = stats.getInt("malicious")
            val undetected = stats.getInt("undetected")
            
            val report = StringBuilder()
            report.append("Scan Results:\n\n🔴 Malicious: $malicious\n🟢 Undetected: $undetected\n\n")
            
            if (malicious > 0 && attributes.has("last_analysis_results")) {
                report.append("--- Detected Threats ---\n")
                val results = attributes.getJSONObject("last_analysis_results")
                val keys = results.keys()
                while (keys.hasNext()) {
                    val engineName = keys.next()
                    val engineData = results.getJSONObject(engineName)
                    if (engineData.optString("category") == "malicious") {
                        report.append("• $engineName: ${engineData.optString("result", "Unknown")}\n")
                    }
                }
            } else if (malicious == 0) {
                report.append("✅ No threats detected.")
            }
            updateUI(report.toString(), if (malicious > 0) Color.RED else Color.GREEN)
        } catch (e: Exception) {
            updateUI("Parsing error", Color.RED)
        }
    }

    private fun updateUI(message: String, color: Int) {
        resultText.text = message
        resultText.setTextColor(color)
    }

    private fun getFileHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { null }
    }
}
