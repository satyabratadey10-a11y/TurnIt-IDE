package com.turnit.ide.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.RotateDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.turnit.ide.R
import com.turnit.ide.shell.ShellEngine

class MainShellScreen : Fragment() {

    // -------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------

    private lateinit var drawerLayout      : DrawerLayout
    private lateinit var navigationView    : NavigationView
    private lateinit var drawerToggle      : ActionBarDrawerToggle
    private lateinit var messageContainer  : LinearLayout
    private lateinit var scrollView        : ScrollView
    private lateinit var inputField        : EditText
    private lateinit var sendButton        : ImageButton
    private lateinit var hamburgerButton   : ImageButton

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private lateinit var shellEngine       : ShellEngine
    private val mainHandler                = Handler(Looper.getMainLooper())
    private var neonAnimator               : ValueAnimator? = null

    // Populated from your app's real storage path. Adjust if your extraction
    // logic writes to a different subdirectory.
    private val rootfsPath: String by lazy {
        "${requireContext().filesDir.absolutePath}/ubuntu-rootfs"
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_main_shell, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        setupNavigationDrawer()
        setupNeonBorderAnimation()
        setupInputHandlers()
        initShellEngine()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        neonAnimator?.cancel()
        shellEngine.stop()
    }

    // -------------------------------------------------------------------------
    // View Binding
    // -------------------------------------------------------------------------

    private fun bindViews(root: View) {
        drawerLayout     = root.findViewById(R.id.drawerLayout)
        navigationView   = root.findViewById(R.id.navigationView)
        messageContainer = root.findViewById(R.id.messageContainer)
        scrollView       = root.findViewById(R.id.scrollView)
        inputField       = root.findViewById(R.id.inputField)
        sendButton       = root.findViewById(R.id.sendButton)
        hamburgerButton  = root.findViewById(R.id.hamburgerButton)
    }

    // -------------------------------------------------------------------------
    // Navigation Drawer — hamburger menu with 3 items
    // -------------------------------------------------------------------------

    private fun setupNavigationDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            requireActivity(),
            drawerLayout,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        hamburgerButton.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.nav_new_chat    -> handleNewChat()
                R.id.nav_history     -> handleHistory()
                R.id.nav_api_settings -> handleApiKeySettings()
            }
            true
        }
    }

    private fun handleNewChat() {
        shellEngine.stop()
        messageContainer.removeAllViews()
        appendBubble("[System] New session started.", isUser = false)
        shellEngine.startProot(rootfsPath)
    }

    private fun handleHistory() {
        // Wire to your history screen / back-stack navigation here.
        appendBubble("[System] History not yet implemented.", isUser = false)
    }

    private fun handleApiKeySettings() {
        // Wire to your API key settings screen here.
        appendBubble("[System] API Key Settings not yet implemented.", isUser = false)
    }

    // -------------------------------------------------------------------------
    // Rotating RGB Neon Border Animation
    // Requires: res/drawable/bg_neon_border_rotate.xml (RotateDrawable wrapping
    // a gradient ring shape) and res/drawable/bg_glass_input.xml as the inner
    // background. See note below for the XML stubs.
    // -------------------------------------------------------------------------

    private fun setupNeonBorderAnimation() {
        val rotateDrawable = inputField.background as? RotateDrawable ?: run {
            // Graceful fallback: if the background isn't a RotateDrawable
            // (e.g., during layout preview), skip animation without crashing.
            return
        }

        neonAnimator = ValueAnimator.ofInt(0, 10000).apply {
            duration       = 2400
            repeatCount    = ValueAnimator.INFINITE
            repeatMode     = ValueAnimator.RESTART
            interpolator   = LinearInterpolator()

            addUpdateListener { animator ->
                val level = animator.animatedValue as Int
                rotateDrawable.level = level
                // Cycle hue across the RGB spectrum as the border rotates.
                // The drawable's color filter shifts the gradient hue so the
                // neon effect cycles red → green → blue → red continuously.
                val hue   = (level / 10000f) * 360f
                val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                rotateDrawable.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            }
        }
        neonAnimator?.start()
    }

    // -------------------------------------------------------------------------
    // Input Handling
    // -------------------------------------------------------------------------

    private fun setupInputHandlers() {
        sendButton.setOnClickListener { dispatchInput() }

        inputField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                dispatchInput()
                true
            } else false
        }
    }

    private fun dispatchInput() {
        val raw = inputField.text.toString().trim()
        if (raw.isEmpty()) return

        inputField.text.clear()
        appendBubble(raw, isUser = true)

        if (shellEngine.isSessionActive) {
            shellEngine.sendInput(raw)
        } else {
            appendBubble(
                "[ShellEngine-V2] No active session. Tap 'New Chat' to start PRoot.",
                isUser = false
            )
        }
    }

    // -------------------------------------------------------------------------
    // ShellEngine Initialisation
    // Wire order: setOutputCallback FIRST, then startProot.
    // Boolean guard uses == true to avoid ! on a property access that could
    // theoretically be on a stale reference after a config change.
    // -------------------------------------------------------------------------

    private fun initShellEngine() {
        shellEngine = ShellEngine(requireContext())

        // Step 1 — register callback BEFORE starting the process so no output
        // lines are emitted before the UI is ready to receive them.
        shellEngine.setOutputCallback { line ->
            mainHandler.post { appendBubble(line, isUser = false) }
        }

        // Step 2 — start only if not already running (safe re-entry guard).
        if (shellEngine.isSessionActive == true) {
            appendBubble("[ShellEngine-V2] Session already active.", isUser = false)
        } else {
            shellEngine.startProot(rootfsPath)
        }
    }

    // -------------------------------------------------------------------------
    // Bubble Rendering — Glassmorphism
    // bg_glass_bubble must be defined in res/drawable/bg_glass_bubble.xml.
    // User bubbles align END (right), system/AI bubbles align START (left).
    // -------------------------------------------------------------------------

    private fun appendBubble(text: String, isUser: Boolean) {
        val bubble = TextView(requireContext()).apply {
            this.text    = text
            textSize     = 14f
            setPadding(28, 18, 28, 18)
            setBackgroundResource(R.drawable.bg_glass_bubble)

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity  = if (isUser) Gravity.END else Gravity.START
                topMargin = 10
                leftMargin  = if (isUser) 80 else 16
                rightMargin = if (isUser) 16 else 80
            }
            layoutParams = lp

            setTextColor(
                if (isUser)
                    requireContext().getColor(R.color.bubble_text_user)
                else
                    requireContext().getColor(R.color.bubble_text_ai)
            )

            // Align text content inside the bubble to match alignment.
            textAlignment = if (isUser) View.TEXT_ALIGNMENT_TEXT_END
                            else        View.TEXT_ALIGNMENT_TEXT_START
        }

        messageContainer.addView(bubble)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
