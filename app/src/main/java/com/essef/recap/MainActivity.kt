package com.essef.recap

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var th: ThemeColors
    private lateinit var content: FrameLayout
    private lateinit var navBar: LinearLayout
    private var currentTab = 1 // Recents is the home tab
    private val tabViews = arrayOfNulls<View>(5)

    companion object {
        private const val REQ_ROLE = 41
        private const val REQ_PERMS = 42
        val PERMS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppIcon.apply(this)
        th = Themes.get(this)
        currentTab = intent?.getIntExtra("tab", 1) ?: 1

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(th.bg)

        // Header: wordmark + profile button (opens Settings — it really is the settings entry)
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(22), dp(12), dp(22), dp(2))
        // App logo — its background follows the color theme, like the launcher icon.
        val logo = ImageView(this)
        logo.setImageResource(AppIcon.drawableFor(th.name))
        logo.layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        header.addView(logo)
        val wm = LinearLayout(this)
        wm.orientation = LinearLayout.HORIZONTAL
        val wmLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        wmLp.leftMargin = dp(10)
        wm.layoutParams = wmLp
        val re = text(this, "Re", 27f, th.onSurface, bold = true)
        val cap = text(this, "cap", 27f, th.primary, bold = true)
        wm.addView(re); wm.addView(cap)
        header.addView(wm)
        header.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        val avatarBtn = avatarView(this, "S", th.primary, Color.WHITE, 36, 14f)
        avatarBtn.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        header.addView(avatarBtn)
        root.addView(header)

        // Setup card (only while the app is not yet the default phone app / permissions missing)
        val setupCard = buildSetupCard()
        if (setupCard != null) root.addView(setupCard)

        content = FrameLayout(this)
        content.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(content)

        navBar = buildNav()
        root.addView(navBar)

        setContentView(root)
        showTab(currentTab)

        if (missingPerms().isNotEmpty()) requestPermissions(missingPerms(), REQ_PERMS)
        Thread { RecStore.autoDelete(this) }.start()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val t = intent?.getIntExtra("tab", -1) ?: -1
        if (t in 0..4) showTab(t)
    }

    override fun onResume() {
        super.onResume()
        // Returning from Settings may have changed the theme.
        val now = Themes.get(this)
        if (now.name != th.name || now.bg != th.bg) { recreate(); return }
        // New calls may have landed while we were away (or during a call):
        // rebuild the visible data tab so Recents / Recordings are never
        // stale. The keypad keeps its state (a half-typed number survives).
        if (currentTab != 0) {
            tabViews[currentTab] = null
            showTab(currentTab)
        }
    }

    override fun onStart() {
        super.onStart()
        FloatBubble.hide()
    }

    override fun onStop() {
        super.onStop()
        // Mid-call and the user left for a game/fullscreen app: keep the call
        // reachable through the floating bubble.
        FloatBubble.refresh(applicationContext)
    }

    private fun isDefaultDialer(): Boolean {
        return try {
            val rm = getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } catch (e: Exception) { false }
    }

    private fun missingPerms(): Array<String> =
        PERMS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

    private fun buildSetupCard(): View? {
        val needRole = !isDefaultDialer()
        val needPerms = missingPerms().isNotEmpty()
        if (!needRole && !needPerms) return null
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(th.container, 20, this)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(18), dp(10), dp(18), dp(2))
        card.layoutParams = lp
        card.setPadding(dp(16), dp(12), dp(16), dp(12))
        val msg = when {
            needRole && needPerms -> "Recap needs to be your phone app to place calls, record them, and read voicemail."
            needRole -> "Set Recap as your phone app to place calls, record them, and read voicemail."
            else -> "A few permissions are still needed for calls, recordings, and contacts."
        }
        card.addView(text(this, msg, 13.5f, th.onSurface))
        val btn = text(this, if (needRole) "Set as phone app" else "Grant permissions", 14f, Color.WHITE, bold = true)
        btn.background = rounded(th.primary, 999, this)
        btn.setPadding(dp(16), dp(9), dp(16), dp(9))
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        blp.topMargin = dp(10)
        btn.layoutParams = blp
        btn.setOnClickListener {
            if (needRole) {
                try {
                    val rm = getSystemService(RoleManager::class.java)
                    startActivityForResult(rm!!.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQ_ROLE)
                } catch (e: Exception) {
                    val i = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    i.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                    try { startActivity(i) } catch (e2: Exception) { }
                }
            } else {
                requestPermissions(missingPerms(), REQ_PERMS)
            }
        }
        card.addView(btn)
        return card
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ROLE) recreate()
    }

    private fun buildNav(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(18), dp(10), dp(18), dp(18))
        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.background = rounded(th.softContainer, 999, this)
        pill.setPadding(dp(5), dp(5), dp(5), dp(5))
        pill.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val items = listOf(
            Triple(R.drawable.ic_keypad, "Keypad", 0),
            Triple(R.drawable.ic_clock, "Recents", 1),
            Triple(R.drawable.ic_user, "Contacts", 2),
            Triple(R.drawable.ic_voicemail, "Voicemail", 3),
            Triple(R.drawable.ic_mic, "Recordings", 4)
        )
        for ((res, label, idx) in items) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.setPadding(0, dp(6), 0, dp(5))
            item.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (idx == currentTab) item.background = rounded(th.surface, 999, this)
            val tint = if (idx == currentTab) th.primary else th.muted
            val iv = ImageView(this)
            iv.setImageResource(res)
            iv.setColorFilter(tint)
            iv.layoutParams = LinearLayout.LayoutParams(dp(21), dp(21))
            item.addView(iv)
            val tv = text(this, label, 10.5f, tint, bold = idx == currentTab)
            val tlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            tlp.topMargin = dp(2)
            tv.layoutParams = tlp
            item.addView(tv)
            item.setOnClickListener { showTab(idx) }
            pill.addView(item)
        }
        bar.addView(pill)
        return bar
    }

    private fun showTab(idx: Int) {
        currentTab = idx
        if (tabViews[idx] == null) {
            tabViews[idx] = when (idx) {
                0 -> KeypadTab(this, th).create { num -> Calls.place(this, num) }
                1 -> RecentsTab(this, th).create()
                2 -> ContactsTab(this, th).create { num -> Calls.place(this, num) }
                3 -> VoicemailTab(this, th).create()
                else -> RecordingsTab(this, th).create()
            }
        }
        content.removeAllViews()
        content.addView(
            tabViews[idx],
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        // rebuild nav to move the active pill
        val parent = navBar.parent as ViewGroup
        val pos = parent.indexOfChild(navBar)
        parent.removeView(navBar)
        navBar = buildNav()
        parent.addView(navBar, pos)
    }
}
