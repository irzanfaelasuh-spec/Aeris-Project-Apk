package dev.aeris.island

import android.content.Context

object AerisPrefs {
    private const val FILE = "aeris_v3_local"
    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun bool(context: Context, key: String, default: Boolean = false) = prefs(context).getBoolean(key, default)
    fun setBool(context: Context, key: String, value: Boolean) { prefs(context).edit().putBoolean(key, value).apply() }
    fun int(context: Context, key: String, default: Int) = prefs(context).getInt(key, default)
    fun setInt(context: Context, key: String, value: Int) { prefs(context).edit().putInt(key, value).apply() }
    fun string(context: Context, key: String, default: String) = prefs(context).getString(key, default) ?: default
    fun setString(context: Context, key: String, value: String) { prefs(context).edit().putString(key, value).apply() }
}
