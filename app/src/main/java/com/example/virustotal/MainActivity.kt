package com.example.virustotal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // --- ТВОИ КЛЮЧИ ОБЛАКА ---
    private val jsonBinApiKey = "$2a$10$WezU1SQqQ8VsB1HETuV14Ouwwa5OOBCaYqwqBglLxWd8Yy2nzdCdq"
    private val jsonBinId = "6b083926ab3a7b614482c5d1"

    // --- Переменные ---
    private var apiKey: String = ""
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var apiKeyInput: EditText
    private val client = OkHttpClient()

    private var coins: Long = 0
    private var clickPower: Long = 1
    private var autoIncome: Long = 0
    private var itemLevels = IntArray(6) { 0 }
    
    private var playerId: String = ""
    private var playerName: String = ""
    private var playerPass: String = ""
    private var avatarPath: String = "" 
    
    private lateinit var coinText: TextView
    private lateinit var clickPowerText: TextView
    private lateinit var autoIncomeText: TextView
    private var timerRunning = false

    private var selectedAvatarUri: Uri? = null
    private val getAvatar = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedAvatarUri = uri
            Toast.makeText(this, "Avatar selected!", Toast.LENGTH_SHORT).show()
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val tempFile = copyUriToFile(uri)
            if (tempFile != null) scanFile(tempFile) else updateUI("Error loading file", Color.RED)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        playerId = prefs.getString("playerId", "") ?: ""
        playerName = prefs.getString("playerName", "") ?: ""
        playerPass = prefs.getString("playerPass", "") ?: ""
        avatarPath = prefs.getString("avatarPath", "") ?: ""

        if (playerId.isEmpty()) {
            showLoginScreen()
        } else {
            loadGameData()
            showMainMenu()
        }
    }

    // ==========================================
    // ЭКРАН ВХОДА (avatar_girl)
    // ==========================================
    private fun showLoginScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        
        try {
            layout.setBackgroundResource(R.drawable.avatar_girl)
            val overlay = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#AA000000"), Color.parseColor("#EE000000")))
            layout.background = android.graphics.drawable.LayerDrawable(arrayOf(layout.background, overlay))
        } catch (e: Exception) {
            applyGtaBackground(layout)
        }

        val title = TextView(this).apply {
            text = "LOGIN / REGISTER"
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#00FFFF"))
            setShadowLayer(15f, 0f, 0f, Color.parseColor("#00FFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val avatarView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(200, 200)
            setImageResource(R.drawable.avatar_girl)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.GRAY)
            }
        }

        val selectAvatarBtn = Button(this).apply {
            text = "📷 SELECT AVATAR"
            setBackgroundColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 24) }
        }

        val inputName = EditText(this).apply {
            hint = "Enter Nickname"
            setTextColor(Color.parseColor("#FF00FF"))
            setHintTextColor(Color.GRAY)
            textSize = 20f
            setShadowLayer(10f, 0f, 0f, Color.parseColor("#FF00FF"))
            inputType = InputType.TYPE_CLASS_TEXT
            gravity = Gravity.CENTER
        }

        val inputPass = EditText(this).apply {
            hint = "Enter Password"
            setTextColor(Color.parseColor("#FF00FF"))
            setHintTextColor(Color.GRAY)
            textSize = 20f
            setShadowLayer(10f, 0f, 0f, Color.parseColor("#FF00FF"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        val loginBtn = Button(this).apply {
            text = "LOGIN / CREATE"
            setBackgroundColor(Color.parseColor("#10B981"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120).apply { setMargins(0, 32, 0, 0) }
        }

        layout.addView(title)
        layout.addView(avatarView)
        layout.addView(selectAvatarBtn)
        layout.addView(inputName)
        layout.addView(inputPass)
        layout.addView(loginBtn)
        setContentView(layout)

        selectAvatarBtn.setOnClickListener {
            getAvatar.launch("image/*")
        }

        loginBtn.setOnClickListener {
            val name = inputName.text.toString().trim()
            val pass = inputPass.text.toString().trim()

            if (name.length < 3 || pass.length < 3) {
                Toast.makeText(this, "Min 3 chars", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedAvatarUri != null) {
                avatarPath = copyAvatarToInternalStorage(selectedAvatarUri!!)
            } else {
                avatarPath = "default"
            }

            checkAccountInCloud(name, pass)
        }
    }

    private fun copyAvatarToInternalStorage(uri: Uri): String {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val file = File(filesDir, "avatar_${UUID.randomUUID()}.png")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    private fun checkAccountInCloud(name: String, pass: String) {
        val url = "https://api.jsonbin.io/v3/b/$jsonBinId/latest"
        val request = Request.Builder().url(url).addHeader("X-Master-Key", jsonBinApiKey).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "No internet", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                runOnUiThread {
                    var accountFound = false
                    var savedPass = ""
                    var uniqueId = ""
                    var savedAvatar = ""

                    try {
                        if (body != null) {
                            val record = JSONObject(body).getJSONObject("record")
                            if (record.has(name.uppercase())) {
                                accountFound = true
                                val userObj = record.getJSONObject(name.uppercase())
                                savedPass = userObj.getString("pass")
                                uniqueId = userObj.getString("uniqueId")
                                savedAvatar = userObj.optString("avatar", "")
                            }
                        }
                    } catch (e: Exception) {}

                    if (accountFound) {
                        if (savedPass == pass) {
                            playerName = name.uppercase()
                            playerId = uniqueId
                            playerPass = pass
                            avatarPath = savedAvatar
                            saveLoginToPrefs()
                            downloadFromCloud(playerId)
                        } else {
                            Toast.makeText(this@MainActivity, "Wrong password!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        playerName = name.uppercase()
                        playerPass = pass
                        val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                        val randomPart = (1..4).map { characters.random() }.joinToString("")
                        playerId = "MACACA-$randomPart"
                        
                        saveLoginToPrefs()
                        coins = 0
                        clickPower = 1
                        autoIncome = 0
                        itemLevels = IntArray(6) { 0 }
                        
                        uploadToCloud()
                        Toast.makeText(this@MainActivity, "Account Created!", Toast.LENGTH_SHORT).show()
                        showMainMenu()
                    }
                }
            }
        })
    }

    private fun saveLoginToPrefs() {
        val prefs = getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("playerId", playerId)
            .putString("playerName", playerName)
            .putString("playerPass", playerPass)
            .putString("avatarPath", avatarPath)
            .apply()
    }

    // ==========================================
    // ОБЛАКО
    // ==========================================
    private fun uploadToCloud() {
        val json = JSONObject().apply {
            put("coins", coins)
            put("clickPower", clickPower)
            put("autoIncome", autoIncome)
            put("itemLevels", itemLevels.joinToString(","))
            put("pass", playerPass)
            put("uniqueId", playerId)
            put("avatar", avatarPath)
        }

        val url = "https://api.jsonbin.io/v3/b/$jsonBinId/latest"
        val request = Request.Builder().url(url).addHeader("X-Master-Key", jsonBinApiKey).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                var allData = JSONObject()
                try {
                    if (body != null) allData = JSONObject(body).getJSONObject("record")
                } catch (e: Exception) {}

                allData.put(playerName, json)

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val bodyToSend = allData.toString().toRequestBody(mediaType)

                val putRequest = Request.Builder().url("https://api.jsonbin.io/v3/b/$jsonBinId")
                    .addHeader("X-Master-Key", jsonBinApiKey).put(bodyToSend).build()

                client.newCall(putRequest).enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        runOnUiThread {
                            if (response.isSuccessful) Toast.makeText(this@MainActivity, "Progress Saved!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call, e: IOException) {}
                })
            }
        })
    }

    private fun downloadFromCloud(idToLoad: String) {
        val url = "https://api.jsonbin.io/v3/b/$jsonBinId/latest"
        val request = Request.Builder().url(url).addHeader("X-Master-Key", jsonBinApiKey).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                runOnUiThread {
                    try {
                        if (body != null) {
                            val record = JSONObject(body).getJSONObject("record")
                            if (record.has(playerName)) {
                                val data = record.getJSONObject(playerName)
                                coins = data.getLong("coins")
                                clickPower = data.getLong("clickPower")
                                autoIncome = data.getLong("autoIncome")
                                avatarPath = data.optString("avatar", "")
                                
                                val levelsStr = data.getString("itemLevels")
                                val levelsArray = levelsStr.split(",")
                                for (i in itemLevels.indices) {
                                    if (i < levelsArray.size) itemLevels[i] = levelsArray[i].toIntOrNull() ?: 0
                                }
                                saveGameData()
                                Toast.makeText(this@MainActivity, "Welcome back!", Toast.LENGTH_SHORT).show()
                                showMainMenu()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Error loading data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // ==========================================
    // ГЛАВНОЕ МЕНЮ (bg_girl)
    // ==========================================
    private fun showMainMenu() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        
        try {
            layout.setBackgroundResource(R.drawable.bg_girl)
            val overlay = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#99000000"), Color.parseColor("#EE000000")))
            layout.background = android.graphics.drawable.LayerDrawable(arrayOf(layout.background, overlay))
        } catch (e: Exception) {
            applyGtaBackground(layout)
        }

        val profileBtn = Button(this).apply {
            text = "$playerName"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#4B5563"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) }
        }

        val title = TextView(this).apply {
            text = "VirusTotal"
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FF00FF"))
            setShadowLayer(10f, 0f, 0f, Color.parseColor("#FF00FF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val scanButton = Button(this).apply {
            text = "🔍 ANTIVIRUS SCANNER"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#3B82F6"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 100).apply { setMargins(0, 0, 0, 16) }
        }

        val gameButton = Button(this).apply {
            text = "💰 COIN CLICKER MINI-GAME"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#F59E0B"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 100).apply { setMargins(0, 0, 0, 16) }
        }

        val tgButton = createRainbowButton("📢 MY TELEGRAM CHANNEL") { openTelegramChannel() }

        layout.addView(profileBtn)
        layout.addView(title)
        layout.addView(scanButton)
        layout.addView(gameButton)
        layout.addView(tgButton)
        setContentView(layout)

        profileBtn.setOnClickListener { showProfileScreen() }
        scanButton.setOnClickListener { showScannerScreen() }
        gameButton.setOnClickListener { showGameScreen() }
    }

    // ==========================================
    // ПРОФИЛЬ (bg_car)
    // ==========================================
    private fun showProfileScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        
        try {
            layout.setBackgroundResource(R.drawable.bg_car)
            val overlay = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#AA000000"), Color.parseColor("#EE000000")))
            layout.background = android.graphics.drawable.LayerDrawable(arrayOf(layout.background, overlay))
        } catch (e: Exception) {
            applyGtaBackground(layout)
        }

        val backBtn = Button(this).apply {
            text = "← BACK"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
        }
        backBtn.setOnClickListener { showMainMenu() }

        val avatarView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(250, 250)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4B5563"))
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            
            if (avatarPath == "default" || avatarPath.isEmpty()) {
                setImageResource(R.drawable.avatar_girl)
            } else {
                try {
                    val imgFile = File(avatarPath)
                    if (imgFile.exists()) {
                        val myBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                        setImageBitmap(myBitmap)
                    } else {
                        setImageResource(R.drawable.avatar_girl)
                    }
                } catch (e: Exception) {
                    setImageResource(R.drawable.avatar_girl)
                }
            }
        }

        val title = TextView(this).apply {
            text = "$playerName's PROFILE"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            setShadowLayer(10f, 0f, 0f, Color.parseColor("#10B981"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 24)
        }

        val changeAvatarBtn = Button(this).apply {
            text = "CHANGE AVATAR"
            setBackgroundColor(Color.parseColor
