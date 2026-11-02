# M7-P3b C3.5 Scope-Budget: LinkageCondition Jackson Round-Trip Fix

## Status

Pre-estimate gate for an inserted M7-P3b C3.5 blocker-fix cluster. No
production implementation has landed for this cluster.

Current anchor: `29b6317` (M7-P3b C4 gate). OpenAPI MD5:
`890e595c6351ee53788d35354b2412a3`. Backend tests: `458/80`. Frontend
Vitest: `81`. Migrations: `11`. humanpending: `135`.

C4 implementation attempted a throwaway round-trip probe before writing the
shared linkage corpus. The probe revealed a production-path blocker:

```text
Cannot construct instance of `com.labelhub.api.generated.model.LinkageCondition`
(no Creators, like default constructor, exist)
through reference chain:
SchemaDocument["fields"] -> ArrayList[1] -> SchemaField["visibleWhen"]
```

The temporary probe was removed and the worktree returned clean. C3.5 exists to
repair this blocker before C4 resumes.

## Problem

C1 generated:

- `LinkageCondition` as a marker interface;
- `LinkageAtomicCondition implements LinkageCondition`;
- `LinkageConditionGroup implements LinkageCondition`.

C1/C2/C3 tests mostly hand-built generated Java objects, so they did not cover
JSON or `Map` back into `SchemaDocument`.

Real production persistence stores schema versions as `Map<String, Object>` in
`schema_versions.schema_json`. The submit path then loads the bound schema
version and executes:

```java
objectMapper.convertValue(schemaVersion.getSchemaJson(), SchemaDocument.class)
```

With a stored `visibleWhen` or `requiredWhen`, Jackson has no concrete class for
the marker interface. Any persisted schema using linkage would fail at submit
time before `AnswerPayloadValidator` can evaluate it.

## Production Convert Paths

Grepped paths that materialize `SchemaDocument` from stored schema JSON:

1. `services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java`
   - `validateAnswerPayload(...)`
   - `objectMapper.convertValue(schemaVersion.getSchemaJson(), SchemaDocument.class)`
   - submit-time answer validation path.
2. `services/api/src/main/java/com/labelhub/api/module/schema/web/SchemaDtoMapper.java`
   - `dto.setSchemaJson(objectMapper.convertValue(entity.getSchemaJson(), SchemaDocument.class))`
   - schema/version API response mapping path.

Grepped write path:

1. `services/api/src/main/java/com/labelhub/api/module/schema/service/SchemaService.java`
   - `objectMapper.convertValue(schemaDocument, new TypeReference<>() {})`
   - publish-time conversion from request model to stored `Map`.

C3.5 must fix the Spring application `ObjectMapper`, not only a local test
mapper, so all existing injection sites inherit the behavior.

## Chosen Shape

Add a backend-only Jackson deserializer for `LinkageCondition` using the same
shape test already used by C1/C2/C3:

- object has `field` or `op` -> `LinkageAtomicCondition`;
- object has `allOf` or `anyOf` -> `LinkageConditionGroup`;
- otherwise fail with a controlled Jackson mapping error.

This preserves:

- OpenAPI JSON shape;
- generated model files;
- DSL semantics;
- OpenAPI MD5.

It does not add a third linkage interpretation rule. It formalizes the key
presence rules already used in C1 TypeScript publish validation, C1 Java
publish validation, C2 backend evaluator, and C3 frontend evaluator.

## Allowed Files

Expected implementation files:

| File | Purpose | Estimate |
|---|---|---:|
| `services/api/src/main/java/com/labelhub/api/config/JacksonConfig.java` or equivalent existing config file | Register a Jackson `Module` bean with a `LinkageCondition` deserializer on the Spring `ObjectMapper` | 30 |
| `services/api/src/main/java/com/labelhub/api/module/schema/json/LinkageConditionDeserializer.java` | Shape-based deserializer from JSON object to generated `LinkageAtomicCondition` or `LinkageConditionGroup` | 90 |
| `services/api/src/test/java/com/labelhub/api/module/schema/json/LinkageConditionDeserializationTest.java` | Permanent round-trip tests for atomic, group, nested linkage, and malformed shape | 130 |
| Optional small Spring-context test or mapper test | Proves the real injected `ObjectMapper` has the module, not only a local mapper | 40 |
| **Hand-authored total** | | **~290** |

Recommended C3.5 caps:

- soft cap: `250`;
- hard cap: `400`.

The soft estimate is slightly below the expected total to keep the fix tight;
the hard cap allows enough room for the Spring ObjectMapper registration test.

## Forbidden

- Do not modify generated OpenAPI files.
- Do not modify `packages/contracts/openapi/labelhub.yaml`.
- Do not modify C1/C2/C3 evaluator or validator logic.
- Do not modify frontend files.
- Do not modify P3a validation corpus or P3b linkage corpus plans.
- Do not modify migrations, `pom.xml`, or humanpending.
- Do not introduce a new DSL JSON shape.

## Required Tests

Permanent backend round-trip coverage:

1. atomic condition:
   - create a `SchemaDocument` with `visibleWhen: { field, op, value }`;
   - convert to `Map<String, Object>`;
   - convert back to `SchemaDocument`;
   - assert `visibleWhen instanceof LinkageAtomicCondition`;
   - assert `field`, `op`, and `value` are preserved.
2. group condition:
   - create a `SchemaDocument` with `requiredWhen: { anyOf: [...] }`;
   - round-trip;
   - assert `requiredWhen instanceof LinkageConditionGroup`;
   - assert nested atomic entries are preserved.
3. nested field:
   - include linkage under a nested child to prove recursive `SchemaField`
     deserialization also works.
4. malformed condition:
   - JSON object without `field`/`op`/`allOf`/`anyOf`;
   - assert controlled `JsonMappingException` / `IllegalArgumentException`
     rather than an unrecognized NPE or hidden null.
5. real Spring mapper:
   - verify the autowired application `ObjectMapper` performs the same
     round-trip. This closes the exact testing blind spot that hid the bug.

## Verification

Implementation report must include:

- targeted deserializer tests passing;
- a test proving the real Spring `ObjectMapper` is configured;
- `mvn -pl services/api compile`;
- `mvn -pl services/api test` or targeted backend test result with D-record if
  sandbox/Docker blocks broader execution;
- OpenAPI MD5 unchanged:
  `890e595c6351ee53788d35354b2412a3`;
- migrations unchanged: `11`;
- humanpending unchanged: `135`;
- no diff in generated OpenAPI model files, frontend, C1/C2/C3 evaluator logic,
  `pom.xml`, or migrations.

## Resume Path

After C3.5 is approved, resume C4 implementation unchanged:

- one shared `packages/contracts/fixtures/linkage-corpus.json`;
- zero default asymmetry;
- runtime + publish corpus cases;
- submit linkage integration test that now exercises the repaired production
  `schema_json -> SchemaDocument -> AnswerPayloadValidator` path.
