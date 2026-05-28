# M7-P3b C3.5 Pre-Estimate: LinkageCondition Jackson Deserialization

## Status

Pre-estimate gate. No implementation code has landed for C3.5.

Current anchor: `29b6317`. OpenAPI MD5:
`890e595c6351ee53788d35354b2412a3`. Backend tests: `458/80`. Frontend
Vitest: `81`. Migrations: `11`. humanpending: `135`.

C3.5 is an inserted blocker-fix cluster between C3 and C4. It is explicitly
not a retroactive rewrite of C1-C3.

## Evidence From The Throwaway Probe

The probe created a schema with:

- top-level text field `driver`;
- top-level text field `target`;
- `target.visibleWhen = { field: "driver", op: "eq", value: "show" }`.

It then executed:

```java
Map<String, Object> stored = objectMapper.convertValue(document, new TypeReference<>() {});
objectMapper.convertValue(stored, SchemaDocument.class);
```

Result:

```text
Cannot construct instance of `com.labelhub.api.generated.model.LinkageCondition`
(no Creators, like default constructor, exist)
through reference chain:
SchemaDocument["fields"] -> ArrayList[1] -> SchemaField["visibleWhen"]
```

This matches the generated model shape:

```java
public interface LinkageCondition {}
public class LinkageAtomicCondition implements LinkageCondition { ... }
public class LinkageConditionGroup implements LinkageCondition { ... }
```

The probe file was deleted after the failure. Worktree was clean afterward.

## Scope

C3.5 implements one backend capability:

> The application `ObjectMapper` can deserialize stored linkage JSON into the
> generated `LinkageCondition` subtypes by JSON shape.

C3.5 does not change validation semantics or DSL behavior.

## Implementation Plan

### 1. Add a deserializer

Create:

```text
services/api/src/main/java/com/labelhub/api/module/schema/json/LinkageConditionDeserializer.java
```

Recommended implementation:

```java
public final class LinkageConditionDeserializer extends JsonDeserializer<LinkageCondition> {
    @Override
    public LinkageCondition deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode node = codec.readTree(parser);
        if (node == null || !node.isObject()) {
            return (LinkageCondition) context.handleUnexpectedToken(LinkageCondition.class, parser);
        }
        if (node.has("field") || node.has("op")) {
            return codec.treeToValue(node, LinkageAtomicCondition.class);
        }
        if (node.has("allOf") || node.has("anyOf")) {
            return codec.treeToValue(node, LinkageConditionGroup.class);
        }
        return (LinkageCondition) context.weirdStringException(
            node.toString(),
            LinkageCondition.class,
            "LinkageCondition must contain field/op or allOf/anyOf"
        );
    }
}
```

Implementation can use `context.reportInputMismatch(...)` instead of
`weirdStringException(...)` if that produces clearer failure messages. The key
requirement is controlled Jackson failure, not silent null.

### 2. Register it with Spring ObjectMapper

Create or extend a config file such as:

```text
services/api/src/main/java/com/labelhub/api/config/JacksonConfig.java
```

Recommended shape:

```java
@Configuration
public class JacksonConfig {
    @Bean
    Module labelhubJacksonModule() {
        SimpleModule module = new SimpleModule("labelhub-jackson");
        module.addDeserializer(LinkageCondition.class, new LinkageConditionDeserializer());
        return module;
    }
}
```

Spring Boot auto-registers `Module` beans with the application
`ObjectMapper`. This is preferred over creating a separate mapper because the
bug is specifically in injected production mappers.

### 3. Add permanent tests

Create:

```text
services/api/src/test/java/com/labelhub/api/module/schema/json/LinkageConditionDeserializationTest.java
```

Recommended test split:

1. local mapper with the module:
   - atomic `visibleWhen` round-trip;
   - group `requiredWhen` round-trip;
   - nested child linkage round-trip;
   - malformed condition fails in a controlled way.
2. Spring mapper integration:
   - `@SpringBootTest`;
   - autowire `ObjectMapper`;
   - perform the same atomic/group round-trip;
   - this proves the real application mapper is configured.

If a full `@SpringBootTest` is too heavy for this small cluster, an
`ApplicationContextRunner` or `@JsonTest` can be used, but it must prove Spring
auto-registered the module. A test that only calls `new ObjectMapper()` is not
enough.

### 4. Leave production logic alone

Do not change:

- `SessionService`;
- `SchemaDtoMapper`;
- `SchemaService`;
- `SchemaValidator`;
- `AnswerPayloadValidator`;
- `LinkageEvaluator`.

Those paths should work by virtue of using the injected configured mapper.

## Shape Rules

Deserializer classification must match existing C1/C2/C3 semantics:

| JSON keys | Concrete type |
|---|---|
| `field` or `op` | `LinkageAtomicCondition` |
| `allOf` or `anyOf` | `LinkageConditionGroup` |
| neither | controlled deserialization failure |

If both atomic and group keys exist, classify as atomic because that matches
the existing `isAtomicCondition` precedence in frontend publish validation and
frontend evaluator. Publish-time validation still rejects malformed group shape
after deserialization where applicable.

## Malformed JSON Policy

Malformed condition objects should not become null. They should fail with a
controlled Jackson mapping exception. Rationale:

- publish-time schema validation rejects malformed DSL before storage;
- if bad data reaches persistence anyway, submit/render should fail loudly
  instead of silently treating the field as unlinked;
- C4 can then rely on published valid schema data.

## File Estimates

| File | Estimate |
|---|---:|
| `LinkageConditionDeserializer.java` | 80 |
| `JacksonConfig.java` | 25 |
| `LinkageConditionDeserializationTest.java` | 150 |
| Optional Spring mapper proof helper | 25 |
| **Total** | **~280** |

C3.5 caps:

- soft cap: `250`;
- hard cap: `400`.

If implementation approaches the hard cap, do not broaden scope. Keep only the
deserializer, registration, and round-trip tests.

## Verification Plan

Run:

```bash
mvn -pl services/api -Dtest=LinkageConditionDeserializationTest test
mvn -pl services/api compile
```

Then run either:

```bash
mvn -pl services/api test
```

or, if sandbox/Testcontainers blocks broad execution, report the D-record and
at minimum run:

```bash
mvn -pl services/api -Dtest=AnswerPayloadValidatorCorpusTest,AnswerPayloadValidatorLinkageTest,SchemaValidatorTest,LinkageConditionDeserializationTest test
```

Also run:

```bash
bash scripts/check-protected-endpoints.sh
```

Frozen checks:

- `md5 -q packages/contracts/openapi/labelhub.yaml` remains
  `890e595c6351ee53788d35354b2412a3`;
- migrations count remains `11`;
- `grep -cE "^- \\[" humanpending.md` remains `135`;
- no diff in generated model files, frontend, OpenAPI, `pom.xml`, migrations,
  or C1/C2/C3 evaluator/validator logic.

## Stop Conditions

STOP before committing if:

- Spring `ObjectMapper` does not pick up the module;
- deserializer requires modifying generated files;
- fixing the issue appears to require changing OpenAPI shape or generated
  codegen settings;
- any existing C1/C2/C3 tests fail for semantic reasons after the module is
  registered.

## C4 Resume Criteria

C4 can resume only after:

1. stored `schema_json` with atomic and group linkage round-trips into
   `SchemaDocument`;
2. the real injected `ObjectMapper` is verified;
3. the C4 submit linkage integration test can exercise the repaired path.
