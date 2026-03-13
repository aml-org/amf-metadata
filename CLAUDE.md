# amf-metadata

**Repository**: https://github.com/aml-org/amf-metadata

## Release Process
Follows the regular release process (see ~/mulesoft/CLAUDE.md).

## Release Position
- **Fourth** in the RC/release line
- Depends on **amf** RC/release

## Files & Locations

### Version Files
- `transform/dependencies.properties` - contains dependencies
- `versions.yaml` - contains project versions

### Dependencies
- amf (update to RC version for RC, release version for release)
- amf-rdf (part of amf-aml)

## Artifacts Published
This project publishes 2 artifacts:
- amf-transform
- amf-vocabulary

## Jenkins Configuration

### Stages to Modify
- **Building stages**: N/A
- **Publishing stages**: `Publish Vocabulary Artifact`, `Publish Transform Artifact`

### Jenkinsfile Updates
- **For RC**: Add `"release/*"` to branch list in `Publish Vocabulary Artifact` and `Publish Transform Artifact` stages
- **For Release**: Remove `"release/*"` from branch list in publish stages

## Release Steps

### Publishing RC (x.y.z-RC.r)
1. Update transform/dependencies.properties with amf RC version from previous step
2. From develop: `git checkout -b release/x.y.z`
3. Edit Jenkinsfile: add `"release/*"` to both Publish stages
4. Edit versions.yaml: update version to `x.y.z-RC.r`
5. Commit: `git commit -m "Publish x.y.z-RC.r"`
6. Push: `git push -u origin release/x.y.z`

### Publishing Release (x.y.z)
1. Update transform/dependencies.properties with amf release version
2. Follow standard three-PR process (setup → master → develop)
3. After merging to master, manually tag: `git tag x.y.z origin/master && git push origin x.y.z`

### Publishing Hotfix (x.y.z-n)
1. Checkout/create `support/x.y.z` branch from tag
2. Cherry-pick required commits
3. Update version in versions.yaml to `x.y.z-n`
4. Check if amf hotfix is also needed and update dependency
5. Ensure Jenkinsfile has `support/*` in both Publish stages (for first HF only)
6. Push and create PR to merge into `support/x.y.z`

## Dependencies Impact
After releasing amf-metadata, update the version in:
- amf-service (post-release action - update amf-transform version)
- amf-tckutor (post-release action)
