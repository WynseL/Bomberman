package com.wynsel.bomberman

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentResolverCompat
import androidx.core.content.ContextCompat
import androidx.core.text.isDigitsOnly
import com.google.android.material.chip.Chip
import com.wynsel.bomberman.models.Recipient
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    companion object {
        private const val PICK_CONTACT = 99
        private const val SENT_TAG = 89
        private const val DELIVERED_TAG = 88

        private const val SENT = "SEND"
        private const val DELIVERED = "DELIVERED"
    }

    private val smsManager: SmsManager by lazy { SmsManager.getDefault() }
    private var sendList: ArrayList<Recipient?> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {

        // Resize window when keyboard is open
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnAddTo.setOnClickListener {
            openContacts()
        }

        btnSend.setOnClickListener {
            val body = etBody.text.toString()
            val amount = etAmount.text.toString()
            when {
                sendList.isEmpty() -> toaster("Please add recipient")
                body.isBlank() -> toaster("Please add message")
                amount.isDigitsOnly() && amount.toInt() !in 1..99 -> toaster("Amount number must be between 1 to 99")
                else -> {
                    // todo dialog
                    sendList.forEach {
                        it?.amount = amount.toInt()
                        it?.message = body
                    }

                    sendSms()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            PICK_CONTACT -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        val recipient = contactData(data)
                        addToList(recipient)
                    }
                    Activity.RESULT_CANCELED -> Unit
                }
            }
            SENT_TAG -> sentIntent.resultCodeCall(resultCode)
            DELIVERED_TAG -> sentIntent.resultCodeCall(resultCode)
        }
    }

    private fun contactData(data: Intent?): Recipient? {
        val contactData = data?.data
        if (contactData != null) {
            val contentCursor = ContentResolverCompat.query(
                contentResolver,
                contactData,
                null,
                null,
                null,
                null,
                null
            )
            if (contentCursor.moveToFirst()) {
                val id = contentCursor.getString(contentCursor.getColumnIndex(ContactsContract.Contacts._ID))
                val dataKindsCursor = ContentResolverCompat.query(
                    contentResolver,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(id),
                    null,
                    null
                )

                if (dataKindsCursor.moveToFirst()) {
                    return Recipient(
                        contentCursor.getString(contentCursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)),
                        dataKindsCursor.getString(dataKindsCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    ).apply {
                        contentCursor.close()
                        dataKindsCursor.close()
                    }
                }
            } else contentCursor.close()
        }
        return null
    }

    private fun addToList(r: Recipient?) {
        val exists = sendList.any { it?.mobileNumber == r?.mobileNumber }
        if (exists) {
            toaster("${r?.name} (${r?.mobileNumber}) already added")
            return
        }

        if (sendList.size != 0) {
            toaster("You can only add one recipient, for now")
            return
        }

        sendList.add(r)

        Chip(this, null, R.style.ChipStyle).also { chip ->
            chip.text = resources.getString(R.string.chip_text).format(r?.name, r?.mobileNumber)
            chip.isCloseIconVisible = true
            chip.setOnCloseIconClickListener {
                sendList.remove(r)
                cgTo.removeView(chip)
            }
            cgTo.addView(chip)
        }
    }

    private fun openContacts() {
        // Show permission if READ_CONTACTS is not allowed yet
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED) {
            toaster("READ_CONTACTS not allowed yet")
            return
        }

        Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            .also { startActivityForResult(it, PICK_CONTACT) }
    }

    private fun sendSms() {
        val r = sendList[0]
        toaster("sendList: $r")
        return
        if (r != null) {
            smsManager.sendTextMessage(
                r.mobileNumber,
                null,
                r.message,
                sentIntent.pendingIntent,
                deliveredIntent.pendingIntent
            )
        }
    }

    private val sentIntent = PendingIntentBuilder(this, SENT, SENT_TAG) { result ->
        when(result) {
            Activity.RESULT_OK -> {}
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {}
            SmsManager.RESULT_ERROR_NO_SERVICE -> {}
            SmsManager.RESULT_ERROR_NULL_PDU -> {}
            SmsManager.RESULT_ERROR_RADIO_OFF -> {}
        }
    }

    private val deliveredIntent = PendingIntentBuilder(this, DELIVERED, DELIVERED_TAG) { result ->
        when(result) {
            Activity.RESULT_OK -> {}
            Activity.RESULT_CANCELED -> {}
        }
    }

//    private fun pendingIntentBuilder(tag: String, requestCode: Int, resultCode: (Int) -> Unit): PendingIntent {
//        val pendingIntent = PendingIntent.getBroadcast(this, requestCode, Intent(tag), 0)
//        val receiver = object: BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                intent.res
//            }
//        }
//
//        registerReceiver(receiver, IntentFilter(tag))
//        return pendingIntent
//    }

    private fun toaster(message: String?) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    inner class PendingIntentBuilder(
        private val context: Context,
        private val tag: String,
        private val requestCode: Int,
        private val resultCodeCallback: (Int) -> Unit
    ) {
        private fun createPendingIntent(): PendingIntent {
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, Intent(tag), 0)
            registerReceiver(receiver, IntentFilter(tag))
            return pendingIntent
        }

        val pendingIntent = createPendingIntent()

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                resultCodeCallback.invoke(resultCode)
            }
        }

        fun resultCodeCall(resultCode: Int) = resultCodeCallback.invoke(resultCode)
    }
}
