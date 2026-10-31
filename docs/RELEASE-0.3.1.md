# 0.3.1: mod-aware recursive smithing

Fixes the 0.3.0 omission of smithing from intermediate recipe lookup.

- Collects registered SmithingRecipe implementations without restricting mod/item namespaces or requiring vanilla recipe classes/serializers.
- Resolves template, base and addition through the public recipe predicates, including recursively supplied inputs and exact stock NBT.
- Validates concrete combinations using matches and assemble on an isolated container. Checks the actual resulting item/NBT against the missing ingredient rather than treating the preview as the real output.
- Allows dynamic trim-style/custom results and empty previews. Preview similarity only affects search priority; it is not an eligibility filter.
- Honors explicitly allowed empty slots, shared stock, virtual batch surplus, cycle detection and existing search bounds. Actual packages still contain leaf input materials, not synthetic intermediate products.

The scope is the standard three-slot SmithingRecipe contract: one unit per nonempty slot. Recipe implementations with extra resources, nonstandard slot consumption, random output or external server-only behavior are not automatically supported. Other custom machine recipe interfaces remain outside this extension. Fixed-plan quantity scaling remains unchanged.

Static reference inspection: mapped Minecraft/Forge 1.20.1 SmithingRecipe interface, SimpleContainer and SmithingTransformRecipe assemble implementation; the latter copies base NBT onto its actual output. The planner calls the public recipe behavior instead of recreating those rules.

Validation: compilation/reobfuscation only, via assemble -x test. No tests or game launches were performed, as requested. Runtime compatibility with individual third-party implementations is not claimed as verified.
