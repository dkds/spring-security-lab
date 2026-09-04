package com.dkds.authserver;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/// The four package-dependency rules from DESIGN.md, enforced mechanically
/// rather than left to review. See DESIGN.md "Dependency rules, enforced by
/// ArchUnit".
@AnalyzeClasses(packages = "com.dkds.authserver", importOptions = ImportOption.DoNotIncludeTests.class)
class Phase11ArchitectureTests {

    /// Rule 1: login, onetimetoken and sso must not depend on authorization.
    /// They communicate a successful/failed attempt to authorization only via
    /// AuthenticationSuccessEvent/AbstractAuthenticationFailureEvent — a
    /// direct class dependency here would mean one mechanism package reaching
    /// into another's policy decisions instead of just reporting what
    /// happened.
    @ArchTest
    static final ArchRule loginOttSsoMustNotDependOnAuthorization = noClasses()
            .that().resideInAnyPackage(
                    "com.dkds.authserver.login..",
                    "com.dkds.authserver.onetimetoken..",
                    "com.dkds.authserver.sso..")
            .should().dependOnClassesThat().resideInAPackage("com.dkds.authserver.authorization..");

    /// Rule 3 (second half): nothing outside `security` may depend on it.
    /// `security` composes the filter chains from every feature package, so
    /// the dependency only ever runs one way; a reverse dependency would be a
    /// feature package reaching back into the composition root.
    @ArchTest
    static final ArchRule nothingMayDependOnSecurity = noClasses()
            .that().resideOutsideOfPackage("com.dkds.authserver.security..")
            .should().dependOnClassesThat().resideInAPackage("com.dkds.authserver.security..");

    /// Rule 4: common depends on nothing else inside the application — it's
    /// the one package every other package is free to depend on without
    /// creating a cycle.
    ///
    /// common currently holds only exception/ and ForwardedHeaderConfig (no
    /// classes exist there yet — DataInitializer and LoginRecordingListener,
    /// which used to live here, moved out in Phase 11: both need to reach
    /// into other domains to do their job, which only devdata's lack of any
    /// rule, and security's rule 3, permit). allowEmptyShould(true) because
    /// an empty match here is the correct, currently-true state, not a
    /// broken rule.
    @ArchTest
    static final ArchRule commonDependsOnNothingInternal = noClasses()
            .that().resideInAPackage("com.dkds.authserver.common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.dkds.authserver.user..",
                    "com.dkds.authserver.organization..",
                    "com.dkds.authserver.login..",
                    "com.dkds.authserver.onetimetoken..",
                    "com.dkds.authserver.sso..",
                    "com.dkds.authserver.authorization..",
                    "com.dkds.authserver.token..",
                    "com.dkds.authserver.security..")
            .allowEmptyShould(true);
}
