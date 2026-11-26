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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

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
            if (tempFile != null) {
                scanFile(tempFile)
            } else {
                updateUI("Error loading file", Color.RED)
            }
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

    private fun showLoginScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        
        try {
            layout.setBackgroundResource(R.drawable.avatar_girl)
            val topColor = Color.parseColor("#AA000000")
            val bottomColor = Color.parseColor("#EE000000")
            val overlay = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(topColor, bottomColor))
            val layers = arrayOf(layout.background, overlay)
            layout.background = android.graphics.drawable.LayerDrawable(layers)
        } catch (e: Exception) { }

        val title = TextView(this).apply {
            text = "LOGIN / REGISTER"
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            val cyanColor = Color.parseColor("#00FFFF")
            setTextColor(cyanColor)
            setShadowLayer(15f, 0f, 0f, cyanColor)
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 24) }
        }

        val inputName = EditText(this).apply {
            hint = "Enter Nickname"
            val pinkColor = Color.parseColor("#FF00FF")
            setTextColor(pinkColor)
            setHintTextColor(Color.GRAY)
            textSize = 20f
            inputType = InputType.TYPE_CLASS_TEXT
            gravity = Gravity.CENTER
        }

        val inputPass = EditText(this).apply {
            hint = "Enter Password"
            val pinkColor = Color.parseColor("#FF00FF")
            setTextColor(pinkColor)
            setHintTextColor(Color.GRAY)
            textSize = 20f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        val loginBtn = Button(this).apply {
            text = "LOGIN / CREATE"
            setBackgroundColor(Color.parseColor("#10B981"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                120
            ).apply { setMargins(0, 32, 0, 0) }
        }

        layout.addView(title)
        layout.addView(avatarView)
        layout.addView(selectAvatarBtn)
        layout.addView(inputName)
        layout.addView(inputPass)
        layout.addView(loginBtn)
        setContentView(layout)

        selectAvatarBtn.setOnClickListener { getAvatar.launch("image/*") }

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

            playerName = name.uppercase()
            playerPass = pass
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val randomPart = (1..4).map { chars.random() }.joinToString("")
            playerId = "MACACA-$randomPart"
            
            saveLoginToPrefs()
            loadGameData()
            showMainMenu()
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
        } catch (e: Exception) { "" }
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

    private fun showMainMenu() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        
        try {
            layout.setBackgroundResource(R.drawable.bg_girl)
            val topColor = Color.parseColor("#99000000")
            val bottomColor = Color.parseColor("#EE000000")
            val overlay = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(topColor, bottomColor))
            val layers = arrayOf(layout.background, overlay)
            layout.background = android.graphics.drawable.LayerDrawable(layers)
        } catch (e: Exception) { }

        val profileBtn = Button(this).apply {
            text = "$playerName"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#4B5563"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val title = TextView(this).apply {
            text = "VirusTotal"
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            val pinkColor = Color.parseColor("#FF00FF")
            setTextColor(pinkColor)
            setShadowLayer(10f, 0f, 0f, pinkColor)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val scanButton = Button(this).apply {
            text = "🔍 ANTIVIRUS SCANNER"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#3B82F6"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                100
            ).apply { setMargins(0, 0, 0, 16) }
        }

        val gameButton = Button(this).apply {
            text = "💰 COIN CLICKER MINI-GAME"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#F59E0B"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                100
            ).apply { setMargins(0, 0, 0, 16) }
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

    private fun showProfileScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        
        try {
            layout.setBackgroundResource(R.drawable.bg_car)
            val topColor = Color.parseColor("#AA000000")
            val bottomColor = Color.parseColor("#EE000000")
            val overlay = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(topColor, bottomColor))
            val layers = arrayOf(layout.background, overlay)
            layout.background = android.graphics.drawable.LayerDrawable(layers)
        } catch (e: Exception) { }

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
                        setImageBitmap(BitmapFactory.decodeFile(imgFile.absolutePath))
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
            val greenColor = Color.parseColor("#10B981")
            setTextColor(greenColor)
            setShadowLayer(10f, 0f, 0f, greenColor)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 24)
        }

        val changeAvatarBtn = Button(this).apply {
            text = "CHANGE AVATAR"
            setBackgroundColor(Color.parseColor("#8B5CF6"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        var isIdVisible = false
        val idText = TextView(this).apply {
            text = "Unique ID: ****"
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }

        val idButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        val showIdBtn = Button(this).apply {
            text = "👁️ SHOW"
            setBackgroundColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 16, 0) }
        }

        val copyIdBtn = Button(this).apply {
            text = "📋 COPY"
            setBackgroundColor(Color.parseColor("#3B82F6"))
            setTextColor(Color.WHITE)
        }

        idButtonsLayout.addView(showIdBtn)
        idButtonsLayout.addView(copyIdBtn)

        val logoutBtn = Button(this).apply {
            text = "LOG OUT / SWITCH ACCOUNT"
            setBackgroundColor(Color.parseColor("#EF4444"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        layout.addView(backBtn)
        layout.addView(avatarView)
        layout.addView(title)
        layout.addView(changeAvatarBtn)
        layout.addView(idText)
        layout.addView(idButtonsLayout)
        layout.addView(logoutBtn)
        setContentView(layout)

        changeAvatarBtn.setOnClickListener { getAvatar.launch("image/*") }

        showIdBtn.setOnClickListener {
            isIdVisible = !isIdVisible
            if (isIdVisible) {
                idText.text = "Unique ID: $playerId"
                showIdBtn.text = "🙈 HIDE"
            } else {
                idText.text = "Unique ID: ****"
                showIdBtn.text = "👁️ SHOW"
            }
        }

        copyIdBtn.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Player ID", playerId)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "ID Copied!", Toast.LENGTH_SHORT).show()
        }

        logoutBtn.setOnClickListener {
            val prefs = getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            playerId = ""
            playerName = ""
            playerPass = ""
            avatarPath = ""
            showLoginScreen()
        }
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
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            gravity = Gravity.START 
        }
        backBtn.setOnClickListener { showMainMenu() }
        
        val title = TextView(this).apply { 
            text = "Antivirus Scanner"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 16, 0, 16) 
        }
        
        apiKeyInput = EditText(this).apply { 
            hint = "Enter API Key"
            setText(apiKey)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT 
        }
        
        val saveKeyButton = Button(this).apply { 
            text = "SAVE KEY"
            setBackgroundColor(Color.GRAY)
            setTextColor(Color.WHITE) 
        }
        
        val scanFileButton = Button(this).apply { 
            text = "SELECT & SCAN FILE"
            setBackgroundColor(Color.parseColor("#3B82F6"))
            setTextColor(Color.WHITE) 
        }
        
        progressBar = ProgressBar(this).apply { visibility = View.GONE }
        resultText = TextView(this).apply { 
            text = "Ready to scan..."
            setTextColor(Color.LTGRAY) 
        }
        
        val scrollView = ScrollView(this).apply { 
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                0, 
                1f
            )
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
        
        scanFileButton.setOnClickListener { 
            if (apiKey.isNotEmpty()) {
                getContent.launch("*/*")
            } else {
                Toast.makeText(this, "Enter Key", Toast.LENGTH_SHORT).show()
            }
        }
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
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            gravity = Gravity.START 
        }
        backBtn.setOnClickListener { showMainMenu() }
        
        val title = TextView(this).apply { 
            text = "💰 COIN CLICKER"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            val pinkColor = Color.parseColor("#FF00FF")
            setTextColor(pinkColor)
            setShadowLayer(10f, 0f, 0f, pinkColor)
            setPadding(0, 16, 0, 32) 
        }
        
        coinText = TextView(this).apply { 
            text = "Coins: $coins"
        }
