package app.venues.organization.api.controller

import app.venues.common.model.ApiResponse
import app.venues.organization.api.dto.CreateOrganizationRequest
import app.venues.organization.api.dto.OrganizationDetailResponse
import app.venues.organization.api.dto.OrganizationResponse
import app.venues.organization.api.dto.UpdateOrganizationRequest
import app.venues.organization.service.OrganizationService
import app.venues.shared.persistence.util.PageableMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Administrative endpoints for organization management.
 *
 * Restricted to SUPER_ADMIN to preserve clear responsibility boundaries
 * (org CRUD and listing remain in the Organization module).
 */
@RestController
@RequestMapping("/api/v1/admin/organizations")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Organization Admin", description = "Super admin organization operations")
class OrganizationAdminController(
    private val organizationService: OrganizationService
) {

    @GetMapping
    @Operation(summary = "List organizations (active/inactive)", description = "SUPER_ADMIN only")
    fun listOrganizations(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false, defaultValue = "false") includeInactive: Boolean
    ): ApiResponse<Page<OrganizationResponse>> {
        val pageable = PageableMapper.createPageableUnsorted(limit, offset)
        val orgs = organizationService.listOrganizations(pageable, includeInactive)
        return ApiResponse.success(orgs, "Organizations listed")
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get organization detail", description = "SUPER_ADMIN only")
    fun getOrganization(
        @PathVariable id: UUID
    ): ApiResponse<OrganizationDetailResponse> {
        val org = organizationService.getOrganizationDetail(id)
        return ApiResponse.success(org, "Organization retrieved")
    }

    @PostMapping
    @Operation(summary = "Create organization", description = "SUPER_ADMIN only")
    fun createOrganization(
        @Valid @RequestBody request: CreateOrganizationRequest
    ): ApiResponse<OrganizationDetailResponse> {
        val org = organizationService.createOrganization(request)
        return ApiResponse.success(org, "Organization created")
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update organization", description = "SUPER_ADMIN only")
    fun updateOrganization(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateOrganizationRequest
    ): ApiResponse<OrganizationDetailResponse> {
        val org = organizationService.updateOrganization(id, request)
        return ApiResponse.success(org, "Organization updated")
    }
}

