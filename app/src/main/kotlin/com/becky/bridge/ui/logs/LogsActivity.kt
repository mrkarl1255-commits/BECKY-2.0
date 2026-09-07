package com.becky.bridge.ui.logs

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.becky.bridge.R
import com.becky.bridge.databinding.ActivitySimpleBinding
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogEntry
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logs screen (section 13 of the spec).
 * Displays the in-memory log ring buffer and allows clearing/exporting it.
 */
class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySimpleBinding
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.screenTitle.text = getString(R.string.logs_title)
        binding.recyclerView.visibility = android.view.View.GONE

        val logsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scrollView = android.widget.ScrollView(this)
        scrollView.addView(logsContainer)
        (binding.root as LinearLayout).addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        BeckyLogger.entries.onEach { entries ->
            renderLogs(logsContainer, entries)
            binding.emptyText.visibility =
                if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.emptyText.text = getString(R.string.logs_empty)
        }.launchIn(lifecycleScope)
    }

    private fun renderLogs(container: LinearLayout, entries: List<LogEntry>) {
        container.removeAllViews()
        entries.takeLast(200).forEach { entry ->
            val row = TextView(this).apply {
                text = "${dateFormat.format(Date(entry.timestamp))} [${entry.level}] [${entry.category}] ${entry.message}"
                setTextColor(
                    when (entry.level.name) {
                        "ERROR" -> 0xFFF85149.toInt()
                        "WARNING" -> 0xFFD29922.toInt()
                        else -> 0xFF8B949E.toInt()
                    }
                )
                textSize = 11f
                setPadding(0, 6, 0, 6)
            }
            container.addView(row)
        }
    }
}
