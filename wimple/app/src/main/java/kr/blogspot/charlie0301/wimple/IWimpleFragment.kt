package kr.blogspot.charlie0301.wimple

import android.os.Message

interface IWimpleFragment {

    fun handleMessage(msg: Message)

    fun setActivityInstance(instance: WimpleActivity)
}
