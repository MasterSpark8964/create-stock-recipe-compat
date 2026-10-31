# 0.1.1 optimization release

Based on the supplied create_stock_recipe_compat-0.1.0-reviewed-source.zip. Its only source differences were CapacityMatcher.java and CapacityMatcherTest.java; both reviewed changes were incorporated.

## Changes

- Retain independent matching budgets for each probe, so earlier binary-search probes do not consume later probes' budgets.
- Retain early validation of array shape, row presence, candidate indices and edge count.
- For inputs with exactly one candidate per ingredient, sum required capacity directly and verify actual new item types before allocating the result. This avoids flow graph construction and recursive type search for that case. Long totals avoid integer overflow.
- Deduplicate candidate indices while preserving order. Duplicate parallel edges could previously overwrite an allocation count in the returned matrix. Production stock enumeration normally produces unique indices; this also makes the solver robust for other callers.
- Copy solver inputs to keep the constructor snapshot stable against caller mutations.
- Visit identical excluded-type sets only once per probe. Alternative ingredients still use capacity matching and retain bounded search.

## Validation

Production build and reobfuscation succeeded. All 14 JUnit tests passed, including 2,000 deterministic comparisons with the independent brute-force allocator, repeated flow probes, duplicate indices, caller mutations and overflow protection. Integration test sources compile.

No timing benchmark or numerical speedup is claimed. This release has not been rerun in-game; recorded physical package and client integration evidence comes from 0.1.0, followed by the user's successful test report. JEI extraction, client mixins, protocol, and Create package delivery code are unchanged in 0.1.1.

## Installation

Replace the previous addon JAR with create_stock_recipe_compat-0.1.1.jar in the client mods folder; do not keep both versions. Target versions and compatibility limits are unchanged (see README.md and docs/PORTABILITY.md). No original modpack files were modified during development or packaging.
