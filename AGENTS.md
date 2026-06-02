# Project Overview

`org.apache.sling.jcr.resource` is an OSGi bundle that implements Sling's JCR-backed resource provider and resolver internals. It maps JCR nodes/properties to Sling `Resource` objects, handles session/provider-state lifecycle, supports query and binary download integrations, exposes JCR-backed `ValueMap` implementations, and emits resource-change events from JCR observation. The bundle runs in an OSGi container with a JCR repository (typically Jackrabbit Oak).

# Core Commands

```bash
# Build and package (skips tests)
mvn clean package -DskipTests

# Full build with tests
mvn clean install

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=JcrResourceProviderTest

# Run session-handling focused provider tests
mvn test -Dtest=JcrResourceProviderSessionHandlingTest

# Run a single test method
mvn test -Dtest=JcrValueMapTest#testPutMultipleValues

# Apply Spotless code formatting (inherited from sling-bundle-parent)
mvn spotless:apply

# Check formatting without applying
mvn spotless:check

# License header check (Apache RAT)
mvn apache-rat:check

# API baseline check
mvn bnd-baseline:baseline

# Generate coverage report
mvn test jacoco:report
```

No dev server — this is a pure OSGi bundle deployed into a Sling/Felix runtime.

# Project Layout

```
pom.xml                          Maven build descriptor
bnd.bnd                          OSGi metadata (optional scripting import, conditional packages, Sling namespaces/nodetypes)
src/
  main/
    java/org/apache/sling/jcr/resource/
      api/
        JcrResourceChange.java          Public API type for JCR-backed resource change details
        JcrResourceConstants.java       Public constants for JCR resource handling
        package-info.java               Exported package metadata/versioning annotations
      internal/
        HelperData.java                 Shared runtime helper/context data for resource/value map internals
        JcrListenerBaseConfig.java      OSGi config model for JCR observation listener behavior
        JcrResourceListener.java        JCR observation listener and event bridge
        JcrModifiableValueMap.java      Writable ValueMap backed by a JCR Node
        JcrValueMap.java                Read-only ValueMap backed by JCR properties
        JcrSystemUserValidator.java     Validates service-user principal names
        NodeUtil.java                   JCR node utility helpers
        scripting/
          JcrObjectsBindingsValuesProvider.java  Exposes JCR objects to script bindings
        helper/
          JcrPropertyMapCacheEntry.java Property map caching
          AccessLogger.java             Structured access logging helpers
          JcrResourceUtil.java          JCR query/value conversion utility methods
          LazyInputStream.java          Lazy binary stream wrapper for JCR values
          Converter.java and *Converter.java  Type conversion helpers (Boolean, Date, Calendar, Number, String, ZonedDateTime)
          jcr/
            JcrResourceProvider.java         Main JCR ResourceProvider implementation
            JcrProviderStateFactory.java     Session/provider-state lifecycle factory
            JcrProviderState.java            Provider state holder
            JcrItemResourceFactory.java      Creates Resource from JCR Item
            JcrItemResource.java             Base resource wrapping a JCR Item
            JcrNodeResource.java             Resource wrapping a JCR Node
            JcrPropertyResource.java         Resource wrapping a JCR Property
            JcrNodeResourceIterator.java     Iterator over node-backed resources
            JcrNodeResourceMetadata.java     Metadata support for node resources
            BasicQueryLanguageProvider.java  Query language provider integration
            BinaryDownloadUriProvider.java   Direct binary download URI support
            JcrExternalizableInputStream.java URI-capable lazy binary InputStream wrapper
            ContextUtil.java                 Context/resource-resolver utility methods
    resources/SLING-INF/nodetypes/   CND node-type definitions (folder, resource, vanitypath, redirect, mapping)
  test/
    java/…                           JUnit 4 tests mirroring the main package structure (includes provider session-handling coverage)
target/                              Build output (generated classes, OSGI-INF, surefire-reports, baseline report)
```

# Development Patterns & Constraints

- **Java version**: source/target compatibility Java 8 (`sling.java.version=8`).
- **Parent POM**: inherits build defaults/checks from `org.apache.sling:sling-bundle-parent:66`.
- **OSGi annotations**: use `org.osgi.service.component.annotations` (R6/R7). Do not use Felix SCR annotations.
- **Nullability**: annotate with `org.jetbrains.annotations` (`@NotNull`, `@Nullable`) on public API where applicable.
- **License header**: every `.java` and `.xml` source file must carry the Apache 2.0 license block. Run `mvn apache-rat:check` to verify.
- **Formatting**: Spotless is enforced by the parent POM. Run `mvn spotless:apply` before committing. 4-space indentation; no tabs.
- **Import order**: follow the Spotless-enforced order (static imports → `javax.*` → `java.*` → third-party → internal).
- **Internal API**: everything under `*.internal.*` is private API. Do not expose internal types in public OSGi service contracts.
- **Public API compatibility**: types in `org.apache.sling.jcr.resource.api` are baseline-checked; keep semantic versioning constraints in mind.
- **OSGi descriptors**: generated at build time from annotations/bnd; do not hand-edit generated `OSGI-INF` files.
- **Optional scripting API**: `org.apache.sling.scripting.api` is imported with `resolution:=optional`; guard scripting-dependent paths.

# Git Workflow

- Default branch: `master`.
- Feature branches: `feature/<jira-id>-short-description` or `fix/<jira-id>-short-description`.
- Commit messages: short imperative subject line; reference JIRA issue (`SLING-XXXXX`) where applicable.
- PRs target `master`. CI runs Maven build + tests via Jenkins (`Jenkinsfile` at repo root).
- See [CONTRIBUTING.md](CONTRIBUTING.md) for Apache CLA and code-review process.

# Testing Guidelines

- **Framework**: JUnit 4 (`junit:junit`), Mockito 5, Hamcrest 2, JMock.
- **Sling testing**: `org.apache.sling.testing.sling-mock.junit4` + `sling-mock-oak` for integration-style tests against an in-memory Oak repository.
- **Test location**: `src/test/java/` mirroring the main package structure. Test classes end with `Test`.
- **Shared base classes**: `JcrItemResourceTestBase`, `SlingRepositoryTestBase` — extend these for tests needing a live JCR session.
- **Session lifecycle coverage**: `JcrResourceProviderSessionHandlingTest` covers provider session handling and cleanup paths.
- **Coverage report**: `mvn test jacoco:report` (JaCoCo is inherited from parent POM).
- Do not use `@RunWith(MockitoJUnitRunner.class)` and `@Rule MockitoRule` together in the same class.

# Gotchas

- **Oak version pinned**: `oak.version=1.44.0` is the minimum for `JackrabbitNode.getPropertyOrNull`. Bumping below 1.44.0 breaks compilation.
- **Jackrabbit compatibility**: `jackrabbit.version=2.18.0` is part of the current dependency baseline.
- **sling-mock exclusion**: `org.apache.sling.testing.sling-mock.junit4` must exclude `org.apache.sling.jcr.resource` to avoid classpath conflicts with the bundle under test (configured in `pom.xml`).
- **`bnd-baseline`**: baseline checks enforce semantic versioning for exported packages; API changes may require package version updates.
- **Conditional packages**: `org.apache.jackrabbit.util` and `org.apache.jackrabbit.name` are inlined via `-conditionalpackage` in `bnd.bnd`; do not add them as explicit `Import-Package` entries.
- **Scripting API is optional**: `org.apache.sling.scripting.api` is optional at runtime; code must handle its absence.
- **Adapter metadata generation**: `sling-maven-plugin` generates adapter metadata during `process-classes`; do not hand-maintain generated metadata.
- **No standalone runner**: the bundle cannot run standalone; deploy into Sling/Felix or use `sling-mock-oak` for repository-backed tests.

# Security

<!-- sling-security-default:start -->
The threat model for this project is https://github.com/apache/sling/blob/master/docs/threat-model.md .
<!-- sling-security-default:end -->

