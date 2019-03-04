package kr.blogspot.charlie0301.wimple.impl

import android.content.Context
import android.content.Intent
import android.util.Log

import kr.blogspot.charlie0301.wimple.PaymentNoticeActivity
import kr.blogspot.charlie0301.wimple.SplashScreenActivity

internal class RestResponseHandler {
    var context: Context? = null

    fun setApplicationContext(context: Context) {
        this.context = context
    }

    /*
     * 코드	내용
     * 200	모든 데이터가 정상적으로 반환됨(레코드가 없을 수 있음)
     * 204	레코드가 없음.
     * 400	요청한 파라미터 중 누락되었거나 잘못되었을 수 있음. 개선하여야할 파라미터는 error_parameters에 배열로 반환됨.
     * 401	권한을 벗어난 요청
     * 402	하루 요청횟수를 초과하였을 때. 업그레이드 안내 페이지로 이동시켜야함.
     * 405	인증토큰이 만료되거나 잘못 되었을 때.	 =>  언제든지 사용자가 후잉에서 인증토큰의 권한을 취소할 수 있으므로 항상 이 값을 확인하여 재인증을 요구하는 구조여야 함.
     * 500	서버측에서 발생한 에러. 잠시 후 다시 시도하면 됨.
     */
    fun handleRestResponse(code: Int) {

        if (null == this.context) {
            Log.e(kr.blogspot.charlie0301.wimple.impl.RestResponseHandler.LOG_TAG, "RestResponseHandler is not initialized!!!")
            return
        }

        when (code) {
            402 -> {
                Log.e(kr.blogspot.charlie0301.wimple.impl.RestResponseHandler.LOG_TAG, "All free report are used!!!(402), Open the payment dialog!!!")

                // TODO : remove backcall
                if (!PaymentNoticeActivity.noMore) {
                    val intent = Intent(this.context, PaymentNoticeActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    this.context!!.startActivity(intent)
                } else {
                    Log.e(kr.blogspot.charlie0301.wimple.impl.RestResponseHandler.LOG_TAG, "User wanted to close PaymentNoticeActivity anymore today")
                    // TODO : show toast!!!
                    //Toast.makeText(context, context.getResources().getString(R.string.notice_need_upgrade), Toast.LENGTH_LONG).show();
                }
            }
            405 -> {
                Log.e(kr.blogspot.charlie0301.wimple.impl.RestResponseHandler.LOG_TAG, "Server requests reauthentication(405), restarting this app!!!")
                WimpleImpl.getInstance().cleanAuth()

                val intent = Intent(this.context, SplashScreenActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.putExtra("auth_again", "")
                this.context!!.startActivity(intent)
            }
            else -> Log.e(kr.blogspot.charlie0301.wimple.impl.RestResponseHandler.LOG_TAG, "Server Response Error : $code")
        }
    }

    companion object {

        const val LOG_TAG = "RestResponseHandler"
    }
}
