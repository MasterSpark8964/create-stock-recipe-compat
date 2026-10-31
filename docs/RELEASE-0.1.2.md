# 0.1.2 optimization release

## Changes

- Fixed-choice recipes compute the maximum batch directly from per-item inventory divided by required occurrences. Only the final allocation matrix is created; repeated binary-search allocations are avoided for this case.
- Multi-choice recipes retain complete capacity matching. Candidate capacity per ingredient and total relevant stock bound the maximum batch search. The returned allocation is the plan for the reported batch count, including the nine-type constraint.
- Count-expanded ingredients reuse candidate lists by Ingredient object identity within one calculation. Repeated units retain separate capacity demand; nothing is cached across stock updates or requests.
- More than 4096 relevant available stock types are rejected before building the candidate matrix. Solver dimension bounds are checked before copying input arrays.
- The representable output-count bound applies to single-batch preview as well as maximum-batch requests.

## Validation

Java 17 production build/reobfuscation and integration-test compilation passed. All 19 JUnit tests passed: the existing 2,000 exact-allocation comparisons plus 2,000 maximum-batch comparisons against an independent brute-force allocator. Added explicit checks for shared fixed stock, batch caps, type limits, empty candidates, negative/unavailable stock and Integer.MAX_VALUE totals.

No numerical speedup or in-game frame-rate improvement is claimed. Game integration was not rerun for 0.1.2; the existing physical-package evidence is from 0.1.0. JEI extraction rules, mixin targets and Create packet delivery are unchanged. MaterialAllocation now delegates maximum-batch calculation to the tested solver.

Target dependency versions and generic compatibility boundaries remain unchanged; see README.md and PORTABILITY.md. Replace the previous addon JAR, do not install multiple versions together. No modpack files were modified.
