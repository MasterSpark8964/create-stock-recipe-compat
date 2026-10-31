# 0.3.0: recursive shortage materials

When A+B produces C and A is unavailable, a supported D+E -> A recipe allows B+D+E to be requested. Shared stock, partial intermediate stock, alternative ingredients, recipe batch yields and surplus reuse within the initial plan are handled by a bounded backtracking planner. Cycles are excluded per active recipe path.

Configuration: expandMissingMaterials=true, maxDependencyDepth=6 (range 1–12). Intermediate recipe sources are the client world's ordinary non-special crafting, cooking and stonecutting recipes. Probabilistic/custom processing, dynamic special recipes, fluids, fuel, returned containers and machine execution are not inferred. Stock-sufficient ordinary crafting remains native; expanded orders use material-only synthetic backing.

The initial solution is frozen into exact leaf ingredients so existing Create order adjustments/removal work against actual dispatched stock. Subsequent quantity changes scale this fixed plan, with no new recipe lookup or global batch-surplus optimization. Re-add to recompute against current stock. No minimum-cost guarantee. Limits are documented in README.md; failure rejects the entire request.

Validation: static source inspection and `assemble -x test` compilation/reobfuscation only. No tests, integration runs or game launches were performed for this release in accordance with the user's instruction. This is a newly implemented, runtime-unverified feature.

No original modpack or Create JAR was modified. Install the client addon by replacing the previous version.
