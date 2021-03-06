package com.fridaytech.dex.presentation.transactions.model

import com.fridaytech.dex.core.model.Coin
import java.math.BigDecimal
import java.util.*

data class TransactionViewItem(
    val coin: Coin,
    val transactionHash: String,
    val coinValue: BigDecimal,
    var fiatValue: BigDecimal?,
    var fee: BigDecimal?,
    var fiatFee: BigDecimal?,
    var historicalRate: BigDecimal?,
    val from: String?,
    val to: String?,
    val incoming: Boolean,
    val date: Date?,
    val status: TransactionStatus,
    val innerIndex: Int
)

sealed class TransactionStatus {
    object Pending : TransactionStatus()
    class Processing(val progress: Double) : TransactionStatus() // progress in 0..100%
    object Completed : TransactionStatus()
}
