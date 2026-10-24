package dev.alaev.rezkatv.rezka

import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.leanback.preference.LeanbackSettingsFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.bumptech.glide.Glide
import dev.alaev.rezkatv.BuildConfig
import dev.alaev.rezkatv.R
import kotlin.concurrent.thread

class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(android.R.id.content, SettingsFragment()).commit()
        }
    }

    class SettingsFragment : LeanbackSettingsFragmentCompat() {
        override fun onPreferenceStartInitialScreen() = startPreferenceFragment(PrefsFragment())

        override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference) = false

        override fun onPreferenceStartScreen(caller: PreferenceFragmentCompat, pref: PreferenceScreen): Boolean {
            startPreferenceFragment(PrefsFragment().apply {
                arguments = Bundle().apply { putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key) }
            })
            return true
        }
    }

    class PrefsFragment : LeanbackPreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings, rootKey)
            findPreference<Preference>("version")?.summary = BuildConfig.VERSION_NAME
            findPreference<Preference>("open_site")?.setOnPreferenceClickListener {
                requireActivity().openSite()
                true
            }
            findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
                val context = requireContext().applicationContext
                RezkaClient.get(context).clearCaches()
                Glide.get(context).clearMemory()
                thread { Glide.get(context).clearDiskCache() }
                Toast.makeText(context, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                true
            }
            findPreference<Preference>("login")?.setOnPreferenceClickListener {
                requireActivity().openLogin()
                true
            }
            findPreference<Preference>("logout")?.setOnPreferenceClickListener {
                RezkaClient.get(requireContext()).logout()
                CookieManager.getInstance().removeAllCookies(null)
                Toast.makeText(requireContext(), R.string.logout_done, Toast.LENGTH_SHORT).show()
                updateAccount()
                true
            }
        }

        override fun onResume() {
            super.onResume()
            updateAccount()
        }

        private fun updateAccount() {
            val loggedIn = RezkaClient.get(requireContext()).isLoggedIn
            findPreference<Preference>("login")?.summary = getString(if (loggedIn) R.string.logged_in else R.string.logged_out)
            findPreference<Preference>("logout")?.isVisible = loggedIn
        }
    }
}
