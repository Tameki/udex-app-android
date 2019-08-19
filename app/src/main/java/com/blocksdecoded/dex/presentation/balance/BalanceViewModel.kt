package com.blocksdecoded.dex.presentation.balance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.blocksdecoded.dex.App
import com.blocksdecoded.dex.core.adapter.IAdapter
import com.blocksdecoded.dex.core.manager.CoinManager
import com.blocksdecoded.dex.core.manager.IAdapterManager
import com.blocksdecoded.dex.core.model.CoinValue
import com.blocksdecoded.dex.core.model.EConvertType.*
import com.blocksdecoded.dex.utils.isValidIndex
import com.blocksdecoded.dex.core.ui.CoreViewModel
import com.blocksdecoded.dex.core.ui.SingleLiveEvent
import com.blocksdecoded.dex.presentation.dialogs.convert.ConvertConfig
import com.blocksdecoded.dex.presentation.widgets.balance.TotalBalanceInfo
import java.math.BigDecimal

class BalanceViewModel : CoreViewModel() {
    private val adaptersManager: IAdapterManager = App.adapterManager
    private val adapters: List<IAdapter>
        get() = adaptersManager.adapters

    private val mBalances = MutableLiveData<List<CoinValue>>()
    val balances: LiveData<List<CoinValue>> = mBalances

    val totalBalance = MutableLiveData<TotalBalanceInfo>()

    private val mRefreshing = MutableLiveData<Boolean>()
    val refreshing: LiveData<Boolean> = mRefreshing

    val openSendDialog = SingleLiveEvent<String>()
    val openReceiveDialog = SingleLiveEvent<String>()
    val openTransactions = SingleLiveEvent<String>()
    val openConvertDialog = SingleLiveEvent<ConvertConfig>()

    init {
        mRefreshing.value = true

        adaptersManager.adaptersUpdatedSignal
                .subscribe { onRefreshAdapters() }
                .let { disposables.add(it) }
    }

    //region Private

    private fun onRefreshAdapters() {
        adapters.forEach { adapter ->
            adapter.stateUpdatedFlowable.subscribe {
                mRefreshing.postValue(false)
            }

            adapter.balanceUpdatedFlowable.subscribe {
                updateBalance()
            }
        }

        updateBalance()
    }

    private fun updateBalance() {
        mBalances.postValue(
            adapters.mapIndexed { index, baseAdapter ->
                CoinValue(
                    CoinManager.coins[index],
                    baseAdapter.balance,
                    when(index) {
                        0 -> WRAP
                        1 -> UNWRAP
                        else -> NONE
                    }
                )
            }
        )
        updateTotalBalance()
    }

    private fun updateTotalBalance() {
        val enabledRange = 0..1
        var balance = BigDecimal.ZERO
        for(i in enabledRange) {
            balance += adapters[i].balance
        }

        totalBalance.postValue(
            TotalBalanceInfo(
                CoinManager.getCoin("ETH"),
                balance,
                0.0
            )
        )
    }

    //endregion

    //region Public

    fun refresh() {
        adaptersManager.refresh()
    }

    fun onSendClick(position: Int) {
        if (adapters.isValidIndex(position)) {
            openSendDialog.postValue(adapters[position].coin.code)
        }
    }

    fun onReceiveClick(position: Int) {
        if (adapters.isValidIndex(position)) {
            openReceiveDialog.postValue(adapters[position].coin.code)
        }
    }

    fun onConvertClick(position: Int) {
        if (mBalances.value.isValidIndex(position)) {
            val balance = mBalances.value?.get(position)
            balance?.let {
                //TODO: Refactor. P.s. pass only coin code
                openConvertDialog.postValue(ConvertConfig(
                    it.coin.code,
                    if (position == 0) ConvertConfig.ConvertType.WRAP else ConvertConfig.ConvertType.UNWRAP)
                )
            }
        }
    }

    fun onTransactionsClick(position: Int) {
        if (adapters.isValidIndex(position)) {
            openTransactions.postValue(adapters[position].coin.code)
        }
    }

    //endregion
}
