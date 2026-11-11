package dev.aeris.island

import android.content.Context

/** Small, local-only preference store. No account or cloud sync is used. */
object AerisPrefs {
    private const val FILE = "aeris_local_settings"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun bool(context: Context, key: String, default: Boolean = false): Boolean =
        prefs(context).getBoolean(key, default)

    fun setBool(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun int(context: Context, key: String, default: Int): Int =
        prefs(context).getInt(key, default)

    fun setInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
    }

    fun string(context: Context, key: String, default: String = ""): String =
        prefs(context).getString(key, default) ?: default

    fun setString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }

    fun firstRunComplete(context: Context): Boolean = bool(context, "first_run_complete", false)
    fun markFirstRunComplete(context: Context) = setBool(context, "first_run_complete", true)

    fun islandEnabled(context: Context): Boolean = bool(context, "island_enabled", true)
}
