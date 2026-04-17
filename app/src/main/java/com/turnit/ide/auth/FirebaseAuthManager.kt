package com.turnit.ide.auth

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class FirebaseAuthManager(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    @Volatile
    var lastErrorMessage: String? = null
        private set

    suspend fun signInWithEmail(email: String, password: String): AuthResult? {
        return try {
            val result = firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .awaitResult()
            syncUserProfile(email)
            lastErrorMessage = null
            result
        } catch (exception: Exception) {
            lastErrorMessage = exception.message ?: "Unable to sign in"
            Log.e("FirebaseAuthManager", "Email sign-in failed", exception)
            null
        }
    }

    suspend fun signUpWithEmail(email: String, password: String): AuthResult? {
        return try {
            val result = firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .awaitResult()
            syncUserProfile(email)
            lastErrorMessage = null
            result
        } catch (exception: Exception) {
            lastErrorMessage = exception.message ?: "Unable to create account"
            Log.e("FirebaseAuthManager", "Email sign-up failed", exception)
            null
        }
    }

    fun buildGoogleSignInClient(context: Context, webClientId: String?): GoogleSignInClient {
        val optionsBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()

        if (!webClientId.isNullOrBlank()) {
            optionsBuilder.requestIdToken(webClientId)
        }

        return GoogleSignIn.getClient(context, optionsBuilder.build())
    }

    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult? {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).awaitResult()
            val email = result.user?.email
            if (!email.isNullOrBlank()) {
                syncUserProfile(email)
            }
            lastErrorMessage = null
            result
        } catch (exception: Exception) {
            lastErrorMessage = exception.message ?: "Unable to sign in with Google"
            Log.e("FirebaseAuthManager", "Google sign-in failed", exception)
            null
        }
    }

    private suspend fun syncUserProfile(email: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val profile = mapOf(
            "email" to email,
            "updatedAt" to System.currentTimeMillis()
        )
        try {
            firestore.collection("users")
                .document(uid)
                .set(profile)
                .awaitVoid()
        } catch (exception: Exception) {
            Log.w("FirebaseAuthManager", "User profile sync failed", exception)
            lastErrorMessage = "Profile sync failed - some cloud features may be unavailable"
        }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T {
    return suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: IllegalStateException("Task failed"))
            }
        }
    }
}

private suspend fun com.google.android.gms.tasks.Task<Void>.awaitVoid() {
    suspendCancellableCoroutine<Unit> { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(task.exception ?: IllegalStateException("Task failed"))
            }
        }
    }
}
