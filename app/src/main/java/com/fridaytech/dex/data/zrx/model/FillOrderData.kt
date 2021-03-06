package com.fridaytech.dex.data.zrx.model

import com.fridaytech.dex.presentation.orders.model.EOrderSide
import java.math.BigDecimal

data class FillOrderData(
    val coinPair: Pair<String, String>,
    val side: EOrderSide,
    val amount: BigDecimal
)
