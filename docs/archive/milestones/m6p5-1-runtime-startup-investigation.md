# M6-P5.1 Runtime Startup Investigation

Date: 2026-05-26

## Reproduction

Command:

```bash
LABELHUB_API_PORT=18080 \
OBJECT_STORAGE_ACCESS_KEY=labelhub \
OBJECT_STORAGE_SECRET_KEY=labelhub-secret \
OBJECT_STORAGE_BUCKET=labelhub-exports \
mvn -pl services/api -e spring-boot:run
```

Result: the API failed during Spring context startup before serving requests.

## Full Root Stack Trace

```text
2026-05-26T09:55:05.256-04:00  WARN 50274 --- [labelhub-api] [           main] ConfigServletWebServerApplicationContext : Exception encountered during context initialization - cancelling refresh attempt: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'aiRetryPolicy' defined in file [/Users/gods./Downloads/LabelHub - Platform/services/api/target/classes/com/labelhub/api/module/ai/service/AiRetryPolicy.class]: Failed to instantiate [com.labelhub.api.module.ai.service.AiRetryPolicy]: No default constructor found
2026-05-26T09:55:05.257-04:00  INFO 50274 --- [labelhub-api] [           main] o.apache.catalina.core.StandardService   : Stopping service [Tomcat]
2026-05-26T09:55:05.264-04:00  INFO 50274 --- [labelhub-api] [           main] .s.b.a.l.ConditionEvaluationReportLogger :

Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.
2026-05-26T09:55:05.268-04:00 ERROR 50274 --- [labelhub-api] [           main] o.s.boot.SpringApplication               : Application run failed

org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'aiRetryPolicy' defined in file [/Users/gods./Downloads/LabelHub - Platform/services/api/target/classes/com/labelhub/api/module/ai/service/AiRetryPolicy.class]: Failed to instantiate [com.labelhub.api.module.ai.service.AiRetryPolicy]: No default constructor found
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.instantiateBean(AbstractAutowireCapableBeanFactory.java:1337) ~[spring-beans-6.1.15.jar:6.1.15]
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBeanInstance(AbstractAutowireCapableBeanFactory.java:1222) ~[spring-beans-6.1.15.jar:6.1.15]
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean(AbstractAutowireCapableBeanFactory.java:562) ~[spring-beans-6.1.15.jar:6.1.15]
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBean(AbstractAutowireCapableBeanFactory.java:522) ~[spring-beans-6.1.15.jar:6.1.15]
	at org.springframework.beans.factory.support.AbstractBeanFactory.lambda$doGetBean$0(AbstractBeanFactory.java:337) ~[spring-beans-6.1.15.jar:6.1.15]
	at org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.getSingleton(DefaultSingletonBeanRegistry.java:234) ~[spring-beans-6.1.15.jar:6.1.15]
	at org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean(AbstractBeanFactory.java:335) ~[spring-beans-6.1.15.jar:6.1.15]
	at org.springframework.beans.factory.support.AbstractBeanFactory.getBean(AbstractBeanFactory.java:200) ~[spring-beans-6.1.15.jar:6.1.15]
	at org.springframework.beans.factory.support.DefaultListableBeanFactory.preInstantiateSingletons(DefaultListableBeanFactory.java:975) ~[spring-beans-6.1.15.jar:6.1.15]
	at org.springframework.context.support.AbstractApplicationContext.finishBeanFactoryInitialization(AbstractApplicationContext.java:971) ~[spring-context-6.1.15.jar:6.1.15]
	at org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:625) ~[spring-context-6.1.15.jar:6.1.15]
	at org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext.refresh(ServletWebServerApplicationContext.java:146) ~[spring-boot-3.2.12.jar:3.2.12]
	at org.springframework.boot.SpringApplication.refresh(SpringApplication.java:754) ~[spring-boot-3.2.12.jar:3.2.12]
	at org.springframework.boot.SpringApplication.refreshContext(SpringApplication.java:456) ~[spring-boot-3.2.12.jar:3.2.12]
	at org.springframework.boot.SpringApplication.run(SpringApplication.java:335) ~[spring-boot-3.2.12.jar:3.2.12]
	at org.springframework.boot.SpringApplication.run(SpringApplication.java:1363) ~[spring-boot-3.2.12.jar:3.2.12]
	at org.springframework.boot.SpringApplication.run(SpringApplication.java:1352) ~[spring-boot-3.2.12.jar:3.2.12]
	at com.labelhub.api.ApiApplication.main(ApiApplication.java:17) ~[classes/:na]
Caused by: org.springframework.beans.BeanInstantiationException: Failed to instantiate [com.labelhub.api.module.ai.service.AiRetryPolicy]: No default constructor found
	at org.springframework.beans.factory.support.SimpleInstantiationStrategy.instantiate(SimpleInstantiationStrategy.java:90) ~[spring-beans-6.1.15.jar:6.1.15]
	at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.instantiateBean(AbstractAutowireCapableBeanFactory.java:1331) ~[spring-beans-6.1.15.jar:6.1.15]
	... 17 common frames omitted
Caused by: java.lang.NoSuchMethodException: com.labelhub.api.module.ai.service.AiRetryPolicy.<init>()
	at java.base/java.lang.Class.getConstructor0(Class.java:3188) ~[na:na]
	at java.base/java.lang.Class.getDeclaredConstructor(Class.java:2494) ~[na:na]
	at org.springframework.beans.factory.support.SimpleInstantiationStrategy.instantiate(SimpleInstantiationStrategy.java:86) ~[spring-beans-6.1.15.jar:6.1.15]
	... 18 common frames omitted
```

## Root Cause

Root cause: `AiRetryPolicy` has two constructors and neither is marked for Spring injection.

Source:

```java
public AiRetryPolicy(OpenAiCompatibleProperties properties) {
    this(properties, AiRetryPolicy::sleepUnchecked);
}

AiRetryPolicy(OpenAiCompatibleProperties properties, LongConsumer sleeper) {
    this.properties = properties;
    this.sleeper = sleeper;
}
```

Compiled class shape confirmed with `javap -p`:

```text
public com.labelhub.api.module.ai.service.AiRetryPolicy(com.labelhub.api.module.ai.provider.OpenAiCompatibleProperties);
com.labelhub.api.module.ai.service.AiRetryPolicy(com.labelhub.api.module.ai.provider.OpenAiCompatibleProperties, java.util.function.LongConsumer);
```

Spring's single-constructor inference does not apply because the class has more than one declared constructor. With no `@Autowired` constructor and no default constructor, Spring falls back to default instantiation and fails with `NoSuchMethodException: AiRetryPolicy.<init>()`.

## Rejected Alternatives

### OpenAiCompatibleProperties constructor binding

`OpenAiCompatibleProperties` is a record with a canonical constructor marked `@ConstructorBinding` and a 5-argument convenience constructor. This remains a possible future sharp edge, but it is not the root cause of this startup failure:

- the stack trace never reaches a configuration-properties binding exception,
- `AiProviderConfig` enables `OpenAiCompatibleProperties`,
- the bottom `Caused by` is specifically `AiRetryPolicy.<init>()`.

No `OpenAiCompatibleProperties` change is justified by this investigation.

### Delete the package-private AiRetryPolicy test constructor

Deleting the two-argument constructor would restore single-constructor inference, but it would remove the test seam used to inject a deterministic sleeper. That is a larger behavioral/testing surface change than needed.

### Make the two-argument constructor public or autowired

The two-argument constructor is a test seam. Autowiring it would require a `LongConsumer` bean in production and would select the wrong production wiring.

## Chosen Fix Strategy

Add `@Autowired` to the public single-argument `AiRetryPolicy(OpenAiCompatibleProperties properties)` constructor. This explicitly selects the production constructor while preserving the package-private test constructor and its deterministic-sleeper seam.

Blast radius: one production file, one annotation import, one constructor annotation.

## Why M6-P5 Did Not Catch This

M6-P5 ran focused service tests and the full backend Maven test suite, but the suite did not include a full Spring Boot context startup test. The existing AI tests instantiate `AiRetryPolicy` manually, including the package-private two-argument constructor in tests, so they exercised retry behavior but not Spring bean construction.

M6-P5.1 adds `ApplicationContextStartupTest` as the 12th cross-phase guardrail so the runtime context has to load in the test suite before future defense-readiness claims.

## Guardrail Test Setup Note

`services/api/src/test/resources/application.yml` intentionally supplies only object-storage test credentials. Because test resources shadow the main `application.yml`, the new full-context guardrail must provide the runtime-equivalent datasource, security, and object-storage properties directly on `@SpringBootTest`.

This is not a production-code fix. It keeps the guardrail on the real Spring Boot context graph while avoiding mocks for `AiRetryPolicy`, `OpenAiCompatibleProperties`, or AI provider beans.

### Runtime Dependency D-口径

`ApplicationContextStartupTest` is intentionally a full Spring Boot context guardrail, not a sliced or mocked unit test. It therefore requires the same local runtime dependencies used by `spring-boot:run`: MySQL reachable at `localhost:3306` with the LabelHub test credentials and MinIO/object storage reachable at `localhost:9000`.

If those local services are not running, `mvn -pl services/api test` may fail on the startup guardrail before application-code wiring is reached. That is an environment precondition for this guardrail, not a product regression. Start the local infra stack (for example, the project docker-compose MySQL/MinIO services or an equivalent local setup) before treating this test as defense-readiness evidence.

After the constructor fix and guardrail properties were in place:

- `mvn -pl services/api -Dtest=ApplicationContextStartupTest test` passed with `1` test, `0` failures, `0` errors.
- `mvn -pl services/api test` passed with `390` tests, `0` failures, `0` errors, `78` skipped.
- `mvn -pl services/api spring-boot:run` logged `Started ApiApplication in 1.578 seconds`.
