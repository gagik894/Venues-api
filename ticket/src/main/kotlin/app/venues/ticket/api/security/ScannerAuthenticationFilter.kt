package app.venues.ticket.api.security

import app.venues.ticket.api.ScannerSessionApi
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class ScannerAuthenticationFilter(
    private val sessionApi: ScannerSessionApi
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = request.getHeader("X-Scanner-Token")

        if (token != null && token.isNotBlank()) {
            val session = sessionApi.validateSession(token)

            if (session != null) {
                val authentication = ScannerAuthentication(session)
                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
    }
}
