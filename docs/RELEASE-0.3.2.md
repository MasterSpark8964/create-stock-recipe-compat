# 0.3.2: nested smithing capacity and explicit failure reasons

Recursive planning defaults:

| Setting | Previous default | New default | Allowed range |
| --- | ---: | ---: | ---: |
| maxDependencyDepth | 6 | 16 | 1–32 |
| maxExpandedIngredients | 256 | 512 | 1–2048 |
| maxStockVariants | 4096 (fixed) | 16384 | 256–32768 |
| maxDependencyRecipes | 8192 (fixed) | 65536 | 1024–131072 |
| dependencySearchOperations | 80000 (fixed) | 2000000 | 80000–20000000 |
| maxDependencyDecisionFrames | 128 (fixed) | 256 | 64–384 |
| dependencyPlanningMillis | no separate deadline | 500 | 50–5000 |

Existing saved config values are not overwritten. In particular, earlier maxDependencyDepth=6 and maxExpandedIngredients=256 remain effective until updated. See recursive-limits-0.3.2.toml for values to merge into the existing [general] section.

Performance changes: per-plan stock/production candidate caches, stable smithing-slot predicates for cache reuse, and whole-recipe direct reservation where inputs are already stocked. The latter avoids one Java continuation per directly available unit. Caches never span transfers or stock changes. Recipe operations and smithing matches/assemble remain subject to cooperative planning limits. A slow third-party callback or allocator cannot be preempted mid-call.

Failure classification: distinguish exhausted search work, timeout, dependency depth, decision frames, stock variant count, recipe count, leaf material units, and Create's nine actual item types. A failed search that touched depth/unit/type limits reports a constraint rather than claiming a proven shortage. A completed no-plan result reports insufficient supply or an unavailable supported intermediate recipe, never certainty about unrecognized custom machines.

The direct capacity solver now accepts up to 2048 units/32768 stock entries, with an additional 16,777,216 matrix-cell bound and 262,144 candidate-edge bound. Its existing bounded flow/type search remains in place. These memory/complexity protections can reject extreme cases even when configurable recursive limits are higher. Create's nine-type order protocol limit is unchanged.

Validation: static review and assemble -x test compilation/reobfuscation only; no tests or game runs. No runtime/performance acceptance is claimed.
