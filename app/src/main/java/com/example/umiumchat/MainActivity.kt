package com.example.umiumchat

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.util.UUID

class MainActivity : Activity() {

    private val serviceId = "com.example.umiumchat"
    private val prefs by lazy { getSharedPreferences("blemess", Context.MODE_PRIVATE) }
    private val myId = UUID.randomUUID().toString().take(6)
    private val client by lazy { Nearby.getConnectionsClient(this) }
    private val connected = mutableSetOf<String>()
    private val seen = HashSet<String>()
    private val history = mutableListOf<String>()

    private var dark = false
    private lateinit var root: LinearLayout
    private lateinit var chatBox: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var statusText: TextView

    private var username: String
        get() = prefs.getString("username", "") ?: ""
        set(v) { prefs.edit().putString("username", v).apply() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark = prefs.getBoolean("dark", false)
        requestPermissions(
            arrayOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.NEARBY_WIFI_DEVICES"
            ), 1
        )
        showChats()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (username.isEmpty()) showProfile() else startMesh()
    }

    // ---------- Цвета темы ----------
    private val bg get() = if (dark) Color.parseColor("#17212B") else Color.parseColor("#FFFFFF")
    private val barColor get() = if (dark) Color.parseColor("#232E3C") else Color.parseColor("#517DA2")
    private val textColor get() = if (dark) Color.WHITE else Color.BLACK
    private val subColor get() = if (dark) Color.parseColor("#8A99A8") else Color.parseColor("#7F8C99")
    private val myBubble get() = if (dark) Color.parseColor("#2B5278") else Color.parseColor("#EFFDDE")
    private val otherBubble get() = if (dark) Color.parseColor("#182533") else Color.parseColor("#FFFFFF")

    private fun topBar(title: String, leftText: String, leftColor: Int, onLeft: () -> Unit,
                       rightText: String, rightColor: Int, onRight: () -> Unit): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(barColor)
            setPadding(16, 40, 16, 16)
        }
        val left = Button(this).apply {
            text = leftText; setTextColor(Color.WHITE); textSize = 13f
            setBackgroundColor(leftColor)
            setOnClickListener { onLeft() }
        }
        val titleView = TextView(this).apply {
            text = title; setTextColor(Color.WHITE); textSize = 18f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }
        val right = Button(this).apply {
            text = rightText; setTextColor(Color.WHITE); textSize = 13f
            setBackgroundColor(rightColor)
            setOnClickListener { onRight() }
        }
        bar.addView(left)
        bar.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(right)
        return bar
    }

    // ---------- Экран чатов ----------
    private fun showChats() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        root.addView(topBar("BLEmess", "Профиль", Color.parseColor("#4CAF50"), { showProfile() },
            "Поиск @", Color.parseColor("#F44336"), { showSearch() }))

        statusText = TextView(this).apply {
            text = "Ищу людей рядом..."; setTextColor(subColor); textSize = 13f
            gravity = Gravity.CENTER; setPadding(0, 16, 0, 16)
        }
        root.addView(statusText)

        val scroll = ScrollView(this)
        chatBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        scroll.addView(chatBox)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        chatScroll = scroll

        // Поле ввода
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(16, 8, 16, 16)
            gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(barColor)
        }
        val input = EditText(this).apply {
            hint = "Сообщение"; setTextColor(textColor); setHintTextColor(subColor)
        }
        val send = Button(this).apply {
            text = "➤"; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3390EC"))
            setOnClickListener { sendText(input.text.toString()); input.setText("") }
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        inputRow.addView(send)
        root.addView(inputRow)

        setContentView(root)
        redrawHistory()
    }

    private fun bubble(text: String, mine: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 16f
            setPadding(24, 14, 24, 14)
            background = GradientDrawable().apply {
                cornerRadius = 32f
                setColor(if (mine) myBubble else otherBubble)
            }
            val lp = LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(0, 8, 0, 8)
                gravity = if (mine) Gravity.END else Gravity.START
            }
            layoutParams = lp
        }
    }

    private fun redrawHistory() {
        if (!::chatBox.isInitialized) return
        runOnUiThread {
            chatBox.removeAllViews()
            for (line in history) {
                val mine = line.startsWith("Я: ")
                chatBox.addView(bubble(line, mine))
            }
            chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun log(s: String) {
        history.add(s)
        redrawHistory()
        if (::statusText.isInitialized) runOnUiThread { statusText.text = "Подключено: ${connected.size}" }
    }

    // ---------- Профиль (задать @юз и тему) ----------
    private fun showProfile() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48); setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "Профиль"; textSize = 22f; setTextColor(textColor)
            typeface = Typeface.DEFAULT_BOLD
        }
        val nameIn = EditText(this).apply {
            hint = "@юзернейм (латиница, цифры, _)"
            setText(username); setTextColor(textColor); setHintTextColor(subColor)
        }
        val myIdText = TextView(this).apply {
            text = "Ваш внутренний id: $myId"; setTextColor(subColor); textSize = 13f
        }
        val themeBtn = Button(this).apply {
            text = if (dark) "Светлая тема" else "Тёмная тема"
            setOnClickListener {
                dark = !dark
                prefs.edit().putBoolean("dark", dark).apply()
                showProfile()
            }
        }
        val save = Button(this).apply {
            text = "Сохранить"; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3390EC"))
            setOnClickListener {
                val name = nameIn.text.toString().trim().removePrefix("@")
                if (name.isNotEmpty()) {
                    username = name
                    showChats()
                    if (connected.isEmpty()) startMesh()
                }
            }
        }
        val back = Button(this).apply {
            text = "Назад"; setOnClickListener { showChats() }
        }
        box.addView(title)
        box.addView(nameIn)
        box.addView(myIdText)
        box.addView(themeBtn)
        box.addView(save)
        box.addView(back)
        setContentView(box)
    }

    // ---------- Поиск по @юзу ----------
    private fun showSearch() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48); setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "Поиск по @юзу"; textSize = 22f; setTextColor(textColor)
            typeface = Typeface.DEFAULT_BOLD
        }
        val info = TextView(this).apply {
            text = "Людей рядом: ${connected.size}. Сообщения отправляются всем в радиусе, поэтому адресат получит их через цепочку телефонов."
            setTextColor(subColor); textSize = 13f
        }
        val find = EditText(this).apply {
            hint = "Введите @юз"; setTextColor(textColor); setHintTextColor(subColor)
        }
        val back = Button(this).apply { text = "Назад"; setOnClickListener { showChats() } }
        box.addView(title)
        box.addView(info)
        box.addView(find)
        box.addView(back)
        setContentView(box)
    }

    // ---------- Mesh-сеть ----------
    private fun startMesh() {
        val options = Strategy.P2P_CLUSTER
        client.startAdvertising(myId, serviceId, connectionCallback,
            AdvertisingOptions.Builder().setStrategy(options).build())
            .addOnFailureListener { log("Ошибка объявления: ${it.message}") }
        client.startDiscovery(serviceId, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(options).build())
            .addOnFailureListener { log("Ошибка поиска: ${it.message}") }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (endpointId !in connected) client.requestConnection(username.ifEmpty { myId }, endpointId, connectionCallback)
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connected.add(endpointId)
                log("Связь установлена с $endpointId")
            }
        }
        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            log("Связь потеряна: $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            try {
                handleIncoming(endpointId, JSONObject(String(bytes)))
            } catch (e: Exception) {
                log("Ошибка данных: ${e.message}")
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun sendText(text: String) {
        if (text.isBlank()) return
        val msg = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("from", username.ifEmpty { myId })
            .put("text", text)
            .put("ttl", 5)
        seen.add(msg.getString("id"))
        log("Я: $text")
        forward(msg, null)
    }

    private fun handleIncoming(fromEndpoint: String, msg: JSONObject) {
        val id = msg.getString("id")
        if (!seen.add(id)) return
        log("@${msg.getString("from")}: ${msg.getString("text")}")
        val ttl = msg.getInt("ttl")
        if (ttl > 1) forward(msg.put("ttl", ttl - 1), fromEndpoint)
    }

    private fun forward(msg: JSONObject, exceptEndpoint: String?) {
        val bytes = msg.toString().toByteArray()
        for (id in connected) {
            if (id != exceptEndpoint) client.sendPayload(id, Payload.fromBytes(bytes))
        }
    }
}
