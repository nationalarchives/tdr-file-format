# File format checks

This is the repository for the backend file format check for the [Transfer Digital Records] project

[Transfer Digital Records]: https://github.com/nationalarchives/tdr-dev-documentation/

## Deployment

### Siegfried and file format signatures

To deploy changes to the Dockerfile (which installs Siegfried) and the file format signatures, run the "TDR File Format
Build" Jenkins job. This deploys the Docker image to Docker Hub and then runs it as an ECS task.

### File format Lambda

To deploy changes to the Lambda which downloads the file from S3 and runs Siegfried, run the "TDR File Format Deploy"
Jenkins job.
