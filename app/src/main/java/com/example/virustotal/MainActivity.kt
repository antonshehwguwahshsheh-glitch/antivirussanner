package com.example.virustotal

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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

    // Тут мы больше не храним ключ в коде, он будет браться из памяти телефона
    private var apiKey: String = ""
    
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var apiKeyInput: EditText
    private val client = OkHttpClient()

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
        
        // Загружаем сохраненный ключ при запуске
        val prefs = getSharedPreferences("VirusTotalPrefs", Context.MODE_PRIVATE)
        apiKey = prefs.getString("api_key", "") ?: ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val logoView = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        val title = TextView(this).apply {
            text = "VirusTotal"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Analyze suspicious files and apps"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        // Поле для ввода API ключа
        apiKeyInput = EditText(this).apply {
            hint = "Enter your VirusTotal API Key here"
            setText(apiKey)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        // Кнопка сохранения ключа
        val saveKeyButton = Button(this).apply {
            text = "SAVE API KEY"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#6B7280")) // Серый
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val scanFileButton = Button(this).apply {
            text = "SELECT & SCAN FILE"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#3B82F6"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        val scanAppsButton = Button(this).apply {
            text = "SCAN INSTALLED APPS"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#10B981"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 16) }
        }

        resultText = TextView(this).apply {
            text = "Ready to scan..."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 16, 0, 16)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            addView(resultText)
        }

        layout.addView(logoView)
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(apiKeyInput)
        layout.addView(saveKeyButton)
        layout.addView(scanFileButton)
        layout.addView(scanAppsButton)
        layout.addView(progressBar)
        layout.addView(scrollView)
        
        setContentView(layout)

        // Сохранение ключа
        saveKeyButton.setOnClickListener {
            val newKey = apiKeyInput.text.toString().trim()
            if (newKey.isNotEmpty()) {
                apiKey = newKey
                prefs.edit().putString("api_key", apiKey).apply()
                Toast.makeText(this, "API Key saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Key cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        scanFileButton.setOnClickListener {
            if (checkApiKey()) getContent.launch("*/*")
        }

        scanAppsButton.setOnClickListener {
            if (checkApiKey()) scanInstalledApps()
        }
    }

    private fun checkApiKey(): Boolean {
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please enter and save API Key first!", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun scanInstalledApps() {
        showLoading(true, "Getting installed apps...")
        val pm = packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        
        val appsToScan = packages.take(3)
        
        if (appsToScan.isEmpty()) {
            showLoading(false)
            updateUI("No apps found", Color.RED)
            return
        }

        var completed = 0
        val total = appsToScan.size

        for (app in appsToScan) {
            val appInfo = app.applicationInfo
            if (appInfo != null) {
                val apkPath = appInfo.sourceDir
                val appName = pm.getApplicationLabel(appInfo).toString()
                val file = File(apkPath)
                
                if (file.exists()) {
                    scanFileForApp(file, appName, completed, total)
                }
            }
            completed++
            try { Thread.sleep(15000) } catch (e: InterruptedException) { }
        }
        showLoading(false)
    }

    private fun scanFileForApp(file: File, appName: String, current: Int, total: Int) {
        val hash = getFileHash(file)
        if (hash == null) {
            updateUI("Error reading $appName", Color.RED)
            return
        }

        val url = "https://www.virustotal.com/api/v3/files/$hash"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-apikey", apiKey)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { updateUI("Network error for $appName", Color.RED) }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        parseAppResponse(responseBody, appName, current, total)
                    } else if (response.code == 404) {
                        updateUI("❓ $appName: Not found in DB", Color.YELLOW)
                    } else {
                        updateUI("API Error ${response.code} for $appName", Color.RED)
                    }
                }
            }
        })
    }

    private fun parseAppResponse(json: String, appName: String, current: Int, total: Int) {
        try {
            val jsonObject = JSONObject(json)
            val attributes = jsonObject.getJSONObject("data").getJSONObject("attributes")
            val stats = attributes.getJSONObject("last_analysis_stats")
            val malicious = stats.getInt("malicious")
            
            if (malicious > 0) {
                updateUI("⚠️ $appName: $malicious threats found! ($current/$total)", Color.RED)
            } else {
                updateUI("✅ $appName: Clean ($current/$total)", Color.GREEN)
            }
        } catch (e: Exception) {
            updateUI("Parsing error for $appName", Color.RED)
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun scanFile(file: File) {
        showLoading(true, "Calculating hash...")
        
        val hash = getFileHash(file)
        if (hash == null) {
            showLoading(false)
            updateUI("Error reading file", Color.RED)
            return
        }

        updateUI("Hash: $hash\nQuerying VirusTotal...", Color.WHITE)

        val url = "https://www.virustotal.com/api/v3/files/$hash"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-apikey", apiKey)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { 
                    showLoading(false)
                    updateUI("Network error: ${e.message}", Color.RED) 
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    showLoading(false)
                    if (response.isSuccessful && responseBody != null) {
                        parseResponse(responseBody)
                    } else if (response.code == 404) {
                        updateUI("File not found in DB.\nIt might be clean or new.", Color.YELLOW)
                    } else {
                        updateUI("API Error: ${response.code}\nCheck API key.", Color.RED)
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
            report.append("Scan Results:\n\n")
            report.append("🔴 Malicious: $malicious\n")
            report.append("🟢 Undetected: $undetected\n\n")
            
            if (malicious > 0 && attributes.has("last_analysis_results")) {
                report.append("--- Detected Threats ---\n")
                val results = attributes.getJSONObject("last_analysis_results")
                
                val keys = results.keys()
                while (keys.hasNext()) {
                    val engineName = keys.next()
                    val engineData = results.getJSONObject(engineName)
                    val category = engineData.optString("category")
                    
                    if (category == "malicious") {
                        val threatName = engineData.optString("result", "Unknown")
                        report.append("• $engineName: $threatName\n")
                    }
                }
            } else if (malicious == 0) {
                report.append("✅ No threats detected by any engine.")
            }
            
            updateUI(report.toString(), if (malicious > 0) Color.RED else Color.GREEN)
            
        } catch (e: Exception) {
            updateUI("Parsing error: ${e.message}", Color.RED)
        }
    }

    private fun showLoading(show: Boolean, message: String = "") {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (message.isNotEmpty()) {
            resultText.text = message
            resultText.setTextColor(Color.WHITE)
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
