package com.fridaytech.dex.data.zrx.adapter

import com.fridaytech.dex.core.model.CoinType
import com.fridaytech.dex.data.manager.ICoinManager
import com.fridaytech.dex.data.zrx.IExchangeInteractor
import com.fridaytech.dex.data.zrx.IRelayerAdapter
import com.fridaytech.dex.data.zrx.OrdersUtil
import com.fridaytech.dex.data.zrx.model.*
import com.fridaytech.dex.presentation.orders.model.EOrderSide
import com.fridaytech.dex.utils.Logger
import com.fridaytech.dex.utils.normalizedDiv
import com.fridaytech.dex.utils.normalizedMul
import com.fridaytech.dex.utils.rx.ioSubscribe
import com.fridaytech.zrxkit.ZrxKit
import com.fridaytech.zrxkit.model.OrderInfo
import com.fridaytech.zrxkit.model.SignedOrder
import com.fridaytech.zrxkit.relayer.model.OrderRecord
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class BaseRelayerAdapter(
    private val coinManager: ICoinManager,
    private val ethereumKit: EthereumKit,
    private val exchangeInteractor: IExchangeInteractor,
    zrxKit: ZrxKit,
    override val refreshInterval: Long,
    override val relayerId: Int
) : IRelayerAdapter {
    private val disposables = CompositeDisposable()

    private val relayerManager = zrxKit.relayerManager
    private val relayer = relayerManager.availableRelayers[relayerId]

    override var myOrders: List<OrderRecord> = listOf()
    override var myOrdersInfo: List<OrderInfo> = listOf()
    override val myOrdersSyncSubject: BehaviorSubject<Unit> = BehaviorSubject.create()

    override var buyOrders = RelayerOrdersList<OrderRecord>()
    override var sellOrders = RelayerOrdersList<OrderRecord>()

    override val allPairs = relayer.availablePairs
    override var exchangePairs: List<ExchangePair> = listOf()
    override val pairsSyncSubject: BehaviorSubject<Unit> = BehaviorSubject.create()

    init {
        initPairs()

        coinManager.coinsUpdatedSubject.subscribe {
            initPairs()
        }.let { disposables.add(it) }

        Observable.interval(refreshInterval, TimeUnit.SECONDS)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe { refreshOrders() }
            .let { disposables.add(it) }
    }

    //region Private

    private fun initPairs() {
        exchangePairs = allPairs.filter {
            val baseCoin = coinManager.getErcCoinForAddress(it.first.address)
            val quoteCoin = coinManager.getErcCoinForAddress(it.second.address)

            baseCoin != null && quoteCoin != null
        }.map {
            ExchangePair(
                (coinManager.getErcCoinForAddress(it.first.address)?.code ?: ""),
                (coinManager.getErcCoinForAddress(it.second.address)?.code ?: ""),
                it.first,
                it.second
            )
        }

        pairsSyncSubject.onNext(Unit)
        refreshOrders()
    }

    private fun getErcCoin(coinCode: String): CoinType.Erc20 =
        coinManager.getCoin(coinCode).type as CoinType.Erc20

    private fun refreshOrders() {
        relayerManager.getOrders(relayerId, ethereumKit.receiveAddress)
            .ioSubscribe(disposables, {
                this.myOrders = it.records.map { it }.sortedByDescending { it.order.salt }
                myOrdersSyncSubject.onNext(Unit)

                exchangeInteractor.ordersInfo(myOrders.map { it.order })
                    .ioSubscribe(disposables, { ordersInfo ->
                        myOrdersInfo = ordersInfo
                        myOrdersSyncSubject.onNext(Unit)
                    }, { })
            }, {})

        relayer.availablePairs.forEachIndexed { index, pair ->
            val base = pair.first.assetData
            val quote = pair.second.assetData

            refreshPair(base, quote)
        }
    }

    private fun isFillableOrder(orderRecord: OrderRecord): Boolean {
        val remaining = orderRecord.metaData?.remainingFillableTakerAssetAmount?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val takerAmount = orderRecord.order.takerAssetAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO

        return if (remaining.stripTrailingZeros() > BigDecimal.ZERO && takerAmount > BigDecimal.ZERO) {
            val percent = remaining.normalizedDiv(takerAmount).toDouble()

            percent > 0.01
        } else {
            true
        }
    }

    private fun refreshPair(baseAsset: String, quoteAsset: String) {
        relayerManager.getOrderbook(relayerId, baseAsset, quoteAsset)
            .ioSubscribe(disposables, {
                val myAddress = ethereumKit.receiveAddress.toLowerCase()

                val bids = it.bids.records
                    .filterNot { it.order.makerAddress.equals(myAddress, false) }
                    .filter { isFillableOrder(it) }
                buyOrders.updatePairOrders(baseAsset, quoteAsset, bids)

                val asks = it.asks.records
                    .filterNot { it.order.makerAddress.equals(myAddress, false) }
                    .filter { isFillableOrder(it) }
                sellOrders.updatePairOrders(baseAsset, quoteAsset, asks)
            })
    }

    private fun getPairOrders(coinPair: Pair<String, String>, side: EOrderSide): RelayerOrders<OrderRecord> {
        val baseCoin = getErcCoin(coinPair.first)
        val quoteCoin = getErcCoin(coinPair.second)

        return when (side) {
            EOrderSide.BUY -> buyOrders
            else -> sellOrders
        }.getPair(
            ZrxKit.assetItemForAddress(baseCoin.address).assetData,
            ZrxKit.assetItemForAddress(quoteCoin.address).assetData
        )
    }

    private fun calculateFillResult(
        orders: List<OrderRecord>,
        side: EOrderSide,
        amount: BigDecimal
    ): FillResult = try {
        val ordersToFill = arrayListOf<SignedOrder>()

        var requestedAmount = amount
        var fillAmount = BigDecimal.ZERO

        val sortedOrders = orders.map {
            OrdersUtil.normalizeOrderDataPrice(it)
        }.apply { if (side == EOrderSide.BUY) sortedByDescending { it.price } else sortedBy { it.price } }

        for (normalizedOrder in sortedOrders) {
            if (requestedAmount != BigDecimal.ZERO) {
                if (requestedAmount > (normalizedOrder.remainingTakerAmount ?: normalizedOrder.takerAmount)) {
                    fillAmount += normalizedOrder.remainingMakerAmount ?: normalizedOrder.makerAmount
                    requestedAmount -= normalizedOrder.remainingTakerAmount ?: normalizedOrder.takerAmount
                } else {
                    fillAmount += requestedAmount.normalizedMul(normalizedOrder.price)
                    requestedAmount = BigDecimal.ZERO
                }
                normalizedOrder.order?.let { ordersToFill.add(it) }
            } else {
                break
            }
        }

        FillResult(
            ordersToFill,
            fillAmount,
            amount - requestedAmount
        )
    } catch (e: Exception) {
        Logger.e(e)
        FillResult.empty()
    }

    //endregion

    //region Public

    //region Exchange

    override fun createOrder(createData: CreateOrderData): Flowable<SignedOrder> =
        exchangeInteractor.createOrder(relayer.feeRecipients.first(), createData)

    override fun fill(fillData: FillOrderData): Flowable<String> {
        val fillResult = calculateSendAmount(
            fillData.coinPair,
            fillData.side,
            fillData.amount
        )

        return exchangeInteractor.fill(fillResult.orders, fillData)
    }

    override fun cancelOrder(order: SignedOrder): Flowable<String> =
        exchangeInteractor.cancelOrder(order)

    override fun batchCancelOrders(orders: List<SignedOrder>): Flowable<String> =
        exchangeInteractor.batchCancelOrders(orders)

    override fun calculateBasePrice(coinPair: Pair<String, String>, side: EOrderSide): BigDecimal = try {
        OrdersUtil.calculateBasePrice(
            getPairOrders(coinPair, side).orders.map { it.order },
            coinPair,
            side
        )
    } catch (e: Exception) {
        Logger.e(e)
        BigDecimal.ZERO
    }

    //endregion

    override fun calculateFillAmount(
        coinPair: Pair<String, String>,
        side: EOrderSide,
        amount: BigDecimal
    ): FillResult = try {
        val orders = getPairOrders(coinPair, side).orders
        calculateFillResult(orders, side, amount)
    } catch (e: Exception) {
        Logger.e(e)
        FillResult.empty()
    }

    override fun calculateSendAmount(
        coinPair: Pair<String, String>,
        side: EOrderSide,
        amount: BigDecimal
    ): FillResult = try {
        val orders = getPairOrders(coinPair, side).orders
        val ordersToFill = arrayListOf<SignedOrder>()

        var requestedAmount = amount
        var fillAmount = BigDecimal.ZERO

        val sortedOrders = orders.map {
            OrdersUtil.normalizeOrderDataPrice(it, isSellPrice = false)
        }.apply { if (side == EOrderSide.BUY) sortedByDescending { it.price } else sortedBy { it.price } }

        for (normalizedOrder in sortedOrders) {
            if (requestedAmount != BigDecimal.ZERO) {
                if (requestedAmount > (normalizedOrder.remainingMakerAmount ?: normalizedOrder.makerAmount)) {
                    fillAmount += normalizedOrder.remainingTakerAmount ?: normalizedOrder.takerAmount
                    requestedAmount -= normalizedOrder.remainingMakerAmount ?: normalizedOrder.makerAmount
                } else {
                    fillAmount += requestedAmount.normalizedMul(normalizedOrder.price)
                    requestedAmount = BigDecimal.ZERO
                }

                normalizedOrder.order?.let { ordersToFill.add(it) }
            } else {
                break
            }
        }

        FillResult(
            ordersToFill,
            amount - requestedAmount,
            fillAmount
        )
    } catch (e: Exception) {
        FillResult.empty()
    }

    override fun stop() {
        disposables.clear()
        buyOrders.clear()
        sellOrders.clear()
        myOrders = listOf()
        myOrdersInfo = listOf()
        exchangePairs = listOf()
    }

    //endregion
}
