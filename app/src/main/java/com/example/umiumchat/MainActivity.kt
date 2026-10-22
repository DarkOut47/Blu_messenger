package com.example.umiumchat

import android.app.Activity
import android.os.Bundle
import android.widget.*
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.util.UUID

class MainActivity : Activity() {

    private val serviceId = "com.example.umiumchat"
    private val me = UUID.randomUUID().toString().take(6)   // короткий id этого телефона
    private val client by lazy { Nearby.getConnectionsClient(this) }
    private val connected = mutableSetOf<String>()           // с кем сейчас есть связь
    private val seen = HashSet<String>()                     // уже обработанные сообщения
    private lateinit var logView: TextView
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        logView = TextView(this).apply { text = "Ваш id: $me\n" }
        val scroll = ScrollView(this).apply { addView(logView) }
        input = EditText(this).apply { hint = "Сообщение всем" }
        val sendBtn = Button(this).apply {
            text = "Отправить"
            setOnClickListener { sendText(input.text.toString()); input.setText("") }
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(input)
        root.addView(sendBtn)
        setContentView(root)

        requestPermissions(
            arrayOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.NEARBY_WIFI_DEVICES"
            ), 1
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        startMesh()
    }

    private fun startMesh() {
        val options = Strategy.P2P_CLUSTER
        client.startAdvertising(me, serviceId, connectionCallback,
            AdvertisingOptions.Builder().setStrategy(options).build())
            .addOnFailureListener { log("Ошибка объявления: ${it.message}") }
        client.startDiscovery(serviceId, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(options).build())
            .addOnFailureListener { log("Ошибка поиска: ${it.message}") }
        log("Ищу людей рядом...")
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (endpointId !in connected) {
                client.requestConnection(me, endpointId, connectionCallback)
            }
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
                log("Связь установлена: ${endpointId}")
            }
        }
        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            log("Связь потеряна: ${endpointId}")
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
            .put("from", me)
            .put("text", text)
            .put("ttl", 5)                       // сколько переходов может пройти сообщение
        seen.add(msg.getString("id"))
        log("Я: $text")
        forward(msg, exceptEndpoint = null)
    }

    private fun handleIncoming(fromEndpoint: String, msg: JSONObject) {
        val id = msg.getString("id")
        if (!seen.add(id)) return                // уже видели — не пересылаем повторно
        log("${msg.getString("from")}: ${msg.getString("text")}")
        val ttl = msg.getInt("ttl")
        if (ttl > 1) {
            forward(msg.put("ttl", ttl - 1), exceptEndpoint = fromEndpoint)
        }
    }

    // Пересылка всем соседям, кроме того, от кого пришло сообщение
    private fun forward(msg: JSONObject, exceptEndpoint: String?) {
        val bytes = msg.toString().toByteArray()
        for (id in connected) {
            if (id != exceptEndpoint) client.sendPayload(id, Payload.fromBytes(bytes))
        }
    }

    private fun log(s: String) = runOnUiThread { logView.append("$s\n") }
}
