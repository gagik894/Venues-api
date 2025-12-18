package app.venues.app.architecture

import app.venues.audit.annotation.Auditable
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.conditions.ArchConditions.beAnnotatedWith
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import org.springframework.web.bind.annotation.*

/**
 * Enforces government-quality audit coverage: every mutating endpoint must be annotated with @Auditable.
 */
@AnalyzeClasses(packages = ["app.venues"], importOptions = [])
class AuditAnnotationTest {

    @ArchTest
    val mutatingEndpointsMustBeAuditable: ArchRule = methods()
        .that().areDeclaredInClassesThat().areAnnotatedWith(RestController::class.java)
        .and(methodHasMutationMapping())
        .should(beAnnotatedWith(Auditable::class.java))
        .because("All POST/PUT/PATCH/DELETE endpoints must have @Auditable to ensure audit logging")

    private fun methodHasMutationMapping(): DescribedPredicate<JavaMethod> =
        object : DescribedPredicate<JavaMethod>("has POST/PUT/PATCH/DELETE mapping") {
            override fun test(method: JavaMethod): Boolean {
                return method.isAnnotatedWith(PostMapping::class.java)
                        || method.isAnnotatedWith(PutMapping::class.java)
                        || method.isAnnotatedWith(PatchMapping::class.java)
                        || method.isAnnotatedWith(DeleteMapping::class.java)
            }
        }
}
