package com.turnit.ide.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenManager: TokenManager
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_AUTH_RETRIES) {
            return null
        }

        val refreshToken = tokenManager.getRefreshToken() ?: return null

        synchronized(refreshLock) {
            val latestAccessToken = tokenManager.getAccessToken()
            val requestAccessToken = response.request.header(AUTHORIZATION_HEADER)
                ?.removePrefix(BEARER_PREFIX)
                ?.trim()

            if (!latestAccessToken.isNullOrBlank() && latestAccessToken != requestAccessToken) {
                return response.request.newBuilder()
                    .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$latestAccessToken")
                    .build()
            }

            val refreshResult = runBlocking {
                AuthNetworkClient.refreshAccessToken(refreshToken)
            }

            return if (refreshResult != null && refreshResult.accessToken.isNotBlank() && refreshResult.refreshToken.isNotBlank()) {
                tokenManager.saveTokens(
                    accessToken = refreshResult.accessToken,
                    refreshToken = refreshResult.refreshToken
                )

                response.request.newBuilder()
                    .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${refreshResult.accessToken}")
                    .build()
            } else {
                tokenManager.clearTokens()
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val MAX_AUTH_RETRIES = 2
    }
}

data class TokenRefreshResult(
    val accessToken: String,
    val refreshToken: String
)

object AuthNetworkClient {
    suspend fun refreshAccessToken(refreshToken: String): TokenRefreshResult? {
        if (refreshToken.isBlank()) {
            return null
        }

        // Dummy implementation to be replaced with real API integration.
        return null
    }
}
