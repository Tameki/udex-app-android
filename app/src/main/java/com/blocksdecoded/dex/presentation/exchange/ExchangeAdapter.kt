package com.blocksdecoded.dex.presentation.exchange

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.blocksdecoded.dex.presentation.exchange.view.limit.LimitOrderView
import com.blocksdecoded.dex.presentation.exchange.view.market.MarketOrderView

class ExchangeAdapter : PagerAdapter() {
	override fun instantiateItem(container: ViewGroup, position: Int): Any {
		val view = when(position) {
			0 -> MarketOrderView(container.context)
			else -> LimitOrderView(container.context)
		}
		
		container.addView(view)
		return view
	}
	
	override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`
	
	override fun getCount(): Int = 2
	
	override fun getPageTitle(position: Int): CharSequence? = when(position) {
		0 -> "Exchange"
		else -> "Limit order"
	}
	
	override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
		super.destroyItem(container, position, `object`)
	}
}