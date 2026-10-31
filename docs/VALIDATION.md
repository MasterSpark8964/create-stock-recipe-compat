# Validation record

## Build and matching

Java 17 / ForgeGradle production build includes reobfJar. Nine JUnit matching tests pass, including 2,000 deterministic comparisons against an independent exhaustive allocator. Cases include overlapping alternatives, quantities, shared shortages, batch allocation, the nine-type limit, existing types, large stock counts and the 256-input limit.

## Observed client integration

See `evidence/client-integration.txt` for completed runtime results: actual JEI smithing, smelting, blasting, smoking and stonecutting layouts passed through the public Create handler; preview remained read-only; ordinary crafting passed through; custom JEI object fixtures verified 4/2/1 quantities, output batch scaling, overlapping alternatives, missing-stock rollback and fluid rejection.

## Observed physical package

See `evidence/package-native.txt`. The isolated E:/mc/run world used a filled blaze burner keeper. Server checks confirmed keeper presence, identical network UUID, online stock link and attachment to the packager. Server and client inventories each contained ten templates, ten diamond swords and ten netherite ingots.

The original Create sendIt method and PackageOrderRequestPacket produced a real packager heldBox containing exactly one netherite upgrade smithing template, one diamond sword and one netherite ingot.

Earlier test setup incorrectly used an empty burner and a manually constructed client menu. The setup was corrected to a filled burner and server-opened menu before the successful package test. The earlier failure is not treated as a successful check.

The user subsequently reported successful testing. No specific additional recipe matrix is inferred from that report.

## Limits of evidence

Additional Create-category and named crafting regression cases exist in integrationTest sources but are not counted as executed acceptance tests here. Ten modpack script samples were inspected read-only; they are not ten completed in-game tests. Dedicated-server join without CSRC, mechanical crafting/repackager regression, every third-party JEI plugin and production-obfuscated-client coverage have not been separately established by the recorded automated runs.

See `PORTABILITY.md` for the code-level scope and limitations. Test mods, generated worlds and integration harness classes are excluded from the release JAR.

## 0.1.1 follow-up

All 14 matching tests passed after the reviewed-source fixes and optimizations; production build/reobfuscation and integration-test compilation passed. The runtime evidence above remains from 0.1.0. See RELEASE-0.1.1.md for details.

## 0.1.2 follow-up

All 19 tests passed, including 2,000 exact-allocation and 2,000 maximum-batch comparisons against independent brute force. Production build/reobfuscation and integration-test compilation passed. No new game runtime evidence; see RELEASE-0.1.2.md.

## 0.2.0 delivery scope

The user prohibited further tests. No tests were run after that instruction. The existing build was packaged after static source inspection; no new runtime acceptance claim is made. See RELEASE-0.2.0.md.

## 0.3.0

Recursive shortage expansion: static review and assemble -x test compilation/reobfuscation only. No tests or game launches were performed. No runtime correctness claim is made for the new planner.

## 0.3.1

Registered smithing recursion was statically reviewed and compiled/reobfuscated with assemble -x test. No tests or game launches were performed. See RELEASE-0.3.1.md for interface scope and unverified runtime behavior.

## 0.3.2

Static review and assemble -x test compilation/reobfuscation only. No tests, performance benchmarks or game launches were run. Revised recursive limits and failure classification remain runtime-unverified.

## 0.3.3

Static inspection and assemble -x test compilation/reobfuscation only. No test tasks, game launches or performance benchmarks. Findings are recorded in REVIEW-0.3.3.md.

## 0.3.4

Static inspection and assemble -x test only. No tests or game launch. Recursive workstation scope and classification changes are documented in RELEASE-0.3.4.md.

## 0.3.5

Static inspection and assemble -x test only. No tests, benchmarks or game launches. Recursive exclusion and preview-throttling details are in RELEASE-0.3.5.md.

## 0.3.6

Static inspection and assemble -x test compilation/reobfuscation only. No tests, game launches or benchmarks. See RELEASE-0.3.6.md.
