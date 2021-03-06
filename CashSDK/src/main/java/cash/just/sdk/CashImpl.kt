package cash.just.sdk

import android.os.Looper
import cash.just.sdk.Cash.BtcNetwork
import cash.just.sdk.Cash.BtcNetwork.MAIN_NET
import cash.just.sdk.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MultipartBody
import retrofit2.*
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.util.concurrent.CountDownLatch

class CashImpl:Cash {
    private lateinit var sessionKey:String
    private lateinit var retrofit: WacAPI
    private lateinit var network:BtcNetwork
    private var _userState: UserState = UserState.NOT_VALID

    override fun createGuestSession(network: BtcNetwork, listener: Cash.SessionCallback) {
        initIfNeeded(network)

        retrofit.guestLogin().enqueue(object: Callback<GuestResponse> {
            override fun onFailure(call: Call<GuestResponse>, t: Throwable) {
                listener.onError(t.message)
            }

            override fun onResponse(call: Call<GuestResponse>, response: Response<GuestResponse>) {
                if (response.isSuccessful) {
                    sessionKey = response.body()!!.data.sessionKey
                    listener.onSessionCreated(sessionKey)
                    _userState = UserState.GUEST
                } else {
                    var message = response.code().toString()
                    if (response.code() == 502) {
                        message = "$message: Service Temporary Not Available"
                    }
                    listener.onError(message)
                }
            }
        })
    }

    private fun initRetrofit(btcNetwork: BtcNetwork){
        network = btcNetwork
        val serverUrl = when(btcNetwork) {
            MAIN_NET -> {
                "https://api-prd.just.cash/"
            }
            BtcNetwork.TEST_NET -> {
                "https://secure.just.cash/"
            }
            BtcNetwork.TEST_LOCAL-> {
                "http://127.0.0.1:8080/"
            }
        }

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        retrofit = Retrofit.Builder().baseUrl(serverUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build().create(WacAPI::class.java)
    }

    private fun initIfNeeded(btcNetwork: BtcNetwork) {
        if (!::retrofit.isInitialized) {
            initRetrofit(btcNetwork)
        } else if(!::network.isInitialized || network != btcNetwork) {
            initRetrofit(btcNetwork)
        }
    }

    override fun isSessionCreated() : Boolean {
        return this::sessionKey.isInitialized
    }

    override fun login(network: BtcNetwork, phoneNumber: String, listener: Cash.WacCallback) {
        retrofit.login(sessionKey, phoneNumber).enqueue(object: Callback<WacBaseResponse> {
            override fun onResponse(call: Call<WacBaseResponse>, response: Response<WacBaseResponse>) {
                if (response.isSuccessful) {
                    if (response.body()?.result?.toLowerCase() == "ok") {
                        listener.onSucceed()
                    } else {
                        listener.onError("error")
                    }
                }
            }

            override fun onFailure(call: Call<WacBaseResponse>, t: Throwable) {
                listener.onError(t.message)
            }
        })
    }

    override fun loginConfirm(confirmNumber: String, listener: Cash.WacCallback) {
        retrofit.loginConfirmation(sessionKey, confirmNumber).enqueue(object: Callback<WacBaseResponse> {
            override fun onResponse(call: Call<WacBaseResponse>, response: Response<WacBaseResponse>) {
                if (response.isSuccessful) {
                    if (response.body()?.result?.toLowerCase() == "ok") {
                        _userState = UserState.LOGGED_IN
                        listener.onSucceed()
                    } else {
                        listener.onError("error")
                    }
                } else {
                    response.errorBody()?.let {
                        val error = it.parseError()
                        listener.onError("Request with error: ${error.error.code}")
                    } ?:run {
                        Timber.e("http code is not 200 and it has no errorBody")
                    }
                }
            }

            override fun onFailure(call: Call<WacBaseResponse>, t: Throwable) {
                listener.onError(t.message)
            }
        })
    }

    override fun register(network: BtcNetwork, phoneNumber: String, firstName: String, lastName: String, listener: Cash.WacCallback) {
        retrofit.register(sessionKey, phoneNumber, firstName, lastName).enqueue(object: Callback<WacBaseResponse> {
            override fun onResponse(call: Call<WacBaseResponse>, response: Response<WacBaseResponse>) {
                if (response.isSuccessful) {
                    if (response.body()?.result?.toLowerCase() == "ok") {
                        listener.onSucceed()
                    } else {
                        listener.onError("error")
                    }
                }
            }

            override fun onFailure(call: Call<WacBaseResponse>, t: Throwable) {
                listener.onError(t.message)
            }
        })
    }

    override fun getAtmList(): Call<AtmListResponse> {
        return retrofit.getAtmList(sessionKey)
    }

    override fun getAtmListByLocation(latitude: String, longitude: String): Call<AtmListResponse> {
        return retrofit.getAtmListByLocation(sessionKey, latitude, longitude)
    }

    override fun checkCashCodeStatus(code: String): Call<CashCodeStatusResponse> {
        return retrofit.checkCodeStatus(sessionKey, code)
    }

    override fun createCashCode(atmId: String, amount: String, verificationCode: String): Call<CashCodeResponse> {
        return retrofit.createCode(sessionKey, atmId, amount, verificationCode)
    }

    override fun sendVerificationCode(
        firstName: String,
        lastName: String,
        phoneNumber: String?,
        email: String?
    ): Call<SendVerificationCodeResponse> {
        return retrofit.sendVerificationCode(sessionKey, firstName, lastName, phoneNumber, email, "1")
    }

    override fun getSession() : String? {
        return sessionKey
    }

    override fun getKycStatus(): Call<KycStatusResponse> {
        return retrofit.getKycStatus(sessionKey)
    }

    override fun getUserState(refresh: Boolean): UserState {
        if (refresh) {
            if (Looper.getMainLooper().thread == Thread.currentThread()) {
                throw IllegalStateException("You must refresh the userState in a background thread") //Current Thread is Main Thread.
            }
        }
        return _userState
    }

    override fun getKycDocTypes (): Call<KycDocTypeResponse> {
        return retrofit.getKycDocTypes(sessionKey)
    }

    override fun getKycPersonalInformation(): Call<KycPiResponse> {
        return retrofit.getKycPersonalInformation(sessionKey)
    }

    override fun getKycDocuments(): Call<KycDocumentResponse> {
        return retrofit.getKycDocuments(sessionKey)
    }

    override fun uploadKycDocs(
        docType: KycDocType,
        filePart: MultipartBody.Part
    ): Call<WacBaseResponse> {
        return retrofit.uploadKycDoc(sessionKey, docType, filePart)
    }
}