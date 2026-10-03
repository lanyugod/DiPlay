package com.shihab.diplay.android6probe

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import android.media.*
import android.net.VpnService
import android.os.*
import android.util.Log
import android.view.*
import android.widget.*
import com.shilapi.xcertplay.transport.*
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Every state-changing probe requires its own button; opening the app only displays its identity. */
class ProbeActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var output: TextView
    private lateinit var video: SurfaceView
    private val work = Executors.newSingleThreadExecutor()
    private val usb by lazy { getSystemService(UsbManager::class.java) }
    private val permissionAction by lazy { "$packageName.USB.${java.util.UUID.randomUUID()}" }
    private var pump: LegacyUsbReadPump? = null
    private var consumer: Thread? = null
    private val consuming = AtomicBoolean(false)
    private val decoding = AtomicBoolean(false)
    private var videoThread: Thread? = null
    private var vpn: ProbeVpnService? = null
    private var bound = false
    @Volatile private var surfaceGeneration = 0
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            vpn = (binder as ProbeVpnService.Handle).service
            runCatching { vpn!!.establishProbe() }.onSuccess { log("G4 establish=$it (no routes)") }.onFailure { log("G4 failure=${it.message}") }
        }
        override fun onServiceDisconnected(name: ComponentName) { vpn = null }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == permissionAction) {
                log("G1a permission=${intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)}")
                work.execute { usb.deviceList.values.filter { it.vendorId == 0x05ac && usb.hasPermission(it) }.forEach { device ->
                    val opened = usb.openDevice(device)
                    log("G1a open=${opened != null}"); opened?.close()
                } }
            } else snapshot()
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(12, 12, 12, 12) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun button(title: String, action: () -> Unit) {
            column.addView(Button(this).apply { text = title; setOnClickListener { runCatching(action).onFailure { log("Test failed: ${it.message}") } } })
        }
        button("USB只读快照") { snapshot() }
        button("G1a 请求USB权限并open") { selectPhone { device ->
            if (usb.hasPermission(device)) work.execute { val opened = usb.openDevice(device); log("G1a open=${opened != null}"); opened?.close() }
            else usb.requestPermission(device, PendingIntent.getBroadcast(this, 0, Intent(permissionAction).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)))
        } }
        button("G1b 主动切换iPhone（影响原车投屏）") { confirm("将发送CarPlay vendor request 0x52，可能导致USB重枚举。请先退出原车投屏。") {
            selectPhone { device -> work.execute { runCatching {
                check(usb.hasPermission(device)) { "先授权USB" }
                val opened = usb.openDevice(device) ?: error("open失败")
                try { val bytes = ByteArray(1); log("G1b vendor bytes=${opened.controlTransfer(0xc0, 0x52, 0, 4, bytes, 1, 1000)}") }
                finally { opened.close() }
                log("请重新读取快照并授权；不自动selectConfiguration或claim")
            }.onFailure { log("G1b failure=${it.message}") } } }
        } }
        button("G1c 选择USBMUX并启动旧queue（影响USB）") { confirm("将选择含USBMUX的配置并claim(false)，持续16KiB旧queue。不发送协议数据。") {
            selectPhone { device -> work.execute { startPump(device) } }
        } }
        button("G1c 关闭USB并报告耗时") { work.execute { closePump() } }
        button("G2 H.264 720p/30 Surface解码（10分钟）") { startVideo() }
        button("停止视频") { stopVideo() }
        button("G3 低音量短测试音（2秒）") { work.execute { sound() } }
        button("G4 VPN授权/建立（无路由）") {
            val consent = VpnService.prepare(this)
            if (consent != null) startActivityForResult(consent, 4) else startVpn()
        }
        button("G4 释放VPN") { stopVpn() }
        root.addView(ScrollView(this).apply { addView(column) }, LinearLayout.LayoutParams(0, -1, 0.44f))
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 0, 0, 0) }
        video = SurfaceView(this).also { it.holder.addCallback(this) }
        results.addView(video, LinearLayout.LayoutParams(-1, 0, 0.6f))
        output = TextView(this).apply { textSize = 12f; setTextIsSelectable(true) }
        results.addView(ScrollView(this).apply { addView(output) }, LinearLayout.LayoutParams(-1, 0, 0.4f))
        root.addView(results, LinearLayout.LayoutParams(0, -1, 0.56f))
        setContentView(root)
        val filter = IntentFilter(permissionAction).apply { addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
        log("uid=${Process.myUid()} model=${Build.MODEL} device=${Build.DEVICE} API=${Build.VERSION.SDK_INT}; 未启动任何测试")
    }
    private fun confirm(message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setMessage(message).setNegativeButton("取消", null).setPositiveButton("执行") { _, _ -> action() }.show()
    }
    private fun selectPhone(action: (UsbDevice) -> Unit) {
        val phones = usb.deviceList.values.filter { it.vendorId == 0x05ac }
        if (phones.size == 1) action(phones.single()) else log("需且仅需一个Apple USB设备，当前=${phones.size}")
    }
    private fun snapshot() {
        usb.deviceList.values.forEach { device ->
            log("USB vid=${device.vendorId.toString(16)} pid=${device.productId.toString(16)} permission=${usb.hasPermission(device)}")
            for (c in 0 until device.configurationCount) {
                val config = device.getConfiguration(c)
                for (i in 0 until config.interfaceCount) {
                    val face = config.getInterface(i)
                    val endpoints = (0 until face.endpointCount).map { face.getEndpoint(it) }.joinToString { "${it.address.toString(16)}/${it.type}/${it.maxPacketSize}" }
                    log("config=${config.id} iface=${face.id}/${face.alternateSetting} class=${face.interfaceClass.toString(16)}/${face.interfaceSubclass.toString(16)}/${face.interfaceProtocol.toString(16)} endpoints=$endpoints")
                }
            }
        }
        log("USB snapshot complete (serial omitted)")
    }
    private fun startPump(device: UsbDevice) {
        if (pump != null) { log("请先关闭当前queue"); return }
        var opened: UsbDeviceConnection? = null
        try {
            LegacyUsbReadPump.assertReconnectAllowed()
            check(usb.hasPermission(device)) { "先授权USB" }
            val config = (0 until device.configurationCount).map(device::getConfiguration).firstOrNull { cfg ->
                (0 until cfg.interfaceCount).map(cfg::getInterface).any { it.interfaceClass == 255 && it.interfaceSubclass == 254 && it.interfaceProtocol == 2 }
            } ?: error("没有USBMUX配置")
            val face = (0 until config.interfaceCount).map(config::getInterface).first { it.interfaceClass == 255 && it.interfaceSubclass == 254 && it.interfaceProtocol == 2 }
            val endpoint = (0 until face.endpointCount).map(face::getEndpoint).first { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN }
            val owned = usb.openDevice(device) ?: error("open失败"); opened = owned
            val selected = owned.setConfiguration(config)
            log("G1c setConfiguration config=${config.id} result=$selected")
            if (!selected) { val active = ByteArray(1); check(owned.controlTransfer(0x80, 8, 0, 0, active, 1, 1000) == 1 && (active[0].toInt() and 255) == config.id) { "无法确认配置生效" } }
            check(owned.claimInterface(face, false)) { "claim失败，请退出原车投屏后重试" }
            val request = UsbRequest()
            val next = LegacyUsbReadPump(object : LegacyUsbDriver {
                override fun initialize() = request.initialize(owned, endpoint)
                @Suppress("DEPRECATION") override fun queue(buffer: ByteBuffer, length: Int) = request.queue(buffer, length)
                override fun waitForCompletion() = owned.requestWait() === request
                override fun cancel() = request.cancel()
                override fun closeConnection() { owned.close() }
                override fun close() { request.close() }
            }, diagnostic = ::log)
            pump = next; opened = null; consuming.set(true)
            consumer = Thread({
                var bytes = 0L; var timeouts = 0
                while (consuming.get()) when (val result = next.read(250)) {
                    is UsbReadResult.Data -> { bytes += result.bytes.size; log("G1c received bytesTotal=$bytes") }
                    UsbReadResult.Timeout -> { timeouts++; if (timeouts % 20 == 0) log("G1c consumer timeouts=$timeouts (request stays queued)") }
                    is UsbReadResult.Terminated -> { log("G1c terminal=${result.cause.message}"); return@Thread }
                }
            }, "probe-usb-consumer").apply { start() }
            log("G1c queue started config=${config.id}; read timeouts never cancel")
        } catch (error: Exception) { log("G1c failure=${error.message}"); opened?.close() }
    }
    private fun closePump() {
        consuming.set(false)
        val old = pump; pump = null
        val started = System.nanoTime(); old?.close()
        consumer?.join(1000); consumer = null
        log("G1c closeMs=${(System.nanoTime() - started) / 1_000_000} reconnectAllowed=${runCatching { LegacyUsbReadPump.assertReconnectAllowed() }.isSuccess}")
    }
    private fun sound() {
        var track: AudioTrack? = null
        val audio = getSystemService(AudioManager::class.java)
        val listener = AudioManager.OnAudioFocusChangeListener { change -> log("G3 focus=$change"); if (change < 0) track?.setVolume(0f) }
        @Suppress("DEPRECATION") val granted = audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        try {
            check(granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "音频焦点被拒绝" }
            track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(9600).setTransferMode(AudioTrack.MODE_STREAM).build()
            val samples = ShortArray(96000) { (kotlin.math.sin(it * 2.0 * Math.PI * 440 / 48000) * 2000).toInt().toShort() }
            track.play(); val count = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            log("G3 wroteSamples=$count rate=48000; 请记录实际声音/失焦")
        } catch (error: Exception) { log("G3 failure=${error.message}") }
        finally { runCatching { track?.stop() }; track?.release(); @Suppress("DEPRECATION") audio.abandonAudioFocus(listener) }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4) { if (resultCode == RESULT_OK) startVpn() else log("G4 consent denied; 可手动重试") }
    }
    private fun startVpn() {
        if (bound) { log("G4 already bound; 请先释放"); return }
        bound = bindService(Intent(this, ProbeVpnService::class.java), connection, BIND_AUTO_CREATE)
        log("G4 bind=$bound")
    }
    private fun stopVpn() { vpn?.stopProbe(); vpn = null; if (bound) { unbindService(connection); bound = false }; log("G4 released") }
    private fun startVideo() {
        if (videoThread?.isAlive == true) { log("视频尚未退出，请等待"); return }
        if (!decoding.compareAndSet(false, true)) return
        videoThread = Thread({
            val stopAt = System.nanoTime() + 600_000_000_000L
            try { while (decoding.get() && System.nanoTime() < stopAt) decodeSample() }
            catch (error: Exception) { log("G2 failure=${error.message}") }
            finally { decoding.set(false); log("G2 stopped") }
        }, "probe-avc").apply { start() }
    }
    private fun decodeSample() {
        val holderSurface = video.holder.surface
        if (!holderSurface.isValid) { Thread.sleep(50); return }
        val generation = surfaceGeneration
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            resources.openRawResourceFd(R.raw.avc720).use { asset -> extractor.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length) }
            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            log("G2 decoder=${codec.name} sample=${format.getInteger(MediaFormat.KEY_WIDTH)}x${format.getInteger(MediaFormat.KEY_HEIGHT)} Surface=${video.width}x${video.height} generation=$generation")
            codec.configure(format, holderSurface, null, 0); codec.start()
            val info = MediaCodec.BufferInfo()
            val began = System.nanoTime(); var ended = false; var first = true
            while (decoding.get() && generation == surfaceGeneration && holderSurface.isValid) {
                if (!ended) {
                    val input = codec.dequeueInputBuffer(10_000)
                    if (input >= 0) {
                        val length = extractor.readSampleData(codec.getInputBuffer(input)!!, 0)
                        if (length < 0) { codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); ended = true }
                        else { codec.queueInputBuffer(input, 0, length, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                val frame = codec.dequeueOutputBuffer(info, 10_000)
                if (frame >= 0) {
                    if (info.size > 0) {
                        val delay = began + info.presentationTimeUs * 1000 - System.nanoTime()
                        if (delay > 0) Thread.sleep(delay / 1_000_000, (delay % 1_000_000).toInt())
                        codec.releaseOutputBuffer(frame, true)
                        if (first) { first = false; log("G2 firstFrameMs=${(System.nanoTime() - began) / 1_000_000}") }
                    } else codec.releaseOutputBuffer(frame, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        } finally { runCatching { codec?.stop() }; codec?.release(); extractor.release() }
    }
    private fun stopVideo() { decoding.set(false) }
    override fun surfaceCreated(holder: SurfaceHolder) { surfaceGeneration++; log("Surface created generation=$surfaceGeneration") }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { surfaceGeneration++; log("Surface size=${width}x$height generation=$surfaceGeneration") }
    override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceGeneration++; log("Surface destroyed generation=$surfaceGeneration") }
    private fun log(message: String) {
        Log.i("DiPlay-API23-Probe", message)
        runOnUiThread { if (!isDestroyed) { output.append("$message\n"); if (output.text.length > 12000) output.text = output.text.takeLast(10000) } }
    }
    override fun onDestroy() {
        unregisterReceiver(receiver); stopVideo(); stopVpn()
        work.execute { closePump() }; work.shutdown()
        super.onDestroy()
    }
}
