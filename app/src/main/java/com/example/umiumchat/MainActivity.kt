package com.example.umiumchat

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainActivity : Activity() {

    private enum class Screen {
        CHATS, CHAT, PROFILE, SETTINGS, SEARCH,
        ACCOUNT, CHAT_SETTINGS, PRIVACY, NOTIFY, FOLDERS, LANGUAGE, BLACKLIST
    }

    private data class Msg(
        val id: String,
        val from: String,
        var text: String,
        val mine: Boolean,
        var read: Boolean = false,
        var edited: Boolean = false,
        var replyText: String = "",
        var fwd: String = "",
        var unread: Boolean = false
    )

    private class Btn(val text: String, val color: Int, val action: () -> Unit)

    private val serviceId = "com.example.umiumchat"
    private val prefs by lazy { getSharedPreferences("blemess", Context.MODE_PRIVATE) }
    private val client by lazy { Nearby.getConnectionsClient(this) }
    private val langs = listOf(
        "Русский", "English", "Español", "Deutsch", "Français",
        "Português", "中文", "日本語", "Türkçe", "Українська"
    )
    private val walls = listOf("", "#D6EAF8", "#E5DDD5", "#D5F5E3", "#2C3E50")

    private var myId = ""
    private var dark = false
    private var unlocked = false
    private var screen = Screen.CHATS
    private var openPeer = ""
    private var archiveView = false
    private var editingId: String? = null
    private var replyPreview = ""

    private val nearby = mutableMapOf<String, String>()   // endpointId -> @юз рядом
    private val links = mutableMapOf<String, String>()    // endpointId -> @юз, связь есть (онлайн)
    private val pending = mutableSetOf<String>()
    private val seen = HashSet<String>()
    private val chats = LinkedHashMap<String, MutableList<Msg>>()

    private var headerSub: TextView? = null
    private var listBox: LinearLayout? = null
    private var msgBox: LinearLayout? = null
    private var chatScroll: ScrollView? = null
    private var nearbyBox: LinearLayout? = null
    private var inputField: EditText? = null
    private var hintBar: LinearLayout? = null
    private var hintText: TextView? = null

    private var username: String
        get() = prefs.getString("username", "") ?: ""
        set(v) { prefs.edit().putString("username", v).apply() }
    private val myName: String get() = username.ifEmpty { myId }

    // ---------- Цвета ----------
    private val bg get() = if (dark) Color.parseColor("#17212B") else Color.WHITE
    private val barColor get() = if (dark) Color.parseColor("#232E3C") else Color.parseColor("#517DA2")
    private val textColor get() = if (dark) Color.WHITE else Color.BLACK
    private val subColor get() = if (dark) Color.parseColor("#8A99A8") else Color.parseColor("#7F8C99")
    private val sepColor get() = if (dark) Color.parseColor("#253341") else Color.parseColor("#E6E8EB")
    private val myBubble get() = if (dark) Color.parseColor("#2B5278") else Color.parseColor("#EFFDDE")
    private val otherBubble get() = if (dark) Color.parseColor("#182533") else Color.WHITE
    private val inputBg get() = if (dark) Color.parseColor("#17212B") else Color.WHITE
    private val fontSize get() = prefs.getInt("fontSize", 16)
    private val chatBg get() = prefs.getInt("wall", 0).let { if (it == 0) bg else Color.parseColor(walls[it]) }

    // ---------- Настройки и наборы ----------
    private fun pref(key: String, def: Boolean) = prefs.getBoolean(key, def)
    private fun setPref(key: String, v: Boolean) { prefs.edit().putBoolean(key, v).apply() }
    private fun setInt(key: String, v: Int) { prefs.edit().putInt(key, v).apply() }
    private fun setStr(key: String, v: String) { prefs.edit().putString(key, v).apply() }
    private fun loadSet(key: String): MutableSet<String> =
        (prefs.getString(key, "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableSet()
    private fun saveSet(key: String, s: Set<String>) { prefs.edit().putString(key, s.joinToString(",")).apply() }
    private fun pinned() = loadSet("pinned")
    private fun archived() = loadSet("archived")
    private fun blocked() = loadSet("blacklist")
    private fun online(name: String) = links.values.contains(name)

    // ---------- Жизненный цикл ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        myId = prefs.getString("id", null) ?: newId()
        dark = pref("dark", false)
        requestPermissions(
            arrayOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.NEARBY_WIFI_DEVICES"
            ), 1
        )
        if ((prefs.getString("pin", "") ?: "").isEmpty()) {
            unlocked = true
            render()
        } else {
            showLock()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startMesh()
        if (unlocked && username.isEmpty()) go(Screen.PROFILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == RESULT_OK) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            setStr("sound", uri?.toString() ?: "")
            toast("Мелодия сохранена")
            go(Screen.NOTIFY)
        }
    }

    override fun onBackPressed() {
        if (!unlocked) { super.onBackPressed(); return }
        when (screen) {
            Screen.CHATS -> if (archiveView) { archiveView = false; go(Screen.CHATS) } else super.onBackPressed()
            Screen.CHAT, Screen.PROFILE, Screen.SETTINGS, Screen.SEARCH -> go(Screen.CHATS)
            Screen.BLACKLIST -> go(Screen.PRIVACY)
            else -> go(Screen.SETTINGS)
        }
    }

    override fun onDestroy() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        super.onDestroy()
    }

    private fun newId(): String {
        val id = UUID.randomUUID().toString().take(6)
        prefs.edit().putString("id", id).apply()
        return id
    }

    // ---------- Навигация и обновления ----------
    private fun go(s: Screen) {
        screen = s
        render()
    }

    private fun render() {
        headerSub = null; listBox = null; msgBox = null; chatScroll = null
        nearbyBox = null; inputField = null; hintBar = null; hintText = null
        when (screen) {
            Screen.CHATS -> buildChats()
            Screen.CHAT -> buildChat()
            Screen.PROFILE -> buildProfile()
            Screen.SETTINGS -> buildSettings()
            Screen.SEARCH -> buildSearch()
            Screen.ACCOUNT -> buildAccount()
            Screen.CHAT_SETTINGS -> buildChatSettings()
            Screen.PRIVACY -> buildPrivacy()
            Screen.NOTIFY -> buildNotify()
            Screen.FOLDERS -> buildFolders()
            Screen.LANGUAGE -> buildLanguage()
            Screen.BLACKLIST -> buildBlacklist()
        }
    }

    private fun refreshLive() {
        ui {
            when (screen) {
                Screen.CHATS -> fillChatList()
                Screen.CHAT -> { fillMessages(); fillHint() }
                Screen.SEARCH -> fillNearby()
                else -> {}
            }
        }
    }

    private fun ui(block: () -> Unit) = runOnUiThread { block() }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    // ---------- Общие элементы интерфейса ----------
    private fun screenRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
    }

    private fun show(root: LinearLayout) {
        if (screen == Screen.CHATS || screen == Screen.PROFILE || screen == Screen.SETTINGS) {
            root.addView(bottomBar())
        }
        setContentView(root)
    }

    private fun bottomBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(barColor)
        bar.addView(tabButton("Чаты", Screen.CHATS))
        bar.addView(tabButton("Профиль", Screen.PROFILE))
        bar.addView(tabButton("Настройки", Screen.SETTINGS))
        return bar
    }

    private fun tabButton(caption: String, target: Screen): TextView {
        val active = screen == target
        val tv = TextView(this)
        tv.text = caption
        tv.gravity = Gravity.CENTER
        tv.textSize = 14f
        tv.setPadding(0, dp(14), 0, dp(14))
        tv.setTextColor(if (active) Color.WHITE else Color.parseColor("#D6E4F0"))
        tv.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tv.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        tv.setOnClickListener { archiveView = false; go(target) }
        return tv
    }

    private fun barButton(b: Btn): Button {
        val btn = Button(this)
        btn.text = b.text
        btn.setTextColor(Color.WHITE)
        btn.textSize = 16f
        btn.isAllCaps = false
        btn.setBackgroundColor(b.color)
        btn.setPadding(dp(10), 0, dp(10), 0)
        btn.setOnClickListener { b.action() }
        return btn
    }

    private fun backBtn(target: Screen) = Btn("←", Color.TRANSPARENT) { go(target) }

    private fun titleBar(title: String, subtitle: String, left: Btn?, right: Btn?): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(barColor)
        bar.setPadding(dp(4), dp(10), dp(4), dp(10))

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

    private fun scrollOf(v: View): ScrollView {
        val s = ScrollView(this)
        s.addView(v)
        return s
    }

    // Страница с заголовком и прокручиваемым содержимым
    private fun page(title: String, left: Btn? = null, right: Btn? = null, sub: String = "",
                     fill: (LinearLayout) -> Unit) {
        val root = screenRoot()
        root.addView(titleBar(title, sub, left, right))
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setBackgroundColor(bg)
        content.setPadding(0, 0, 0, dp(12))
        fill(content)
        root.addView(scrollOf(content), LinearLayout.LayoutParams(-1, 0, 1f))
        show(root)
    }

    private fun label(s: String): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.setTextColor(subColor)
        tv.textSize = 13f
        tv.setPadding(dp(18), dp(16), dp(18), dp(6))
        return tv
    }

    private fun smallText(s: String): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.setTextColor(subColor)
        tv.textSize = 13f
        return tv
    }

    private fun emptyText(s: String): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.setTextColor(subColor)
        tv.textSize = 15f
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(32), dp(60), dp(32), dp(32))
        return tv
    }

    private fun avatar(name: String): TextView {
        val palette = listOf("#E17076", "#7BC862", "#65AADD", "#A695E7", "#EE7AAE", "#6EC9CB", "#FAA774")
        val tv = TextView(this)
        tv.text = name.firstOrNull()?.uppercase() ?: "?"
        tv.setTextColor(Color.WHITE)
        tv.textSize = 20f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.gravity = Gravity.CENTER
        val shape = GradientDrawable()
        shape.setShape(GradientDrawable.OVAL)
        shape.setColor(Color.parseColor(palette[Math.abs(name.hashCode()) % palette.size]))
        tv.background = shape
        return tv
    }

    private fun dot(): View {
        val v = View(this)
        val shape = GradientDrawable()
        shape.setShape(GradientDrawable.OVAL)
        shape.setColor(Color.parseColor("#4CAF50"))
        shape.setStroke(dp(2), bg)
        v.background = shape
        return v
    }

    private fun addSeparator(c: LinearLayout) {
        val line = View(this)
        line.setBackgroundColor(sepColor)
        c.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun addRow(c: LinearLayout, v: View) {
        c.addView(v)
        addSeparator(c)
    }

    // Строка с заголовком и подписью; нажатие вызывает onClick
    private fun navRow(title: String, sub: String, danger: Boolean = false, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(dp(18), dp(12), dp(18), dp(12))
        row.setBackgroundColor(bg)
        row.setOnClickListener { onClick() }
        val t = TextView(this)
        t.text = title
        t.setTextColor(if (danger) Color.parseColor("#E53935") else textColor)
        t.textSize = 16f
        row.addView(t)
        if (sub.isNotEmpty()) row.addView(smallText(sub))
        return row
    }

    private fun switchRow(title: String, key: String, def: Boolean, onChange: (Boolean) -> Unit = {}): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(18), dp(10), dp(18), dp(10))
        row.setBackgroundColor(bg)
        val tv = TextView(this)
        tv.text = title
        tv.setTextColor(textColor)
        tv.textSize = 16f
        val sw = Switch(this)
        sw.isChecked = pref(key, def)
        sw.setOnCheckedChangeListener { _, checked ->
            setPref(key, checked)
            onChange(checked)
        }
        row.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(sw)
        return row
    }

    private fun seekRow(title: String, key: String, min: Int, max: Int, def: Int): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(12), dp(18), dp(12))
        col.setBackgroundColor(bg)
        val value = prefs.getInt(key, def)
        val caption = TextView(this)
        caption.text = "$title: $value"
        caption.setTextColor(textColor)
        caption.textSize = 15f
        val seek = SeekBar(this)
        seek.max = max - min
        seek.progress = value - min
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                caption.text = "$title: ${progress + min}"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                setInt(key, seek.progress + min)
            }
        })
        col.addView(caption)
        col.addView(seek)
        return col
    }

    private fun choiceRow(title: String, key: String, options: List<String>): LinearLayout {
        val current = options.getOrElse(prefs.getInt(key, 0)) { options[0] }
        return navRow(title, current) {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(options.toTypedArray()) { _, which ->
                    setInt(key, which)
                    go(screen)
                }
                .show()
        }
    }

    private fun swatchRow(): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(18), dp(10), dp(18), dp(10))
        row.setBackgroundColor(bg)
        val current = prefs.getInt("wall", 0)
        for (i in walls.indices) {
            val v = View(this)
            val shape = GradientDrawable()
            shape.setCornerRadius(dp(8).toFloat())
            shape.setColor(if (i == 0) bg else Color.parseColor(walls[i]))
            shape.setStroke(
                if (i == current) dp(3) else dp(1),
                if (i == current) Color.parseColor("#3390EC") else subColor
            )
            v.background = shape
            v.setOnClickListener { setInt("wall", i); go(Screen.CHAT_SETTINGS) }
            row.addView(v, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        return row
    }

    // ---------- Экран входа по паролю ----------
    private fun showLock() {
        val root = screenRoot()
        root.setBackgroundColor(bg)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setPadding(dp(32), dp(32), dp(32), dp(32))

        val title = TextView(this)
        title.text = "BLEmess заблокирован"
        title.setTextColor(textColor)
        title.textSize = 20f
        title.gravity = Gravity.CENTER

        val input = EditText(this)
        input.hint = "Пароль"
        input.setTextColor(textColor)
        input.setHintTextColor(subColor)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

        val ok = Button(this)
        ok.text = "Войти"
        ok.setTextColor(Color.WHITE)
        ok.isAllCaps = false
        ok.setBackgroundColor(Color.parseColor("#3390EC"))
        ok.setOnClickListener {
            if (input.text.toString() == (prefs.getString("pin", "") ?: "")) {
                unlocked = true
                render()
                if (username.isEmpty()) go(Screen.PROFILE)
            } else {
                toast("Неверный пароль")
            }
        }

        box.addView(title, LinearLayout.LayoutParams(-1, -2))
        box.addView(input, LinearLayout.LayoutParams(-1, -2))
        box.addView(ok, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        root.addView(box, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    // ---------- Список чатов ----------
    private fun buildChats() {
        val left = if (archiveView) {
            Btn("←", Color.TRANSPARENT) { archiveView = false; go(Screen.CHATS) }
        } else {
            Btn("☰", Color.TRANSPARENT) { openMenu() }
        }
        val right = Btn("🔍", Color.TRANSPARENT) { go(Screen.SEARCH) }
        page(if (archiveView) "Архив" else "BLEmess", left = left, right = right) { c ->
            listBox = c
            fillChatList()
        }
    }

    private fun openMenu() {
        val items = listOf(
            "Архив" to { archiveView = true; go(Screen.CHATS) },
            "Профиль" to { archiveView = false; go(Screen.PROFILE) },
            "Настройки" to { archiveView = false; go(Screen.SETTINGS) }
        )
        AlertDialog.Builder(this)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun fillChatList() {
        headerSub?.text = "Людей рядом: ${nearby.values.distinct().size}"
        val box = listBox ?: return
        box.removeAllViews()
        val pins = pinned()
        val arch = archived()
        val peers = chats.keys.reversed()
            .filter { chats[it].orEmpty().isNotEmpty() && (it in arch) == archiveView }
            .sortedByDescending { it in pins }

        if (peers.isEmpty()) {
            box.addView(emptyText(
                if (archiveView) "В архиве пусто."
                else "Чатов пока нет.\n\nНажмите 🔍 и найдите человека рядом по юзу."
            ))
            return
        }

        for (peer in peers) {
            val msgs = chats[peer].orEmpty()
            val last = msgs.last()
            val preview = if (last.mine) "Вы: ${last.text}" else last.text
            val row = personRow(peer, preview, online(peer), msgs.count { it.unread }) { openChat(peer) }
            row.setOnLongClickListener { chatMenu(peer); true }
            box.addView(row, LinearLayout.LayoutParams(-1, -2))
            addSeparator(box)
        }
    }

    private fun personRow(name: String, sub: String, isOnline: Boolean, badge: Int, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(10), dp(16), dp(10))
        row.setBackgroundColor(bg)
        row.setOnClickListener { onClick() }

        val frame = FrameLayout(this)
        frame.addView(avatar(name), FrameLayout.LayoutParams(dp(50), dp(50)))
        if (isOnline) {
            frame.addView(dot(), FrameLayout.LayoutParams(dp(14), dp(14), Gravity.BOTTOM or Gravity.END))
        }
        row.addView(frame, LinearLayout.LayoutParams(dp(50), dp(50)).apply { rightMargin = dp(12) })

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

        if (badge > 0) {
            val b = TextView(this)
            b.text = badge.toString()
            b.setTextColor(Color.WHITE)
            b.textSize = 12f
            b.gravity = Gravity.CENTER
            b.setPadding(dp(7), dp(2), dp(7), dp(2))
            val shape = GradientDrawable()
            shape.setCornerRadius(dp(20).toFloat())
            shape.setColor(Color.parseColor("#3390EC"))
            b.background = shape
            row.addView(b)
        }
        return row
    }

    private fun chatMenu(peer: String) {
        val isPinned = peer in pinned()
        val isArchived = peer in archived()
        val items = listOf(
            (if (isPinned) "Открепить" else "Закрепить") to { togglePin(peer) },
            (if (isArchived) "Вернуть из архива" else "В архив") to { toggleArchive(peer) },
            "Удалить чат" to { deleteChat(peer) }
        )
        AlertDialog.Builder(this)
            .setTitle("@$peer")
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun togglePin(peer: String) {
        val s = pinned()
        if (!s.add(peer)) s.remove(peer)
        saveSet("pinned", s)
        refreshLive()
    }

    private fun toggleArchive(peer: String) {
        val s = archived()
        if (!s.add(peer)) s.remove(peer)
        saveSet("archived", s)
        refreshLive()
    }

    private fun deleteChat(peer: String) {
        chats.remove(peer)
        refreshLive()
    }

    // ---------- Чат ----------
    private fun openChat(peer: String) {
        openPeer = peer
        if (!chats.containsKey(peer)) chats[peer] = mutableListOf()
        go(Screen.CHAT)
    }

    private fun buildChat() {
        val root = screenRoot()
        root.setBackgroundColor(chatBg)
        root.addView(titleBar("@$openPeer", "", backBtn(Screen.CHATS), null))

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(12), dp(12), dp(12), dp(12))
        msgBox = box

        val scroll = ScrollView(this)
        chatScroll = scroll
        scroll.addView(box)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(buildHintBar())
        root.addView(inputRow())
        setContentView(root)
        fillMessages()
        fillHint()
    }

    // Полоска над полем ввода: «Ответ» или «Изменение»
    private fun buildHintBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(barColor)
        bar.setPadding(dp(12), dp(4), dp(4), dp(4))
        bar.visibility = View.GONE

        val tv = TextView(this)
        tv.setTextColor(Color.WHITE)
        tv.textSize = 13f
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
        bar.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))

        val close = Button(this)
        close.text = "✕"
        close.setTextColor(Color.WHITE)
        close.setBackgroundColor(Color.TRANSPARENT)
        close.setOnClickListener { editingId = null; replyPreview = ""; fillHint() }
        bar.addView(close)

        hintBar = bar
        hintText = tv
        return bar
    }

    private fun fillHint() {
        val bar = hintBar ?: return
        val tv = hintText ?: return
        if (editingId != null) {
            bar.visibility = View.VISIBLE
            tv.text = "Изменение сообщения"
        } else if (replyPreview.isNotEmpty()) {
            bar.visibility = View.VISIBLE
            tv.text = "Ответ: $replyPreview"
        } else {
            bar.visibility = View.GONE
        }
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
        input.textSize = fontSize.toFloat()
        input.setPadding(dp(14), dp(10), dp(14), dp(10))
        inputField = input

        val send = Button(this)
        send.text = "➤"
        send.setTextColor(Color.WHITE)
        send.isAllCaps = false
        send.setBackgroundColor(Color.parseColor("#3390EC"))
        send.setOnClickListener { submit() }

        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(send)
        return row
    }

    private fun submit() {
        val field = inputField ?: return
        val text = field.text.toString().trim()
        if (text.isEmpty()) return
        val editing = editingId
        if (editing != null) {
            editMessage(openPeer, editing, text)
        } else {
            sendMessage(openPeer, text, replyPreview, "")
        }
        field.setText("")
        editingId = null
        replyPreview = ""
        fillMessages()
        fillHint()
    }

    private fun fillMessages() {
        headerSub?.text = if (online(openPeer)) "в сети" else "не в сети"
        val box = msgBox ?: return
        box.removeAllViews()
        for (m in chats[openPeer].orEmpty()) {
            box.addView(bubble(m, openPeer), LinearLayout.LayoutParams(-1, -2))
        }
        markRead(openPeer)
        chatScroll?.post { chatScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    // Отправляем собеседнику, что его сообщения прочитаны
    private fun markRead(peer: String) {
        val ids = JSONArray()
        for (m in chats[peer].orEmpty()) {
            if (m.unread) {
                m.unread = false
                ids.put(m.id)
            }
        }
        if (ids.length() > 0) sendPacket(newPacket("read", peer).put("ids", ids))
    }

    private fun smallLine(s: String): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = 12f
        tv.setTextColor(subColor)
        return tv
    }

    private fun bubble(m: Msg, peer: String): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = if (m.mine) Gravity.END else Gravity.START
        row.setPadding(0, dp(3), 0, dp(3))

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(12), dp(8), dp(12), dp(6))
        val shape = GradientDrawable()
        shape.setCornerRadius(dp(16).toFloat())
        shape.setColor(if (m.mine) myBubble else otherBubble)
        box.background = shape

        if (m.fwd.isNotEmpty()) box.addView(smallLine("Переслано от @${m.fwd}"))
        if (m.replyText.isNotEmpty()) box.addView(smallLine("↪ ${m.replyText}"))

        val body = TextView(this)
        body.text = if (m.edited) "${m.text} (изм.)" else m.text
        body.setTextColor(textColor)
        body.textSize = fontSize.toFloat()
        box.addView(body)

        if (m.mine) {
            val meta = TextView(this)
            meta.text = if (m.read) "✓✓" else "✓"
            meta.setTextColor(if (m.read) Color.parseColor("#4FC3F7") else subColor)
            meta.textSize = 11f
            meta.gravity = Gravity.END
            box.addView(meta, LinearLayout.LayoutParams(-1, -2))
        }

        row.addView(box, LinearLayout.LayoutParams(-2, -2))
        row.setOnLongClickListener { messageMenu(peer, m); true }
        return row
    }

    private fun messageMenu(peer: String, m: Msg) {
        val names = mutableListOf("Ответить", "Переслать", "Копировать")
        if (m.mine) {
            names.add("Изменить")
            names.add("Удалить")
        }
        AlertDialog.Builder(this)
            .setItems(names.toTypedArray()) { _, which ->
                when (names[which]) {
                    "Ответить" -> {
                        replyPreview = "@${m.from}: ${m.text}".take(80)
                        editingId = null
                        fillHint()
                    }
                    "Переслать" -> pickForward(m)
                    "Копировать" -> copyText(m.text)
                    "Изменить" -> {
                        editingId = m.id
                        replyPreview = ""
                        inputField?.setText(m.text)
                        fillHint()
                    }
                    "Удалить" -> confirmDelete(peer, m)
                    else -> {}
                }
            }
            .show()
    }

    private fun confirmDelete(peer: String, m: Msg) {
        val items = if (m.mine) arrayOf("Удалить у меня", "Удалить у меня и у @$peer")
                    else arrayOf("Удалить у меня")
        AlertDialog.Builder(this)
            .setTitle("Удалить сообщение?")
            .setItems(items) { _, which ->
                val both = m.mine && which == 1
                chats[peer]?.removeAll { it.id == m.id }
                if (both) sendPacket(newPacket("del", peer).put("target", m.id))
                refreshLive()
            }
            .show()
    }

    private fun pickForward(m: Msg) {
        val targets = chats.keys.toList()
        if (targets.isEmpty()) {
            toast("Нет чатов для пересылки")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Переслать в чат")
            .setItems(targets.map { "@$it" }.toTypedArray()) { _, which ->
                sendMessage(targets[which], m.text, "", if (m.mine) myName else m.from)
                toast("Переслано")
            }
            .show()
    }

    private fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("message", text))
        toast("Скопировано")
    }

    private fun editMessage(peer: String, id: String, text: String) {
        val m = chats[peer]?.firstOrNull { it.id == id } ?: return
        if (!m.mine) return
        m.text = text
        m.edited = true
        sendPacket(newPacket("edit", peer).put("target", id).put("text", text))
        refreshLive()
    }

    // ---------- Профиль ----------
    private fun buildProfile() {
        page("Профиль", sub = "Ваш аккаунт") { c ->
            val big = avatar(myName)
            big.textSize = 36f
            c.addView(big, LinearLayout.LayoutParams(dp(96), dp(96)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(20)
            })

            val nameTv = TextView(this)
            nameTv.text = "@$myName"
            nameTv.setTextColor(textColor)
            nameTv.textSize = 20f
            nameTv.typeface = Typeface.DEFAULT_BOLD
            nameTv.gravity = Gravity.CENTER
            c.addView(nameTv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

            c.addView(label("Ваш @юз (латиница, цифры и _, от 3 до 32 символов)"))
            val nameIn = EditText(this)
            nameIn.hint = "например: ivan_07"
            nameIn.setText(username)
            nameIn.setTextColor(textColor)
            nameIn.setHintTextColor(subColor)
            c.addView(nameIn, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(18); rightMargin = dp(18)
            })

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
                archiveView = false
                startMesh()
                go(Screen.CHATS)
            }
            c.addView(save, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(18); rightMargin = dp(18); topMargin = dp(16)
            })
        }
    }

    // ---------- Поиск ----------
    private fun buildSearch() {
        page("Поиск по @юзу", left = backBtn(Screen.CHATS)) { c ->
            val find = EditText(this)
            find.hint = "Введите @юз"
            find.setTextColor(textColor)
            find.setHintTextColor(subColor)
            c.addView(find, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(12)
            })

            val findBtn = Button(this)
            findBtn.text = "Найти"
            findBtn.setTextColor(Color.WHITE)
            findBtn.isAllCaps = false
            findBtn.setBackgroundColor(Color.parseColor("#F44336"))
            findBtn.setOnClickListener {
                val q = find.text.toString().trim().removePrefix("@")
                if (q.isEmpty()) return@setOnClickListener
                val found = (nearby.values + chats.keys).firstOrNull { it.equals(q, ignoreCase = true) }
                if (found != null) openChat(found) else toast("@$q не найден рядом")
            }
            c.addView(findBtn, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(8)
            })

            c.addView(label("Сейчас рядом"))
            val list = LinearLayout(this)
            list.orientation = LinearLayout.VERTICAL
            nearbyBox = list
            c.addView(list)
            fillNearby()
        }
    }

    private fun fillNearby() {
        val box = nearbyBox ?: return
        box.removeAllViews()
        val names = nearby.values.distinct()
        headerSub?.text = "Людей рядом: ${names.size}"
        if (names.isEmpty()) {
            box.addView(emptyText("Рядом пока никого нет.\nПопросите друга открыть BLEmess рядом с вами."))
            return
        }
        for (name in names) {
            val isOn = online(name)
            box.addView(
                personRow(name, if (isOn) "в сети" else "рядом", isOn, 0) { openChat(name) },
                LinearLayout.LayoutParams(-1, -2)
            )
            addSeparator(box)
        }
    }

    // ---------- Настройки ----------
    private fun buildSettings() {
        page("Настройки") { c ->
            addRow(c, navRow("Аккаунт", "Юз, удаление аккаунта") { go(Screen.ACCOUNT) })
            addRow(c, navRow("Настройки чатов", "Фон, размер текста, тема, иконка") { go(Screen.CHAT_SETTINGS) })
            addRow(c, navRow("Конфиденциальность", "Пароль, чёрный список, видимость") { go(Screen.PRIVACY) })
            addRow(c, navRow("Уведомления", "Громкость и звук") { go(Screen.NOTIFY) })
            addRow(c, navRow("Папки с чатами", "Создание и удаление папок") { go(Screen.FOLDERS) })
            addRow(c, navRow("Язык", prefs.getString("lang", "Русский") ?: "Русский") { go(Screen.LANGUAGE) })
        }
    }

    private fun buildAccount() {
        page("Аккаунт", left = backBtn(Screen.SETTINGS)) { c ->
            addRow(c, navRow("Юз", "@${username.ifEmpty { "не задан" }}") { go(Screen.PROFILE) })
            addRow(c, navRow("Удалить аккаунт", "Юз, чаты и настройки будут стёрты на этом телефоне", danger = true) {
                deleteAccount()
            })
        }
    }

    private fun deleteAccount() {
        AlertDialog.Builder(this)
            .setTitle("Удалить аккаунт?")
            .setMessage("Юз, чаты и настройки будут стёрты на этом телефоне.")
            .setPositiveButton("Удалить") { _, _ ->
                client.stopAdvertising()
                client.stopDiscovery()
                client.stopAllEndpoints()
                links.clear(); nearby.clear(); pending.clear(); seen.clear(); chats.clear()
                prefs.edit().clear().apply()
                myId = newId()
                dark = false
                archiveView = false
                go(Screen.PROFILE)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun buildChatSettings() {
        page("Настройки чатов", left = backBtn(Screen.SETTINGS)) { c ->
            c.addView(label("Фон чата (только для вас)"))
            addRow(c, swatchRow())
            addRow(c, seekRow("Размер текста", "fontSize", 12, 24, 16))
            addRow(c, switchRow("Тёмная тема", "dark", dark) { on ->
                dark = on
                go(Screen.CHAT_SETTINGS)
            })
            addRow(c, navRow("Иконка приложения", "Выбор своей иконки будет добавлен позже") {
                toast("Пока недоступно")
            })
        }
    }

    private fun buildPrivacy() {
        page("Конфиденциальность", left = backBtn(Screen.SETTINGS)) { c ->
            val pinOn = (prefs.getString("pin", "") ?: "").isNotEmpty()
            addRow(c, navRow("Пароль для входа", if (pinOn) "включён" else "выключен") { pinDialog() })
            addRow(c, navRow("Чёрный список", "Заблокировано: ${blocked().size}") { go(Screen.BLACKLIST) })
            addRow(c, choiceRow("Время захода", "seenBy", listOf("Все", "Никто", "Мои контакты")))
            addRow(c, choiceRow("Пересылка сообщений", "forwardBy", listOf("Все", "Никто")))
            addRow(c, choiceRow("Кто может начинать со мной общение?", "whoWrites",
                listOf("Все рядом", "Только мои контакты")))
        }
    }

    private fun pinDialog() {
        val input = EditText(this)
        input.hint = "Минимум 4 цифры"
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle("Пароль для входа")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val pin = input.text.toString()
                if (pin.length >= 4) {
                    setStr("pin", pin)
                    go(Screen.PRIVACY)
                } else {
                    toast("Нужно минимум 4 цифры")
                }
            }
            .setNeutralButton("Выключить") { _, _ ->
                setStr("pin", "")
                go(Screen.PRIVACY)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun buildBlacklist() {
        page("Чёрный список", left = backBtn(Screen.PRIVACY)) { c ->
            val input = EditText(this)
            input.hint = "@юз"
            input.setTextColor(textColor)
            input.setHintTextColor(subColor)
            c.addView(input, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(12)
            })

            val addBtn = Button(this)
            addBtn.text = "Заблокировать"
            addBtn.setTextColor(Color.WHITE)
            addBtn.isAllCaps = false
            addBtn.setBackgroundColor(Color.parseColor("#F44336"))
            addBtn.setOnClickListener {
                val name = input.text.toString().trim().removePrefix("@")
                if (name.isNotEmpty()) {
                    val s = blocked()
                    s.add(name)
                    saveSet("blacklist", s)
                    go(Screen.BLACKLIST)
                }
            }
            c.addView(addBtn, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(8)
            })

            val list = blocked().sorted()
            if (list.isEmpty()) {
                c.addView(emptyText("Список пуст"))
            }
            for (name in list) {
                addRow(c, navRow("@$name", "нажмите, чтобы разблокировать") {
                    val s = blocked()
                    s.remove(name)
                    saveSet("blacklist", s)
                    go(Screen.BLACKLIST)
                })
            }
        }
    }

    private fun buildNotify() {
        page("Уведомления", left = backBtn(Screen.SETTINGS)) { c ->
            addRow(c, seekRow("Громкость уведомлений", "volume", 0, 100, 80))
            addRow(c, switchRow("Звук уведомлений", "notifSound", true))
            val sound = prefs.getString("sound", "") ?: ""
            addRow(c, navRow("Мелодия уведомлений", if (sound.isEmpty()) "По умолчанию" else "Выбрана своя") {
                pickRingtone()
            })
        }
    }

    private fun pickRingtone() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        startActivityForResult(intent, 42)
    }

    private fun buildLanguage() {
        page("Язык", left = backBtn(Screen.SETTINGS)) { c ->
            val cur = prefs.getString("lang", "Русский") ?: "Русский"
            for (l in langs) {
                addRow(c, navRow(l, if (l == cur) "✓ выбран" else "") {
                    setStr("lang", l)
                    go(Screen.LANGUAGE)
                })
            }
            c.addView(smallText("Пока интерфейс на русском. Выбор сохранится и будет применён в следующем обновлении.")
                .apply { setPadding(dp(18), dp(12), dp(18), 0) })
        }
    }

    private fun buildFolders() {
        page("Папки с чатами", left = backBtn(Screen.SETTINGS)) { c ->
            addRow(c, navRow("Все чаты", "${chats.size} чатов") { archiveView = false; go(Screen.CHATS) })
            addRow(c, navRow("Архив", "${archived().size} чатов") { archiveView = true; go(Screen.CHATS) })

            c.addView(label("Свои папки"))
            val input = EditText(this)
            input.hint = "Название папки"
            input.setTextColor(textColor)
            input.setHintTextColor(subColor)
            c.addView(input, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(16); rightMargin = dp(16)
            })

            val create = Button(this)
            create.text = "Создать папку"
            create.setTextColor(Color.WHITE)
            create.isAllCaps = false
            create.setBackgroundColor(Color.parseColor("#3390EC"))
            create.setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val s = loadSet("folders")
                    s.add(name)
                    saveSet("folders", s)
                    go(Screen.FOLDERS)
                }
            }
            c.addView(create, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(8)
            })

            for (f in loadSet("folders").sorted()) {
                addRow(c, navRow(f, "нажмите, чтобы удалить") { confirmFolderDelete(f) })
            }
            c.addView(smallText("Назначение чатов в папки добавим в следующем обновлении.")
                .apply { setPadding(dp(18), dp(12), dp(18), 0) })
        }
    }

    private fun confirmFolderDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Удалить папку «$name»?")
            .setPositiveButton("Удалить") { _, _ ->
                val s = loadSet("folders")
                s.remove(name)
                saveSet("folders", s)
                go(Screen.FOLDERS)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- Сеть (Nearby Connections) ----------
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
                if (endpointId !in links.keys && endpointId !in pending) {
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
                if (result.status.isSuccess) {
                    links[endpointId] = nearby[endpointId] ?: endpointId
                }
                refreshLive()
            }
        }

        override fun onDisconnected(endpointId: String) {
            ui {
                links.remove(endpointId)
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
                    // пришли данные, которые не удалось разобрать
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    // ---------- Пакеты и сообщения ----------
    private fun newPacket(type: String, to: String): JSONObject =
        JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("type", type)
            .put("from", myName)
            .put("to", to)
            .put("ttl", 6)

    private fun sendPacket(p: JSONObject) {
        seen.add(p.getString("id"))
        forward(p, null)
    }

    private fun sendMessage(peer: String, text: String, replyText: String, fwd: String) {
        val p = newPacket("msg", peer)
            .put("text", text)
            .put("replyText", replyText)
            .put("fwd", fwd)
        addMsg(peer, Msg(p.getString("id"), myName, text, true, replyText = replyText, fwd = fwd))
        sendPacket(p)
        refreshLive()
    }

    private fun receiveMessage(p: JSONObject, from: String) {
        val msg = Msg(
            id = p.getString("id"),
            from = from,
            text = p.getString("text"),
            mine = false,
            replyText = p.optString("replyText", ""),
            fwd = p.optString("fwd", ""),
            unread = true
        )
        addMsg(from, msg)
        if (!(screen == Screen.CHAT && openPeer == from)) notify(from, msg.text)
    }

    private fun addMsg(peer: String, m: Msg) {
        val list = chats.remove(peer) ?: mutableListOf()
        list.add(m)
        chats[peer] = list
    }

    private fun handleIncoming(fromEndpoint: String, p: JSONObject) {
        val id = p.getString("id")
        if (!seen.add(id)) return
        val type = p.getString("type")
        val from = p.getString("from")
        val to = p.getString("to")
        val ttl = p.getInt("ttl")

        if (to == myName && from !in blocked()) {
            when (type) {
                "msg" -> receiveMessage(p, from)
                "edit" -> {
                    val target = p.getString("target")
                    chats[from]?.firstOrNull { it.id == target }?.let {
                        it.text = p.getString("text")
                        it.edited = true
                    }
                }
                "del" -> {
                    val target = p.getString("target")
                    chats[from]?.removeAll { it.id == target }
                }
                "read" -> {
                    val ids = p.getJSONArray("ids")
                    for (i in 0 until ids.length()) {
                        val target = ids.getString(i)
                        chats[from]?.firstOrNull { it.id == target }?.read = true
                    }
                }
                else -> {}
            }
            refreshLive()
        }

        if (ttl > 1) forward(p.put("ttl", ttl - 1), fromEndpoint)
    }

    // Пересылка всем соседям, кроме того, от кого пришёл пакет
    private fun forward(p: JSONObject, except: String?) {
        val bytes = p.toString().toByteArray()
        for (ep in links.keys.toList()) {
            if (ep != except) client.sendPayload(ep, Payload.fromBytes(bytes))
        }
    }

    private fun notify(from: String, text: String) {
        toast("@$from: $text")
        if (!pref("notifSound", true)) return
        val saved = prefs.getString("sound", "") ?: ""
        val uri = if (saved.isEmpty()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            Uri.parse(saved)
        }
        val r = RingtoneManager.getRingtone(this, uri) ?: return
        if (Build.VERSION.SDK_INT >= 28) r.volume = prefs.getInt("volume", 80) / 100f
        r.play()
    }
}
