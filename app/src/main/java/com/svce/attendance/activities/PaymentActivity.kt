package com.svce.attendance.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.svce.attendance.R
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.*

class PaymentActivity : AppCompatActivity() {

    private lateinit var btnPayNow: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val client = OkHttpClient()

    // Temporarily save token so we can use it when browser returns
    private var currentAccessToken: String? = null


    // **REPLACE WITH YOUR SANDBOX CLIENT ID FROM DEVELOPER.PAYPAL.COM**
    // --- PROFESSIONAL ENVIRONMENT CONFIGURATION ---
    // Change this to 'true' when releasing to the Google Play Store
    private val isProduction = false

    private val PAYPAL_CLIENT_ID = if (isProduction) {
        "YOUR_LIVE_CLIENT_ID" // Add this later when you go live
    } else {
        "AVyc73kivhhQyoDkpHKf_51fTliLwo3-YSzoPh_x3hSLGwHUE-a7VVmAwUIBcasvrByhV4ssMW6e7_rq"
    }

    private val PAYPAL_CLIENT_SECRET = if (isProduction) {
        "YOUR_LIVE_SECRET_KEY" // Add this later when you go live
    } else {
        "EP04AOonf0Xyaj31BfDRzCtPHszrfk5sKcvY_5zmkrBSQYusL8CavAijWE6czVeQQjuBeiksvAKWblYh"
    }

    private val PAYPAL_API_URL = if (isProduction) {
        "https://api.paypal.com" // Live Money URL
    } else {
        "https://api.sandbox.paypal.com" // Fake Money URL
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)

        auth = Firebase.auth
        db = Firebase.firestore

        btnPayNow = findViewById(R.id.btnPayNow)
        progressBar = findViewById(R.id.progressBar)

        btnPayNow.setOnClickListener {
            initiatePayPalPayment()
        }
    }

    private fun initiatePayPalPayment() {
        btnPayNow.isEnabled = false
        progressBar.visibility = View.VISIBLE

        getPayPalAccessToken { accessToken ->
            if (accessToken != null) {
                currentAccessToken = accessToken  // ADDED THIS LINE
                createPayPalOrder(accessToken)
            } else {
                btnPayNow.isEnabled = true
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to connect to PayPal", Toast.LENGTH_SHORT).show()
            }
        }
    }



    private fun getPayPalAccessToken(callback: (String?) -> Unit) {
        val credentials = "$PAYPAL_CLIENT_ID:$PAYPAL_CLIENT_SECRET"

        // FIX: Use android.util.Base64.encodeToString with NO_WRAP flag
        val encodedCredentials = android.util.Base64.encodeToString(
            credentials.toByteArray(),
            android.util.Base64.NO_WRAP
        )

        val requestBody = "grant_type=client_credentials"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())

        val request = Request.Builder()
            .url("$PAYPAL_API_URL/v1/oauth2/token")
            .addHeader("Authorization", "Basic $encodedCredentials")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PaymentActivity", "Failed to get access token", e)
                runOnUiThread { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    val token = json.getString("access_token")
                    Log.d("PaymentActivity", "Got access token")
                    runOnUiThread { callback(token) }
                } else {
                    Log.e("PaymentActivity", "Token request failed: $body")
                    runOnUiThread { callback(null) }
                }
            }
        })
    }

    private fun createPayPalOrder(accessToken: String) {
        val orderJson = JSONObject().apply {
            put("intent", "CAPTURE")
            put("purchase_units", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("amount", JSONObject().apply {
                        put("currency_code", "USD")
                        put("value", "5.00") // $1 for testing (change to 99 INR for production)
                    })
                    put("description", "SVCE Attendance App - Lifetime Access")
                })
            })
            // ADDED THIS BLOCK: Application Context with return URLs
            // CHANGE THIS BLOCK
            put("application_context", JSONObject().apply {
                put("return_url", "svceattendance://paypal/success") // CHANGED
                put("cancel_url", "svceattendance://paypal/cancel")  // CHANGED
                put("user_action", "PAY_NOW")
            })

        }

        val requestBody = orderJson.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$PAYPAL_API_URL/v2/checkout/orders")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PaymentActivity", "Failed to create order", e)
                runOnUiThread {
                    btnPayNow.isEnabled = true
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@PaymentActivity, "Payment failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    val orderId = json.getString("id")
                    val approvalUrl = json.getJSONArray("links")
                        .let { links ->
                            (0 until links.length()).map { links.getJSONObject(it) }
                                .first { it.getString("rel") == "approve" }
                                .getString("href")
                        }

                    Log.d("PaymentActivity", "Created order: $orderId")
                    runOnUiThread {
                        // Open PayPal approval URL in browser
                        openPayPalApproval(approvalUrl, orderId, accessToken)
                    }
                } else {
                    Log.e("PaymentActivity", "Order creation failed: $body")
                    runOnUiThread {
                        btnPayNow.isEnabled = true
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@PaymentActivity, "Payment setup failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun openPayPalApproval(approvalUrl: String, orderId: String, accessToken: String) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(approvalUrl))
        startActivity(intent)
        // TIMER EXPLOIT REMOVED. The app now waits for the browser to deep link back.
    }

    // THIS FUNCTION CATCHES THE APP WHEN IT WAKES UP FROM THE BROWSER
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { uri ->
            if (uri.scheme == "svceattendance" && uri.host == "paypal") {
                if (uri.path == "/success") {
                    // Browser says success. Now we securely capture the funds!
                    val orderId = uri.getQueryParameter("token") // PayPal returns Order ID as 'token'
                    if (orderId != null && currentAccessToken != null) {
                        capturePayPalOrder(orderId, currentAccessToken!!)
                    }
                } else if (uri.path == "/cancel") {
                    progressBar.visibility = View.GONE
                    btnPayNow.isEnabled = true
                    Toast.makeText(this, "Payment was cancelled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    private fun markUserAsPaid() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .update("hasPaid", true)
                .addOnSuccessListener {
                    Log.d("PaymentActivity", "User marked as paid")
                    Toast.makeText(this, "Payment successful!", Toast.LENGTH_SHORT).show()

                    // Navigate to HomeActivity
                    val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
                    val role = sharedPref.getString("user_role", "teacher") ?: "teacher"
                    val email = sharedPref.getString("user_email", "") ?: ""
                    val rollNumber = sharedPref.getString("user_roll", "") ?: ""

                    val intent = Intent(this, HomeActivity::class.java).apply {
                        putExtra("role", role)
                        putExtra("email", email)
                        if (role == "student") {
                            putExtra("rollNumber", rollNumber)
                        }
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finishAffinity()
                }
                .addOnFailureListener { e ->
                    Log.e("PaymentActivity", "Failed to update payment status", e)
                    Toast.makeText(this, "Error updating status", Toast.LENGTH_SHORT).show()
                }
        }
    }
    // NEW PRODUCTION FUNCTION: Actually take the money!
    private fun capturePayPalOrder(orderId: String, accessToken: String) {
        progressBar.visibility = View.VISIBLE
        btnPayNow.isEnabled = false

        // Empty body required for capture endpoint
        val requestBody = "".toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$PAYPAL_API_URL/v2/checkout/orders/$orderId/capture")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            // ADD here the EXACT LINE FOR NEGATIVE TESTING:

            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnPayNow.isEnabled = true
                    Toast.makeText(this@PaymentActivity, "Network error during capture", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()

                // ADD THIS LINE: Print the exact bank receipt to Android Studio!
                Log.d("PayPalReceipt", "Capture API Response: $body")


                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && body != null) {
                        val json = JSONObject(body)
                        val status = json.optString("status")

                        if (status == "COMPLETED") {
                            // Only unlock features if the bank officially processed the funds
                            markUserAsPaid()
                        } else {
                            btnPayNow.isEnabled = true
                            Toast.makeText(this@PaymentActivity, "Payment not completed: $status", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        btnPayNow.isEnabled = true
                        Toast.makeText(this@PaymentActivity, "Capture failed. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }


}
