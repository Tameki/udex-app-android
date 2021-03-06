package com.fridaytech.dex.presentation.send.confirm

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import com.fridaytech.dex.R
import com.fridaytech.dex.core.model.Coin
import com.fridaytech.dex.presentation.dialogs.BaseDialog
import com.fridaytech.dex.utils.ui.toFiatDisplayFormat
import com.fridaytech.dex.utils.ui.toMediumDisplayFormat
import java.math.BigDecimal
import kotlinx.android.synthetic.main.dialog_send_confirm.*

class SendConfirmDialog private constructor() :
    BaseDialog(R.layout.dialog_send_confirm) {

    private var sendConfirmData: SendConfirmData? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        send_confirm_btn?.setOnClickListener {
            sendConfirmData?.onConfirm?.invoke()
            dismiss()
        }

        sendConfirmData?.let {
            send_confirm_amount?.text = "${it.amount.toMediumDisplayFormat()} ${it.coin.code}"
            send_confirm_fiat_amount?.text = "$${it.fiatAmount.toFiatDisplayFormat()}"
            send_confirm_address?.update(it.address)
            send_confirm_total_amount?.setFiat(it.total, isExactAmount = true)
            send_confirm_fee?.setCoin("ETH", it.fee, isExactAmount = false) // TODO: Get fee coin code from adapter
            send_confirm_duration?.setMillis(it.estimatedFinishTime)
        }
    }

    companion object {
        fun show(fragmentManager: FragmentManager, data: SendConfirmData) {
            val dialog = SendConfirmDialog()

            dialog.sendConfirmData = data

            dialog.show(fragmentManager, "send_confirm")
        }
    }

    data class SendConfirmData(
        val coin: Coin,
        val address: String,
        val amount: BigDecimal,
        val fiatAmount: BigDecimal,
        val fee: BigDecimal,
        val total: BigDecimal,
        val estimatedFinishTime: Long,
        val onConfirm: () -> Unit
    )
}
