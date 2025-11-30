package app.venues.ticket.api.security

import app.venues.ticket.api.dto.ScannerSessionDto
import org.springframework.security.authentication.AbstractAuthenticationToken

class ScannerAuthentication(
    private val session: ScannerSessionDto
) : AbstractAuthenticationToken(emptyList()) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any {
        return session.secretToken
    }

    override fun getPrincipal(): Any {
        return session
    }
}
