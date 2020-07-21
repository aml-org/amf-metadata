# AMF-Metadata
---
This repository holds:
- AMF Canonical Web Api Spec dialect
- AMF Vocabularies
- Canonical Web Api Spec Transform
- Dialect and Vocabulary exporters

## Artifacts

### AMF Vocabulary

This _resource only_ artifact holds the canonical web api spec dialect and AMF's vocabularies.

Artifact group: com.github.amlorg
Artifact name: amf-vocabulary
Depends on: None. This jar doesn't have dependencies with any other project. 

##### How to read a dialect or vocabulary from the jar?

```java
public class Main {

    public static void main(String[] args) {
        InputStream stream = Main.class.getClassLoader().getResourceAsStream("dialects/canonical_webapi_spec.yaml");
        String content = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining());
        System.out.println(content);
    }
}
```

### AMF Transform

This artifact contains AMF model transformations. Currently the only one it has is the Canonical WebApi Spec transform.

Artifact group: com.github.amlorg
Artifact name: amf-transform
Depends on: https://github.com/aml-org/amf