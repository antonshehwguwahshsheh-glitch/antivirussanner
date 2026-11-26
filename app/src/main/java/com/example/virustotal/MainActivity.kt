package com.example.virustotal

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private var apiKey: String = ""
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var apiKeyInput: EditText
    private val client = OkHttpClient()

    private var coins: Long = 0
    private var clickPower: Long = 1
    private var autoIncome: Long = 0
    private var itemLevels = IntArray(6) { 0 }
    
    private lateinit var coinText: TextView
    private lateinit var clickPowerText: TextView
    private lateinit var autoIncomeText: TextView
    private lateinit var clickButton: Button
    private var timerRunning = false

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

    private fun applyGtaBackground(layout: LinearLayout) {
        try {
            val resId = resources.getIdentifier("gta_bg", "drawable", packageName)
            if (resId != 0) {
                layout.setBackgroundResource(resId)
                val overlay = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.parseColor("#AA1A0033"), Color.parseColor("#EE000000")) 
                )
                layout.background = android.graphics.drawable.LayerDrawable(arrayOf(layout.background, overlay))
            } else {
                layout.background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.parseColor("#FF007F"), Color.parseColor("#7F00FF"), Color.parseColor("#1A0033"))
                )
            }
        } catch (e: Exception) {
            layout.setBackgroundColor(Color.parseColor("#121212"))
        }
    }

    private fun createRainbowButton(text: String, onClick: () -> Unit): Button {
        val button = Button(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#FF0000"), Color.parseColor("#FF7F00"),
                    Color.parseColor("#FFFF00"), Color.parseColor("#00FF00"),
                    Color.parseColor("#0000FF"), Color.parseColor("#4B0082"),
                    Color.parseColor("#9400D3")
                )
            ).apply { cornerRadius = 30f }
        }
        button.setOnClickListener { onClick() }
        return button
    }

    private fun openTelegramChannel() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/tgk_macaca_soft")))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open Telegram link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMainMenu() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        applyGtaBackground(layout)

        val logoView = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            layoutParams = LinearLayout.LayoutParams(250, 250)
        }

        val title = TextView(this).apply {
            text = "VirusTotal"
            textSize = 36f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FF00FF"))
            setShadowLayer(10f, 0f, 0f, Color.parseColor("#FF00FF"))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 48)
        }

        val scanButton = Button(this).apply {
            text = "🔍 ANTIVIRUS SCANNER"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#3B82F6"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { setMargins(0, 0, 0, 24) }
        }

        val gameButton = Button(this).apply {
            text = "💰 COIN CLICKER MINI-GAME"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#F59E0B"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { setMargins(0, 0, 0, 24) }
        }

        val tgButton = createRainbowButton("📢 MY TELEGRAM CHANNEL") { openTelegramChannel() }

        layout.addView(logoView)
        layout.addView(title)
        layout.addView(scanButton)
        layout.addView(gameButton)
        layout.addView(tgButton)
        setContentView(layout)

        scanButton.setOnClickListener { showScannerScreen() }
        gameButton.setOnClickListener { showGameScreen() }
    }

    private fun showScannerScreen() {
        val prefs = getSharedPreferences("VirusTotalPrefs", Context.MODE_PRIVATE)
        apiKey = prefs.getString("api_key", "") ?: ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        applyGtaBackground(layout)

        val backBtn = Button(this).apply {
            text = "← BACK"
            textSize = 16f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            gravity = Gravity.START
        }
        backBtn.setOnClickListener { showMainMenu() }

        val title = TextView(this).apply { 
            text = "Antivirus Scanner"
            textSize = 24f
            setTextColor(Color.WHITE)
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
            setPadding(0, 16, 0, 16) 
        }

        apiKeyInput = EditText(this).apply {
            hint = "Enter API Key"
            setText(apiKey)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val saveKeyButton = Button(this).apply { text = "SAVE KEY"; setBackgroundColor(Color.GRAY); setTextColor(Color.WHITE) }
        val scanFileButton = Button(this).apply { text = "SELECT & SCAN FILE"; setBackgroundColor(Color.parseColor("#3B82F6")); setTextColor(Color.WHITE) }

        progressBar = ProgressBar(this).apply { visibility = View.GONE }
        resultText = TextView(this).apply { 
            text = "Ready to scan..."
            setTextColor(Color.LTGRAY)
            setShadowLayer(5f, 0f, 0f, Color.BLACK)
            setPadding(0, 16, 0, 16) 
        }

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

    private fun showGameScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 48, 48, 48)
        }
        applyGtaBackground(layout)

        val backBtn = Button(this).apply {
            text = "← BACK TO MENU"
            textSize = 16f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            gravity = Gravity.START
        }
        backBtn.setOnClickListener { showMainMenu() }

        val title = TextView(this).apply { 
            text = "💰 COIN CLICKER"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FF00FF"))
            setShadowLayer(10f, 0f, 0f, Color.parseColor("#FF00FF"))
            setPadding(0, 16, 0, 32) 
        }

        coinText = TextView(this).apply { text = "Coins: $coins"; textSize = 32f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setShadowLayer(5f, 0f, 0f, Color.BLACK) }
        clickPowerText = TextView(this).apply { text = "Tap Power: +$clickPower"; textSize = 16f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0, 8, 0, 8) }
        autoIncomeText = TextView(this).apply { text = "Auto Income: +$autoIncome / 5 min"; textSize = 16f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0, 0, 0, 32) }

        clickButton = Button(this).apply {
            text = "TAP ME!"
            textSize = 24f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(400, 400)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#10B981"))
            }
        }

        val shopButton = Button(this).apply {
            text = "🛒 OPEN SHOP"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#8B5CF6"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 48, 0, 24) }
        }

        val tgButton = createRainbowButton("📢 MY TELEGRAM CHANNEL") { openTelegramChannel() }

        layout.addView(backBtn)
        layout.addView(title)
        layout.addView(coinText)
        layout.addView(clickPowerText)
        layout.addView(autoIncomeText)
        layout.addView(clickButton)
        layout.addView(shopButton)
        layout.addView(tgButton)
        setContentView(layout)

        clickButton.setOnClickListener {
            coins += clickPower
            updateGameUI()
            saveGameData()
        }

        shopButton.setOnClickListener { showShopScreen() }
        startAutoIncomeTimer()
    }

    private fun showShopScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        applyGtaBackground(layout)

        val backBtn = Button(this).apply {
            text = "← BACK TO GAME"
            textSize = 16f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            gravity = Gravity.START
        }
        backBtn.setOnClickListener { showGameScreen() }

        val title = TextView(this).apply { 
            text = "🛒 SHOP"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FF00FF")) 
            setShadowLayer(10f, 0f, 0f, Color.parseColor("#FF00FF"))
            setPadding(0, 16, 0, 32) 
        }

        val shopScroll = ScrollView(this)
        val shopLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val items = listOf(
            Triple("Tap Power +1", 100L, "CLICK_1"),
            Triple("Tap Power +10", 1000L, "CLICK_10"),
            Triple("Tap Power x2", 5000L, "MULTIPLY_CLICK"),
            Triple("Auto Income +20/5m", 100L, "AUTO_20"),
            Triple("Auto Income +100/5m", 1000L, "AUTO_100"),
            Triple("Auto Income x2", 5000L, "MULTIPLY_AUTO")
        )

        for ((index, item) in items.withIndex()) {
            val (name, baseCost, type) = item
            val cost = baseCost * (1L shl itemLevels[index])
            
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
                    itemLevels[index]++
                    updateGameUI()
                    saveGameData()
                    showShopScreen()
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
            "CLICK_1" -> clickPower += 1
            "CLICK_10" -> clickPower += 10
            "MULTIPLY_CLICK" -> clickPower *= 2
            "AUTO_20" -> autoIncome += 20
            "AUTO_100" -> autoIncome += 100
            "MULTIPLY_AUTO" -> autoIncome *= 2
        }
    }

    private fun startAutoIncomeTimer() {
        if (timerRunning) return
        timerRunning = true
        val interval = 300000L 
        
        object : CountDownTimer(interval, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (autoIncome > 0) {
                    coins += autoIncome
                    updateGameUI()
                    saveGameData()
                    Toast.makeText(this@MainActivity, "Auto Income: +$autoIncome coins!", Toast.LENGTH_SHORT).show()
                }
                startAutoIncomeTimer()
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
            putString("itemLevels", itemLevels.joinToString(","))
            apply()
        }
    }

    private fun loadGameData() {
        val prefs = getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        coins = prefs.getLong("coins", 0)
        clickPower = prefs.getLong("clickPower", 1)
        autoIncome = prefs.getLong("autoIncome", 0)
        
        val savedLevels = prefs.getString("itemLevels", "0,0,0,0,0,0") ?: "0,0,0,0,0,0"
        val levelsArray = savedLevels.split(",")
        for (i in itemLevels.indices) {
            if (i < levelsArray.size) {
                itemLevels[i] = levelsArray[i].toIntOrNull() ?: 0
            }
        }
    }

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
          
