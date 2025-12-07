package br.edu.utfpr.gestaofaturasicoob.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Utility class to manage month filter persistence across fragments
 */
object MonthFilterManager {
    
    private const val PREFS_NAME = "month_filter_prefs"
    private const val KEY_SELECTED_MONTH = "selected_month"
    
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Save the selected month filter
     */
    fun saveSelectedMonth(context: Context, month: String?) {
        val prefs = getSharedPreferences(context)
        prefs.edit()
            .putString(KEY_SELECTED_MONTH, month)
            .apply()
    }
    
    /**
     * Get the saved month filter
     */
    fun getSelectedMonth(context: Context): String? {
        val prefs = getSharedPreferences(context)
        return prefs.getString(KEY_SELECTED_MONTH, null)
    }
    
    /**
     * Clear the saved month filter
     */
    fun clearSelectedMonth(context: Context) {
        val prefs = getSharedPreferences(context)
        prefs.edit()
            .remove(KEY_SELECTED_MONTH)
            .apply()
    }
}
