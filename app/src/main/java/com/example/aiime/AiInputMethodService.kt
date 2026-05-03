package com.example.aiime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AiInputMethodService : InputMethodService() {
    private val aiClient = AiClient()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var aiSuggestButton: Button
    private lateinit var candidateContainer: LinearLayout
    private lateinit var candidateButtons: List<Button>
    private var lastContextText: String = ""
    private var isGenerating = false
    private var lastGenerateAtMillis = 0L

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.ime_keyboard, null)

        aiSuggestButton = view.findViewById(R.id.aiSuggestButton)
        candidateContainer = view.findViewById(R.id.candidateContainer)
        candidateButtons = listOf(
            view.findViewById(R.id.candidateOne),
            view.findViewById(R.id.candidateTwo),
            view.findViewById(R.id.candidateThree)
        )

        bindQuickReplyKeys(view)
        bindActionKeys(view)
        hideCandidates()

        return view
    }

    private fun bindQuickReplyKeys(root: View) {
        val keyIds = listOf(
            R.id.keyQ, R.id.keyW, R.id.keyE, R.id.keyR, R.id.keyT,
            R.id.keyY, R.id.keyU, R.id.keyI, R.id.keyO, R.id.keyP,
            R.id.keyA, R.id.keyS, R.id.keyD, R.id.keyF, R.id.keyG,
            R.id.keyH, R.id.keyJ, R.id.keyK, R.id.keyL,
            R.id.keyZ, R.id.keyX, R.id.keyC, R.id.keyV, R.id.keyB,
            R.id.keyN, R.id.keyM
        )

        val quickReplies = listOf(
            "你好",
            "谢谢",
            "老师您好",
            "不好意思",
            "我想问一下",
            "可以",
            "不可以",
            "稍等一下",
            "我晚点回复你",
            "麻烦您了",
            "请问",
            "收到",
            "好的",
            "没问题",
            "我现在不太方便",
            "我稍后处理",
            "辛苦了",
            "感谢您"
        )

        keyIds.forEachIndexed { index, keyId ->
            val button = root.findViewById<Button>(keyId)
            val quickReply = quickReplies.getOrNull(index)

            if (quickReply == null) {
                button.visibility = View.GONE
                button.isEnabled = false
                button.setOnClickListener(null)
                return@forEachIndexed
            }

            button.visibility = View.VISIBLE
            button.isEnabled = true
            button.text = quickReply
            button.textSize = 12f
            button.maxLines = 2
            button.setOnClickListener {
                currentInputConnection?.commitText(quickReply, 1)
            }
        }
    }

    private fun bindActionKeys(root: View) {
        root.findViewById<Button>(R.id.spaceButton).setOnClickListener {
            currentInputConnection?.commitText(" ", 1)
        }

        root.findViewById<Button>(R.id.deleteButton).setOnClickListener {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        root.findViewById<Button>(R.id.enterButton).setOnClickListener {
            currentInputConnection?.commitText("\n", 1)
        }

        aiSuggestButton.setOnClickListener {
            requestAiSuggestions()
        }
    }

    private fun requestAiSuggestions() {
        val now = SystemClock.elapsedRealtime()
        if (isGenerating || now - lastGenerateAtMillis < GENERATE_THROTTLE_MS) {
            return
        }

        val contextText = currentInputConnection
            ?.getTextBeforeCursor(200, 0)
            ?.toString()
            ?: ""

        isGenerating = true
        lastGenerateAtMillis = now
        lastContextText = contextText
        aiSuggestButton.text = "生成中..."
        showLoadingCandidate()

        serviceScope.launch {
            try {
                val suggestions = aiClient.generateSuggestions(contextText)
                showCandidates(suggestions)
            } finally {
                isGenerating = false
                aiSuggestButton.text = "AI建议"
            }
        }
    }

    private fun showCandidates(candidates: List<String>) {
        candidateContainer.visibility = View.VISIBLE

        candidateButtons.forEachIndexed { index, button ->
            val candidate = candidates.getOrNull(index) ?: ""
            button.text = candidate
            button.visibility = if (candidate.isBlank()) View.GONE else View.VISIBLE
            button.isEnabled = candidate.isNotBlank()
            button.setOnClickListener {
                commitSuggestion(candidate)
                hideCandidates()
            }
        }
    }

    private fun showLoadingCandidate() {
        candidateContainer.visibility = View.VISIBLE
        candidateButtons.forEachIndexed { index, button ->
            button.text = if (index == 0) "生成中..." else ""
            button.visibility = if (index == 0) View.VISIBLE else View.GONE
            button.isEnabled = false
            button.setOnClickListener(null)
        }
    }

    private fun commitSuggestion(suggestion: String) {
        val inputConnection = currentInputConnection ?: return
        inputConnection.commitText(suggestion.removeExistingPrefix(), 1)
        lastContextText = ""
    }

    private fun String.removeExistingPrefix(): String {
        if (lastContextText.isBlank()) {
            return this
        }

        return if (startsWith(lastContextText)) {
            removePrefix(lastContextText).trimStart()
        } else {
            this
        }
    }

    private fun hideCandidates() {
        candidateContainer.visibility = View.GONE
        candidateButtons.forEach { button ->
            button.text = ""
            button.isEnabled = true
            button.setOnClickListener(null)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val GENERATE_THROTTLE_MS = 2_000L
    }
}
