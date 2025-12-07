package br.edu.utfpr.gestaofaturasicoob.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for date operations
 */
object DateUtils {
    
    private const val ISO_DATE_FORMAT = "yyyy-MM-dd"
    
    private val isoDateFormat = SimpleDateFormat(ISO_DATE_FORMAT, Locale.getDefault())
    
    /**
     * Get days until due date
     */
    fun getDaysUntilDue(dueDate: String): Int {
        return try {
            val due = isoDateFormat.parse(dueDate)
            val now = Date()
            val diff = due?.time?.minus(now.time) ?: 0
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            0
        }
    }
    
}
