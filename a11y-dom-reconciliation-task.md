# Preserve A11Y DOM Node Identity During Semantics Tree Synchronization

## Summary

Refactor Compose Web accessibility-tree synchronization so that surviving semantics nodes remain continuously attached to the DOM and retain their existing `HTMLElement` identity.

The synchronization must reconcile the current semantics tree with the existing A11Y DOM tree incrementally. It must not clear and rebuild a parent's children on every semantics or layout update.

This work must be implemented and validated as an independent accessibility-tree correctness improvement.

## Background

`ComposeWebSemanticsListener` maintains an invisible DOM tree corresponding to the Compose semantics tree.

The current synchronization algorithm removes all children of an existing A11Y element and appends them again in semantics order. Even when a Compose semantics node survives an update and retains the same `HTMLElement` object, that element is temporarily disconnected from the document.

Removing a DOM element can destroy its platform accessibility object. Reattaching the same JavaScript object does not guarantee that Safari/WebKit or another browser will preserve the corresponding accessibility-node identity.

This causes observable assistive-technology regressions:

- VoiceOver can lose its current item when an unrelated semantics or layout update occurs.
- VoiceOver may move its cursor to the nearest surviving container or group.
- Accessibility highlight bounds can temporarily refer to an obsolete accessibility object.
- Repeated detach/reattach operations produce unnecessary DOM and accessibility-tree mutations.

The required invariant is:

> If a semantics node survives an update, its corresponding DOM element must remain continuously connected to the A11Y DOM tree whenever its effective DOM parent also survives.

## Relevant code

Primary implementation:

```text
compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/platform/accessibility/ComposeWebSemanticsListener.kt
```

Existing Web A11Y tests:

```text
compose/ui/ui/src/webTest/kotlin/androidx/compose/ui/platform/a11y/
```

The problematic behavior currently includes:

```kotlin
removeAllChildrenOf(htmlNode)
```

followed by unconditional insertion such as:

```kotlin
htmlParent.appendChild(htmlNode)
```

The implementation must no longer use clear-and-rebuild behavior for normal semantics synchronization.

## Goals

1. Preserve the `HTMLElement` identity of every surviving semantics node.
2. Keep surviving elements continuously connected when their effective parent is unchanged.
3. Reconcile child order without detaching children already in the correct position.
4. Move an element only when its effective parent or sibling position actually changes.
5. Remove elements only when their semantics nodes are genuinely absent from the synchronized tree.
6. Minimize unnecessary attribute, text, and DOM-tree mutations where practical.
7. Preserve all existing semantics-to-ARIA behavior and tree ordering.
8. Work for both Kotlin/JS and Kotlin/WasmJS targets.

## Non-goals

This task must not implement or redesign:

- accessibility-focus restoration after a semantics node is genuinely removed;
- geometry calculation or ARIA mapping;
- changes to public Compose semantics APIs;
- a general-purpose virtual DOM framework.

The reconciliation mechanism may support internal non-semantics children, but feature-specific behavior must remain outside the generic tree reconciler.

## Required behavior

### 1. Stable identity for surviving nodes

If a semantics node ID exists before and after synchronization:

- the same `HTMLElement` instance must be reused;
- a new element must not be created;
- the element must not be removed merely to rebuild sibling ordering;
- if its effective parent is unchanged, it must remain continuously connected to that parent and to the document.

In conceptual terms:

```kotlin
assertSame(elementBefore, elementAfter)
```

Identity preservation alone is not enough. The element must not be temporarily disconnected between those observations.

### 2. Insertion of new nodes

When a new semantics node appears:

- create exactly one corresponding `HTMLElement`;
- initialize its semantics attributes and geometry;
- insert it directly at the correct position under its effective parent;
- do not detach or recreate unaffected siblings;
- update all forward and reverse lookup maps consistently.

### 3. Removal of obsolete nodes

When a semantics node is absent from the new tree:

- remove its element from the DOM;
- remove its entries from all node/element lookup structures;
- clean up listeners and node-specific resources;
- do not remove or reinsert surviving siblings as part of the cleanup.

Subtrees may be removed as a consequence of removing their root, but internal bookkeeping must still be cleaned consistently for every obsolete semantics-node ID.

### 4. Reordering siblings

When sibling order changes:

- use incremental keyed reconciliation based on semantics-node ID;
- leave already correctly positioned elements untouched;
- move only elements whose position actually changed;
- produce the exact order exposed by `SemanticsNode.replacedChildren`;
- avoid clearing the parent or detaching all children.

`Node.insertBefore`, `Node.appendChild`, or equivalent DOM operations may be used, but only when a placement change is required.

Calling `appendChild` unconditionally is not acceptable because it moves an already attached element and creates an unnecessary child-list mutation.

### 5. Reparenting

If a surviving semantics node's effective parent changes:

- reuse the existing `HTMLElement`;
- move it directly from the old parent to the correct position under the new parent;
- do not recreate the element;
- do not rebuild unaffected children of either parent.

A reparented element cannot remain continuously attached to its old parent, but its identity must remain stable and the move must be limited to the required DOM operation.

### 6. Nodes with no semantics children

If an existing node changes from having children to having none:

- remove only obsolete semantics child elements;
- do not replace the parent element;
- do not retain stale semantic children.

If an existing node changes from having no children to having children, insert only the new children in the correct order.

### 7. Internal non-semantics DOM children

The reconciliation algorithm must distinguish semantics children from internal implementation elements that are not represented by semantics-node IDs.

Requirements:

- internal children must not accidentally be treated as obsolete semantics nodes;
- their required placement relative to semantics children must be explicit and deterministic;
- synchronization must not delete an internal child unless the owning feature requests its removal;
- semantics-child order must remain correct regardless of internal children.

The implementation should expose a small internal reconciliation abstraction rather than adding feature-specific exceptions to generic tree traversal.

### 8. Text and attribute updates

For a surviving element, update properties in place.

Where assignment recreates native DOM descendants or accessibility objects, avoid assigning the same value unnecessarily. In particular, review uses of:

```kotlin
htmlNode.innerText = value
```

Expected behavior:

- unchanged text must not be reassigned merely because synchronization ran;
- changed text must be reflected without replacing the semantic element itself;
- removing a semantics property must remove or reset its corresponding DOM state where required;
- attribute updates must not change child-tree identity unnecessarily.

This requirement applies especially to text nodes that may be the object VoiceOver currently exposes under a containing semantic element.

### 9. Lookup-map consistency

After every synchronization:

- every synchronized semantics-node ID maps to exactly one live `HTMLElement`;
- every mapped element has the expected semantics node in the reverse map;
- removed nodes are absent from all maps;
- no stale parent mappings remain;
- each semantics element is attached to the correct DOM parent;
- DOM sibling order matches semantics sibling order.

## Suggested reconciliation model

Use semantics-node IDs as stable keys.

A suitable high-level algorithm is:

1. Traverse the new semantics tree and collect the expected node, parent, and ordered-child relationships.
2. Reuse or create the `HTMLElement` for each expected node.
3. Update each element's properties in place.
4. Reconcile each parent's ordered semantics children incrementally.
5. Remove obsolete elements and bookkeeping after expected nodes have been identified.

For each parent, reconciliation should compare the expected ordered elements with the current semantic children and perform only necessary insertions or moves.

Pseudocode:

```kotlin
fun reconcileChildren(
    parent: HTMLElement,
    expectedChildren: List<HTMLElement>,
) {
    var expectedPosition = firstSemanticChildPosition(parent)

    expectedChildren.forEach { child ->
        if (child.parentElement != parent || child != expectedPosition) {
            parent.insertBefore(child, expectedPosition)
        }
        expectedPosition = nextSemanticSiblingAfter(child)
    }

    removeUnexpectedSemanticChildren(parent, expectedChildren)
}
```

The exact implementation may differ, but it must preserve stable nodes and avoid clear-and-rebuild behavior.

## Test requirements

Add focused browser tests that validate DOM identity and mutation behavior. Tests must run for the Web test targets used by this module.

### Test 1: unchanged child remains connected

Given a parent with multiple semantic children, trigger a semantics or layout update that does not remove a selected child.

Verify:

- the child before and after is the same `HTMLElement` instance;
- it remains connected after synchronization;
- no removal mutation for that child occurred during synchronization.

Use a `MutationObserver` on the A11Y subtree to detect temporary removal. Checking only the final `isConnected` value is insufficient.

### Test 2: attribute-only update preserves identity

Change a property such as content description, disabled state, role-related state, or test-visible semantics while retaining the same node.

Verify:

- element identity is unchanged;
- parent identity is unchanged;
- sibling identities and order are unchanged;
- only the expected property changes.

### Test 3: text update preserves semantic element

Change text on a surviving semantics node.

Verify:

- the containing `HTMLElement` identity is unchanged;
- the new text is exposed;
- unrelated siblings are not removed or reinserted.

Where possible, assert that assigning an unchanged text value does not produce child-list mutations.

### Test 4: insert one child

Insert a semantics child between existing siblings.

Verify:

- existing sibling elements preserve identity;
- existing siblings are not temporarily disconnected;
- exactly one new semantic element is created;
- final order is correct.

### Test 5: remove one child

Remove one semantics child while retaining its siblings.

Verify:

- only the removed child's element becomes disconnected;
- surviving sibling identities remain stable;
- surviving siblings are not temporarily disconnected;
- all reverse and ID lookup behavior remains correct through observable output.

### Test 6: reorder children

Change semantics sibling order without replacing their semantics-node identities.

Verify:

- all child `HTMLElement` instances are reused;
- final order matches semantics order;
- only nodes requiring movement generate placement mutations;
- the parent is never cleared.

### Test 7: reparent a surviving node

Move a semantics node between two surviving parents.

Verify:

- the moved node keeps the same `HTMLElement` identity;
- it ends under the correct new parent;
- unaffected nodes in both parents remain attached and retain identity.

### Test 8: surviving node in a frequently updated subtree

Create a subtree whose composition, layout, or semantics changes repeatedly while at least one tagged semantics node survives every update.

Verify:

- the surviving node's `HTMLElement` is identical before and after every update;
- a `MutationObserver` never observes that node being removed;
- its parent remains the same when the semantics structure does not require reparenting.

### Test 9: genuinely disposed node

Change composition so that one semantics node is genuinely absent from the resulting semantics tree.

Verify:

- its DOM element is removed;
- surviving sibling elements retain their identity;
- no stale element with the removed test tag remains in the A11Y DOM.

## Mutation-observer expectations

Tests should distinguish between:

- an element being moved because its parent/order genuinely changed;
- an element being temporarily removed and reinserted during routine synchronization;
- an element being permanently removed because its semantics node disappeared.

A surviving node under an unchanged parent must generate no mutation record in which it appears in `removedNodes`.

Tests should avoid relying solely on final HTML strings because identical final markup does not prove accessibility-node continuity.

## Acceptance criteria

The task is complete when all of the following are true:

- [ ] Normal synchronization no longer clears a surviving semantics parent's children.
- [ ] Existing elements are not unconditionally appended on every synchronization.
- [ ] A surviving semantics-node ID always reuses its existing `HTMLElement`.
- [ ] A surviving node under an unchanged parent remains continuously connected.
- [ ] New nodes are inserted at the correct position without disturbing unaffected siblings.
- [ ] Removed nodes and their bookkeeping are cleaned up without rebuilding surviving siblings.
- [ ] Reordered and reparented nodes preserve `HTMLElement` identity.
- [ ] Internal non-semantics children are handled explicitly and are not accidentally deleted.
- [ ] Unchanged text is not reassigned in a way that recreates native descendants unnecessarily.
- [ ] Final DOM hierarchy and sibling order exactly match the Compose semantics tree.
- [ ] Mutation-observer tests cover insertion, removal, reordering, and a surviving node in a frequently updated subtree.
- [ ] Existing Web accessibility tests pass for Kotlin/JS and Kotlin/WasmJS.
- [ ] New reconciliation tests pass for Kotlin/JS and Kotlin/WasmJS.
- [ ] Changed Kotlin files pass `:ktCheckFile`.

## Manual validation

Automated tests cannot observe the VoiceOver cursor directly. After implementation, manually verify in Safari with VoiceOver:

1. Open content containing a group with several accessible children.
2. Move the VoiceOver cursor to a child that will survive the next update.
3. Trigger repeated state, layout, and semantics updates without removing that child.
4. Confirm that VoiceOver remains on the same child rather than returning to the group.
5. Confirm that its highlight remains associated with the same visual element.
6. Remove the selected child genuinely and document the resulting fallback behavior separately.

## Constraints

- Keep the change internal to Web accessibility synchronization.
- Do not introduce a new public API.
- Do not use test tags as reconciliation keys; production reconciliation must use semantics-node identity.
- Do not solve the problem by restoring browser DOM focus after every update. VoiceOver's virtual cursor is not equivalent to `document.activeElement`.
- Do not rely on recreating an identical element and expecting assistive technology to treat it as the same accessibility object.
- Avoid unrelated changes to geometry or ARIA mappings.

## Expected result

After this work, routine semantics and layout updates should produce a stable A11Y DOM. Assistive technologies should be able to retain their current accessibility object when the corresponding Compose semantics node survives the update.
