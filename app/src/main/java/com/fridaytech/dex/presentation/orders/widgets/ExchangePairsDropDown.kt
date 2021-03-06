package com.fridaytech.dex.presentation.orders.widgets

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isInvisible
import com.fridaytech.dex.R
import com.fridaytech.dex.presentation.orders.model.ExchangePairViewItem
import com.fridaytech.dex.presentation.widgets.BaseDropDownView
import com.fridaytech.dex.utils.getAttr
import com.fridaytech.dex.utils.inflate
import com.fridaytech.dex.utils.ui.toFiatDisplayFormat
import com.fridaytech.dex.utils.visible

class ExchangePairsDropDown :
    BaseDropDownView<ExchangePairViewItem> {
    override val popupVerticalOffset: Int = 0

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun refreshSelectedItem(item: ExchangePairViewItem) {
        val baseCoin: TextView? = selectedView?.findViewById(R.id.exchange_pair_base_coin)
        val quoteCoin: TextView? = selectedView?.findViewById(R.id.exchange_pair_quote_coin)
        val basePrice: TextView? = selectedView?.findViewById(R.id.exchange_pair_base_coin_price)
        val quotePrice: TextView? = selectedView?.findViewById(R.id.exchange_pair_quote_coin_price)

        baseCoin?.text = item.baseCoin
        quoteCoin?.text = item.quoteCoin
        basePrice?.text = "$${item.basePrice?.toFiatDisplayFormat()}"
        quotePrice?.text = "$${item.quotePrice?.toFiatDisplayFormat()}"

        selectedView?.findViewById<View>(R.id.divider)?.visible = false

        val color = context.theme.getAttr(R.attr.AccentTextColor) ?: 0
        selectedView?.findViewById<ImageView>(R.id.exchange_pair_switch)?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    fun init(onItemPick: (position: Int) -> Unit) {
        val adapter =
            OrdersPopupAdapter(
                listOf()
            )
        init(adapter, onItemPick)
    }

    private class OrdersPopupAdapter(
        items: List<ExchangePairViewItem>,
        onItemPick: ((position: Int) -> Unit)? = null
    ) : BaseDropDownView.PopupAdapter<ExchangePairViewItem>(items, onItemPick) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DropDownHolder<ExchangePairViewItem> =
            ExchangePairHolder(
                parent.inflate(R.layout.item_exchange_pair),
                onItemPick
            )

        override fun onBindViewHolder(holder: DropDownHolder<ExchangePairViewItem>, position: Int) {
            super.onBindViewHolder(holder, position)
            (holder as ExchangePairHolder).divider.isInvisible = position == itemCount - 1
        }

        private class ExchangePairHolder(
            view: View,
            onItemPick: ((position: Int) -> Unit)? = null
        ) : DropDownHolder<ExchangePairViewItem> (view, onItemPick) {
            val baseCoin: TextView = itemView.findViewById(R.id.exchange_pair_base_coin)
            val quoteCoin: TextView = itemView.findViewById(R.id.exchange_pair_quote_coin)
            val basePrice: TextView = itemView.findViewById(R.id.exchange_pair_base_coin_price)
            val quotePrice: TextView = itemView.findViewById(R.id.exchange_pair_quote_coin_price)
            val divider: View = itemView.findViewById(R.id.divider)

            override fun onBind(data: ExchangePairViewItem) {
                baseCoin.text = data.baseCoin
                quoteCoin.text = data.quoteCoin
                basePrice.text = "$${data.basePrice.toFiatDisplayFormat()}"
                quotePrice.text = "$${data.quotePrice.toFiatDisplayFormat()}"
            }
        }
    }
}
