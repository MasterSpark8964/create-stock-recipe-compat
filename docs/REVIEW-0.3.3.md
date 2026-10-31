# 0.3.3 static review

## Findings and changes

1. Ordinary-crafting shortage preflight could throw CapacityMatcher.SearchLimit outside the transfer method's existing handler, turning a complexity limit into a generic internal error. The outer mixin now returns the localized search-limit error for that case.
2. Reaching maximum depth unconditionally marked a depth failure, even when no eligible non-cyclic continuation remained. Depth is now marked only when a supported production or candidate smithing continuation exists. Unknown dynamic smithing behavior remains conservatively classified; this is not a proof that arbitrary unsupported recipes do not exist.
3. Smithing previews were repeatedly fetched in two passes on recursive revisits. Preview stacks and ordered smithing candidate lists are now cached per planning invocation. These caches do not survive stock updates or another transfer.
4. For the exact SmithingTransformRecipe implementation, a known vanilla/strict-NBT ingredient item set can exclude transforms whose invariant output item is different. This is based on the inspected implementation, not an item/mod namespace whitelist. Subclasses and custom/dynamic recipes retain the assemble-based path; inherited NBT is still validated on the actual assembled output.
5. Each constrained-allocation parent frame retained its completed dense matrix while recursively searching child alternatives. The matrix reference is now cleared once the used-type set has been collected. Independent graph/matrix limits remain unchanged.
6. Inventory snapshot collection now participates in the cooperative deadline/work accounting. Deadline comparison uses elapsed nanoTime subtraction.

## Static inspection coverage

Inspected recursive stock reservation and backtracking, intermediate surplus accounting, concrete smithing inputs/assembly, frozen leaf variants, native Create request/remove/revalidation behavior, exception routes, configuration limits, mixin boundaries and release metadata. No assertion of exhaustive correctness is made from source inspection alone.

Compiled/reobfuscated using assemble -x test. No test tasks, performance benchmarks, integration clients or game launches were run. Existing functional limitations remain: frozen-plan scaling, bounded search, standard three-slot smithing consumption, unsupported custom machine resources, and no automatic execution of intermediate recipes. The GUI planning deadline is cooperative and cannot preempt third-party callbacks.
