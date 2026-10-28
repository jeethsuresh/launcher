// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.still

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Telephony
import helium314.keyboard.compat.isDeviceLocked
import helium314.keyboard.latin.utils.prefs

object RecentSmsCodes {
    val store = RecentCodeStore()
    private val listeners = mutableSetOf<() -> Unit>()
    fun subscribe(listener: () -> Unit) { listeners.add(listener) }
    fun unsubscribe(listener: () -> Unit) { listeners.remove(listener) }
    fun changed() { listeners.toList().forEach { it() } }
    fun enabled(context: Context) = context.prefs().getBoolean(OneTimeCodes.SMS_ENABLED_PREF, false) &&
        context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
}

class RecentSmsCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION || !RecentSmsCodes.enabled(context) || isDeviceLocked(context)) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Multipart SMS is one message; never merge messages from different senders.
        val codes = parts.groupBy { it.originatingAddress }.values.flatMap { messages ->
            OneTimeCodes.fromMessage(messages.joinToString("") { it.messageBody.orEmpty() })
        }
        if (codes.isEmpty()) return
        RecentSmsCodes.store.add(codes, SystemClock.elapsedRealtime())
        RecentSmsCodes.changed()
    }
}
