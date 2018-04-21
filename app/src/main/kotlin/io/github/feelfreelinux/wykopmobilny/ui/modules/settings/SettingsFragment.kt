package io.github.feelfreelinux.wykopmobilny.ui.modules.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.ListPreference
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.AndroidSupportInjection
import dagger.android.support.HasSupportFragmentInjector
import io.github.feelfreelinux.wykopmobilny.R
import io.github.feelfreelinux.wykopmobilny.ui.modules.blacklist.BlacklistActivity
import io.github.feelfreelinux.wykopmobilny.ui.modules.notifications.notificationsservice.WykopNotificationsJob
import io.github.feelfreelinux.wykopmobilny.utils.preferences.SettingsPreferencesApi
import io.github.feelfreelinux.wykopmobilny.utils.usermanager.UserManagerApi
import javax.inject.Inject
import android.content.pm.PackageManager
import io.github.feelfreelinux.wykopmobilny.ui.modules.notifications.notificationsservice.NotificationPiggyback
import android.app.ActivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import io.github.feelfreelinux.wykopmobilny.ui.dialogs.createAlertBuilder
import kotlinx.android.synthetic.main.link_related_layout.*


class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener, HasSupportFragmentInjector {
    @Inject
    lateinit var settingsApi: SettingsPreferencesApi
    @Inject lateinit var userManagerApi : UserManagerApi
    @Inject lateinit var childFragmentInjector : DispatchingAndroidInjector<Fragment>

    override fun supportFragmentInjector(): AndroidInjector<Fragment> {
        return childFragmentInjector
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.app_preferences)
        (findPreference("notificationsSchedulerDelay") as ListPreference).apply {
            summary = entry
        }

        (findPreference("showNotifications") as CheckBoxPreference).isEnabled = !(findPreference("piggyBackPushNotifications") as CheckBoxPreference).isChecked
        (findPreference("appearance") as Preference).setOnPreferenceClickListener {
            (activity as SettingsActivity).openFragment(SettingsAppearance(), "appearance")
            true
        }

        (findPreference("blacklist") as Preference).setOnPreferenceClickListener {
            userManagerApi.runIfLoggedIn(activity!!) {
                startActivity(BlacklistActivity.createIntent(activity!!))
            }
            true
        }

    }

    override fun onSharedPreferenceChanged(sharedPrefs: SharedPreferences, key: String) {
        val pref = findPreference(key)
        if (pref is CheckBoxPreference) {
            when (pref.key) {
                "showNotifications" -> {
                    if (pref.isChecked) {
                        WykopNotificationsJob.schedule(settingsApi)
                    } else {
                        WykopNotificationsJob.cancel()
                    }
                }

                "piggyBackPushNotifications" -> {
                    (findPreference("showNotifications") as Preference).isEnabled = !pref.isChecked
                    if (pref.isChecked) {
                        if (isOfficialAppInstalled()) {
                            if (isNotificationAccessEnabled()) {
                                WykopNotificationsJob.cancel()
                            } else {
                                pref.isChecked = false
                                onSharedPreferenceChanged(sharedPrefs, key)
                                Toast.makeText(activity!!, R.string.toast_allow_notification_access, Toast.LENGTH_SHORT).show()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                } else {
                                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                                }
                            }
                        } else {
                            pref.isChecked = false
                            onSharedPreferenceChanged(sharedPrefs, key)
                            openWykopMarketPage()
                        }
                    } else {
                        if ((findPreference("showNotifications") as CheckBoxPreference).isChecked) {
                            WykopNotificationsJob.schedule(settingsApi)
                        } else {
                            WykopNotificationsJob.cancel()
                        }
                    }
                }
            }
        }
    }

    fun isOfficialAppInstalled(): Boolean {
        return try {
            activity!!.packageManager.getApplicationInfo("pl.wykop.droid", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isNotificationAccessEnabled(): Boolean {
        val manager = activity!!.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return manager.getRunningServices(
                Integer.MAX_VALUE).any { NotificationPiggyback::class.java.name == it.service.className }
    }

    fun openWykopMarketPage() {
        activity!!.createAlertBuilder().apply {
            setTitle(R.string.dialog_piggyback_market_title)
            setMessage(R.string.dialog_piggyback_market_message)
            setCancelable(false)
            setPositiveButton(android.R.string.ok, { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("market://details?id=pl.wykop.droid")
                startActivity(intent)
            })
            create()
            show()
        }
    }


    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun restartActivity() {
        val intent = Intent(context, SettingsActivity::class.java)
        intent.putExtra(SettingsActivity.THEME_CHANGED_EXTRA, true)
        startActivity(intent)
        activity?.finish()
    }
}