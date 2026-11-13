package com.example.data.remote

import android.util.Base64
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class DarajaTokenResponse(
    @Json(name = "access_token") val accessToken: String = "",
    @Json(name = "expires_in") val expiresIn: String = ""
)

@JsonClass(generateAdapter = true)
data class DarajaStkPushRequest(
    @Json(name = "BusinessShortCode") val businessShortCode: String,
    @Json(name = "Password") val password: String,
    @Json(name = "Timestamp") val timestamp: String,
    @Json(name = "TransactionType") val transactionType: String = "CustomerPayBillOnline",
    @Json(name = "Amount") val amount: Int,
    @Json(name = "PartyA") val partyA: String,
    @Json(name = "PartyB") val partyB: String,
    @Json(name = "PhoneNumber") val phoneNumber: String,
    @Json(name = "CallBackURL") val callBackUrl: String,
    @Json(name = "AccountReference") val accountReference: String,
    @Json(name = "TransactionDesc") val transactionDesc: String
)

@JsonClass(generateAdapter = true)
data class DarajaStkPushResponse(
    @Json(name = "MerchantRequestID") val merchantRequestId: String = "",
    @Json(name = "CheckoutRequestID") val checkoutRequestId: String = "",
    @Json(name = "ResponseCode") val responseCode: String = "",
    @Json(name = "ResponseDescription") val responseDescription: String = "",
    @Json(name = "CustomerMessage") val customerMessage: String = ""
)

@JsonClass(generateAdapter = true)
data class DarajaStkQueryRequest(
    @Json(name = "BusinessShortCode") val businessShortCode: String,
    @Json(name = "Password") val password: String,
    @Json(name = "Timestamp") val timestamp: String,
    @Json(name = "CheckoutRequestID") val checkoutRequestId: String
)

@JsonClass(generateAdapter = true)
data class DarajaStkQueryResponse(
    @Json(name = "ResponseCode") val responseCode: String = "",
    @Json(name = "ResponseDescription") val responseDescription: String = "",
    @Json(name = "ResultCode") val resultCode: String = "",
    @Json(name = "ResultDesc") val resultDesc: String = ""
)

interface DarajaApiService {
    @GET("oauth/v1/generate?grant_type=client_credentials")
    suspend fun getAccessToken(
        @Header("Authorization") basicAuth: String
    ): Response<DarajaTokenResponse>

    @POST("mpesa/stkpush/v1/processrequest")
    suspend fun initiateStkPush(
        @Header("Authorization") bearerAuth: String,
        @Body request: DarajaStkPushRequest
    ): Response<DarajaStkPushResponse>

    @POST("mpesa/stkpushquery/v1/query")
    suspend fun queryStkPushStatus(
        @Header("Authorization") bearerAuth: String,
        @Body request: DarajaStkQueryRequest
    ): Response<DarajaStkQueryResponse>
}

class DarajaPaymentClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private fun createService(environment: String): DarajaApiService {
        val baseUrl = if (environment == "production") {
            "https://api.safaricom.co.ke/"
        } else {
            "https://sandbox.safaricom.co.ke/"
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DarajaApiService::class.java)
    }

    suspend fun triggerStkPush(
        environment: String,
        consumerKey: String,
        consumerSecret: String,
        shortcode: String,
        passkey: String,
        phone: String,
        amount: Double,
        accountReference: String,
        callbackUrl: String
    ): Result<DarajaStkPushResponse> = runCatching {
        require(consumerKey.isNotBlank() && consumerSecret.isNotBlank() && passkey.isNotBlank()) {
            "Daraja Consumer Key, Consumer Secret, and Passkey must be configured in Business Payment Settings or use Paybill / Manual Verification."
        }
        val normalizedPhone = normalizeKenyanPhone(phone)
        val service = createService(environment)
        val credentials = Base64.encodeToString("$consumerKey:$consumerSecret".toByteArray(), Base64.NO_WRAP)
        val tokenResp = service.getAccessToken("Basic $credentials")
        if (!tokenResp.isSuccessful || tokenResp.body()?.accessToken.isNullOrBlank()) {
            throw IllegalStateException("Daraja OAuth failed (${tokenResp.code()}): ${tokenResp.message()}")
        }
        val accessToken = tokenResp.body()!!.accessToken
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        val password = Base64.encodeToString("$shortcode$passkey$timestamp".toByteArray(), Base64.NO_WRAP)

        val request = DarajaStkPushRequest(
            businessShortCode = shortcode,
            password = password,
            timestamp = timestamp,
            amount = amount.toInt().coerceAtLeast(1),
            partyA = normalizedPhone,
            partyB = shortcode,
            phoneNumber = normalizedPhone,
            callBackUrl = callbackUrl.ifBlank { "https://api.example.com/mpesa/callback" },
            accountReference = accountReference.take(12),
            transactionDesc = "Order $accountReference"
        )
        val pushResp = service.initiateStkPush("Bearer $accessToken", request)
        if (!pushResp.isSuccessful || pushResp.body() == null) {
            throw IllegalStateException("M-Pesa STK Push failed (${pushResp.code()}): ${pushResp.message()}")
        }
        pushResp.body()!!
    }

    private fun normalizeKenyanPhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("254") && digits.length == 12 -> digits
            digits.startsWith("0") && digits.length == 10 -> "254${digits.drop(1)}"
            digits.length == 9 -> "254$digits"
            else -> digits
        }
    }
}
