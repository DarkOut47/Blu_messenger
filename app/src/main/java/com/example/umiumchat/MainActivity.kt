package com.example.umiumchat

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.util.UUID

class MainActivity : Activity() {

    private enum class Screen { CHATS, CHAT, PROFILE, SEARCH }
    private data class Msg(val from: String, val text: String, val mine: Boolean)
    private class Btn(val text: String, val color: Int, val action: () -> Unit)

    private val serviceId = "com.example.umiumchat"
    private val prefs by lazy { getSharedPreferences("blemess", Context.MODE_PRIVATE) }
    private val client by lazy { Nearby.getConnectionsClient(this) }
    private val myId: String by lazy {
        prefs.getString("id", null) ?: UUID.randomUUID().toString().take(6).also {
            prefs.edit().putString("id", it).apply()
        }
    }

    private var username: String
        get() = prefs.getString("username", "") ?: ""
        set(v) { prefs.edit().putString("username", v).apply() }

    private val myName: String get() = username.ifEmpty { myId }

    private var dark = false
    private var screen = Screen.CHATS
    private var openPeer = ""
    private var draftName: String? = null

    private val nearby = mutableMapOf<String, String>()                 // endpointId -> @юз
    private val connected = mutableSetOf<String>()                      // endpointId со связью
    private val pending = mutableSetOf<String>()                        // запросы связи в процессе
    private val seen = HashSet<String>()                                // уже виденные сообщения
    private val chats = LinkedHashMap<String, MutableList<Msg>>()       // @юз -> переписка

    // Живые элементы экрана, которые обновляются без полной перерисовки
    private var headerSub: TextView? = null
    private var listBox: LinearLayout? = null
    private var msgBox: LinearLayout? = null
    private var chatScroll: ScrollView? = null
    private var nearbyBox: LinearLayout? = null

    // ---------- Цвета темы ----------
    private val bg get() = if (dark) Color.parseColor("#17212B") else Color.WHITE
    private val barColor get() = if (dark) Color.parseColor("#232E3C") else Color.parseColor("#517DA2")
    private val textColor get() = if (dark) Color.WHITE else Color.BLACK
    private val subColor get() = if (dark) Color.parseColor("#8A99A8") else Color.parseColor("#7F8C99")
    private val sepColor get() = if (dark) Color.parseColor("#253341") else Color.parseColor("#E6E8EB")
    private val myBubble get() = if (dark) Color.parseColor("#2B5278") else Color.parseColor("#EFFDDE")
    private val otherBubble get() = if (dark) Color.parseColor("#182533") else Color.WHITE
    private val inputBg get() = if (dark) Color.parseColor("#17212B") else Color.WHITE

    // ---------- Жизненный цикл ----------
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
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startMesh()
        if (username.isEmpty()) go(Screen.PROFILE)
    }

    override fun onBackPressed() {
        if (screen != Screen.CHATS) go(Screen.CHATS) else super.onBackPressed()
    }

    override fun onDestroy() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        super.onDestroy()
    }

    // ---------- Навигация и перерисовка ----------
    private fun go(s: Screen) {
        screen = s
        render()
    }

    private fun openChat(peer: String) {
        openPeer = peer
        chats.getOrPut(peer) { mutableListOf() }
        go(Screen.CHAT)
    }

    private fun render() {
        headerSub = null; listBox = null; msgBox = null; chatScroll = null; nearbyBox = null
        when (screen) {
            Screen.CHATS -> buildChats()
            Screen.CHAT -> buildChat()
            Screen.PROFILE -> buildProfile()
            Screen.SEARCH -> buildSearch()
        }
    }

    private fun refreshLive() {
        when (screen) {
            Screen.CHATS -> fillChatList()
            Screen.CHAT -> fillMessages()
            Screen.SEARCH -> fillNearby()
            Screen.PROFILE -> {}
        }
    }

    private fun ui(block: () -> Unit) = runOnUiThread { block() }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    // ---------- Элементы интерфейса ----------
    private fun screenRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
    }

    private fun barButton(b: Btn) = Button(this).apply {
        text = b.text
        setTextColor(Color.WHITE)
        textSize = 14f
        isAllCaps = false
        setBackgroundColor(b.color)
        setOnClickListener { b.action() }
    }

    private fun titleBar(title: String, subtitle: String, left: Btn?, right: Btn?): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(barColor)
        bar.setPadding(dp(8), dp(12), dp(8), dp(12))

        left?.let { bar.addView(barButton(it)) }

        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.gravity = Gravity.CENTER

        val titleView = TextView(this)
        titleView.text = title
        titleView.setTextColor(Color.WHITE)
        titleView.textSize = 18f
        titleView.typeface = Typeface.DEFAULT_BOLD
        titleView.gravity = Gravity.CENTER

        val sub = TextView(this)
        sub.text = subtitle
        sub.setTextColor(Color.parseColor("#D6E4F0"))
        sub.textSize = 12f
        sub.gravity = Gravity.CENTER
        headerSub = sub

        titles.addView(titleView)
        titles.addView(sub)
        bar.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))

        right?.let { bar.addView(barButton(it)) }
        return bar
    }

    private fun label(s: String): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.setTextColor(subColor)
        tv.textSize = 13f
        tv.setPadding(0, dp(18), 0, dp(6))
        return tv
    }

    private fun avatar(name: String): TextView {
        val palette = listOf("#E17076", "#7BC862", "#65AADD", "#A695E7", "#EE7AAE", "#6EC9CB", "#FAA774")
        val color = palette[Math.abs(name.hashCode()) % palette.size]
        val tv = TextView(this)
        tv.text = name.firstOrNull()?.uppercase() ?: "?"
        tv.setTextColor(Color.WHITE)
        tv.textSize = 20f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.gravity = Gravity.CENTER
        val shape = GradientDrawable()
        shape.setShape(GradientDrawable.OVAL)
        shape.setColor(Color.parseColor(color))
        tv.background = shape
        tv.layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).apply { rightMargin = dp(12) }
        return tv
    }

    private fun personRow(name: String, sub: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(10), dp(16), dp(10))
        row.setBackgroundColor(bg)
        row.setOnClickListener { onClick() }
        row.addView(avatar(name))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL

        val nameView = TextView(this)
        nameView.text = "@$name"
        nameView.setTextColor(textColor)
        nameView.textSize = 17f
        nameView.typeface = Typeface.DEFAULT_BOLD

        val subView = TextView(this)
        subView.text = sub
        subView.setTextColor(subColor)
        subView.textSize = 14f
        subView.maxLines = 1
        subView.ellipsize = TextUtils.TruncateAt.END

        texts.addView(nameView)
        texts.addView(subView)
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun addSeparator(box: LinearLayout) {
        val line = View(this)
        line.setBackgroundColor(sepColor)
        box.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
    }

    // ---------- Экран: список чатов ----------
    private fun buildChats() {
        val root = screenRoot()
        root.addView(
            titleBar(
                "BLEmess", "",
                Btn("Профиль", Color.parseColor("#4CAF50")) { go(Screen.PROFILE) },
                Btn("Поиск @", Color.parseColor("#F44336")) { go(Screen.SEARCH) }
            )
        )
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        listBox = list

        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        fillChatList()
    }

    private fun fillChatList() {
        headerSub?.text = "Людей рядом: ${nearby.values.distinct().size}"
        val box = listBox ?: return
        box.removeAllViews()

        if (chats.isEmpty()) {
            val hint = TextView(this)
            hint.text = "Чатов пока нет.\n\nНажмите «Поиск @» и введите юз человека, который рядом с вами."
            hint.setTextColor(subColor)
            hint.textSize = 15f
            hint.gravity = Gravity.CENTER
            hint.setPadding(dp(32), dp(80), dp(32), dp(32))
            box.addView(hint)
            return
        }

        for ((peer, msgs) in chats.entries.reversed()) {
            val last = msgs.lastOrNull()
            val preview = if (last == null) "Нет сообщений" else (if (last.mine) "Вы: " else "") + last.text
            box.addView(personRow(peer, preview) { openChat(peer) }, LinearLayout.LayoutParams(-1, -2))
            addSeparator(box)
        }
    }

    // ---------- Экран: чат ----------
    private fun buildChat() {
        val root = screenRoot()
        root.addView(
            titleBar("@$openPeer", "", Btn("←", Color.TRANSPARENT) { go(Screen.CHATS) }, null)
        )

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(12), dp(12), dp(12), dp(12))
        msgBox = box

        val scroll = ScrollView(this)
        chatScroll = scroll
        scroll.addView(box)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(inputRow())
        setContentView(root)
        fillMessages()
    }

    private fun inputRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(10), dp(8), dp(10), dp(8))
        row.setBackgroundColor(barColor)

        val input = EditText(this)
        input.hint = "Сообщение"
        input.setTextColor(textColor)
        input.setHintTextColor(subColor)
        input.setBackgroundColor(inputBg)
        input.setPadding(dp(14), dp(10), dp(14), dp(10))

        val send = Button(this)
        send.text = "➤"
        send.setTextColor(Color.WHITE)
        send.isAllCaps = false
        send.setBackgroundColor(Color.parseColor("#3390EC"))
        send.setOnClickListener {
            sendTo(openPeer, input.text.toString())
            input.setText("")
        }

        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(send)
        return row
    }

    private fun bubble(m: Msg): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = if (m.mine) Gravity.END else Gravity.START
        row.setPadding(0, dp(3), 0, dp(3))

        val tv = TextView(this)
        tv.text = m.text
        tv.setTextColor(textColor)
        tv.textSize = 16f
        tv.setPadding(dp(14), dp(9), dp(14), dp(9))
        val shape = GradientDrawable()
        shape.setCornerRadius(dp(16).toFloat())
        shape.setColor(if (m.mine) myBubble else otherBubble)
        tv.background = shape

        row.addView(tv, LinearLayout.LayoutParams(-2, -2))
        return row
    }

    private fun fillMessages() {
        headerSub?.text = if (nearby.containsValue(openPeer)) "в сети рядом" else "не в сети рядом"
        val box = msgBox ?: return
        box.removeAllViews()
        for (m in chats[openPeer].orEmpty()) {
            box.addView(bubble(m), LinearLayout.LayoutParams(-1, -2))
        }
        chatScroll?.post { chatScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    // ---------- Экран: профиль ----------
    private fun buildProfile() {
        val root = screenRoot()
        root.addView(
            titleBar("Профиль", "Настройки", Btn("←", Color.TRANSPARENT) { go(Screen.CHATS) }, null)
        )

        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(24), dp(16), dp(24), dp(24))

        val nameIn = EditText(this)
        nameIn.hint = "например: ivan_07"
        nameIn.setText(draftName ?: username)
        nameIn.setTextColor(textColor)
        nameIn.setHintTextColor(subColor)

        val themeSwitch = Switch(this)
        themeSwitch.text = "Тёмная тема"
        themeSwitch.setTextColor(textColor)
        themeSwitch.isChecked = dark
        themeSwitch.setOnCheckedChangeListener { _, checked ->
            dark = checked
            prefs.edit().putBoolean("dark", checked).apply()
            draftName = nameIn.text.toString()
            go(Screen.PROFILE)
        }

        val save = Button(this)
        save.text = "Сохранить"
        save.setTextColor(Color.WHITE)
        save.isAllCaps = false
        save.setBackgroundColor(Color.parseColor("#3390EC"))
        save.setOnClickListener {
            val name = nameIn.text.toString().trim().removePrefix("@")
            if (!name.matches(Regex("[A-Za-z0-9_]{3,32}"))) {
                toast("Юз: от 3 до 32 символов, только латиница, цифры и _")
                return@setOnClickListener
            }
            username = name
            draftName = null
            startMesh()
            go(Screen.CHATS)
        }

        form.addView(label("Ваш @юз (латиница, цифры и _, от 3 до 32 символов)"))
        form.addView(nameIn)
        form.addView(
            label("Юз нужен, чтобы вас находили по поиску. Пока он не задан, вас видят как: $myId")
        )
        form.addView(themeSwitch, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        form.addView(save, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })

        root.addView(form)
        setContentView(root)
    }

    // ---------- Экран: поиск по @юзу ----------
    private fun buildSearch() {
        val root = screenRoot()
        root.addView(
            titleBar("Поиск по @юзу", "", Btn("←", Color.TRANSPARENT) { go(Screen.CHATS) }, null)
        )

        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(16), dp(16), dp(16), dp(8))

        val find = EditText(this)
        find.hint = "Введите @юз"
        find.setTextColor(textColor)
        find.setHintTextColor(subColor)

        val findBtn = Button(this)
        findBtn.text = "Найти"
        findBtn.setTextColor(Color.WHITE)
        findBtn.isAllCaps = false
        findBtn.setBackgroundColor(Color.parseColor("#F44336"))
        findBtn.setOnClickListener {
            val q = find.text.toString().trim().removePrefix("@")
            if (q.isEmpty()) return@setOnClickListener
            val real = nearby.values.firstOrNull { it.equals(q, ignoreCase = true) }
            if (real != null) {
                openChat(real)
            } else {
                toast("@$q сейчас не рядом. Подойдите ближе или проверьте юз.")
            }
        }

        form.addView(find)
        form.addView(findBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        form.addView(label("Сейчас рядом:"))
        root.addView(form)

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        nearbyBox = list

        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        fillNearby()
    }

    private fun fillNearby() {
        val names = nearby.values.distinct()
        headerSub?.text = "Людей рядом: ${names.size}"
        val box = nearbyBox ?: return
        box.removeAllViews()

        if (names.isEmpty()) {
            val hint = TextView(this)
            hint.text = "Рядом пока никого нет.\nПопросите друга открыть BLEmess рядом с вами."
            hint.setTextColor(subColor)
            hint.textSize = 14f
            hint.gravity = Gravity.CENTER
            hint.setPadding(dp(24), dp(40), dp(24), dp(24))
            box.addView(hint)
            return
        }

        for (name in names) {
            box.addView(personRow(name, "в сети рядом") { openChat(name) }, LinearLayout.LayoutParams(-1, -2))
            addSeparator(box)
        }
    }

    // ---------- Mesh-сеть (Nearby Connections) ----------
    private fun startMesh() {
        client.stopAdvertising()
        client.stopDiscovery()
        val options = Strategy.P2P_CLUSTER
        client.startAdvertising(
            myName, serviceId, connectionCallback,
            AdvertisingOptions.Builder().setStrategy(options).build()
        ).addOnFailureListener { toast("Не могу объявить себя: ${it.message}") }
        client.startDiscovery(
            serviceId, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(options).build()
        ).addOnFailureListener { toast("Не могу искать людей: ${it.message}") }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            ui {
                nearby[endpointId] = info.endpointName
                if (endpointId !in connected && endpointId !in pending) {
                    pending.add(endpointId)
                    client.requestConnection(myName, endpointId, connectionCallback)
                        .addOnFailureListener { pending.remove(endpointId) }
                }
                refreshLive()
            }
        }

        override fun onEndpointLost(endpointId: String) {
            ui {
                nearby.remove(endpointId)
                refreshLive()
            }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            ui { nearby[endpointId] = info.endpointName }
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            ui {
                pending.remove(endpointId)
                if (result.status.isSuccess) connected.add(endpointId)
                refreshLive()
            }
        }

        override fun onDisconnected(endpointId: String) {
            ui {
                connected.remove(endpointId)
                pending.remove(endpointId)
                refreshLive()
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            ui {
                try {
                    handleIncoming(endpointId, JSONObject(String(bytes)))
                } catch (e: Exception) {
                    // пришли данные, которые не удалось разобрать, пропускаем
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    // ---------- Сообщения ----------
    private fun addMsg(peer: String, m: Msg) {
        val list = chats.remove(peer) ?: mutableListOf()   // перенос в конец = самый свежий
        list.add(m)
        chats[peer] = list
    }

    private fun sendTo(peer: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || peer.isEmpty()) return
        val msg = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("from", myName)
            .put("to", peer)
            .put("text", clean)
            .put("ttl", 6)
        seen.add(msg.getString("id"))
        addMsg(peer, Msg(myName, clean, true))
        forward(msg, null)
        refreshLive()
    }

    private fun handleIncoming(fromEndpoint: String, msg: JSONObject) {
        val id = msg.getString("id")
        if (!seen.add(id)) return

        val from = msg.getString("from")
        val to = msg.getString("to")
        val text = msg.getString("text")

        if (to == myName) {
            addMsg(from, Msg(from, text, false))
            if (!(screen == Screen.CHAT && openPeer == from)) toast("Сообщение от @$from")
            refreshLive()
        }

        val ttl = msg.getInt("ttl")
        if (ttl > 1) forward(msg.put("ttl", ttl - 1), fromEndpoint)
    }

    // Пересылка всем соседям, кроме того, от кого пришло сообщение
    private fun forward(msg: JSONObject, except: String?) {
        val bytes = msg.toString().toByteArray()
        for (id in connected.toList()) {
            if (id != except) client.sendPayload(id, Payload.fromBytes(bytes))
        }
    }
}
