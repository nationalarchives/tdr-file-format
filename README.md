# File format checks

This is the repository for the backend file format check for the [Transfer Digital Records] project.

The file format checks run in AWS Lambda. They run the [DROID] file format tool to extract [PRONOM] IDs.

The Lambda function connects to an AWS EFS file store which contains both DROID and the files to be scanned.

DROID and its file signatures are deployed to EFS by an AWS ECS task.

[Transfer Digital Records]: https://github.com/nationalarchives/tdr-dev-documentation/
[DROID]: https://www.nationalarchives.gov.uk/information-management/manage-information/preserving-digital-records/droid/
[PRONOM]: http://www.nationalarchives.gov.uk/PRONOM/Default.aspx

## Deployment

### DROID and file format signatures

To deploy changes to the Dockerfile (which installs DROID) and the file format signatures, run the "TDR File Format
Build" Jenkins job. This deploys the Docker image to Docker Hub and then runs it as an ECS task.

### File format Lambda

To deploy changes to the Lambda which downloads the file from S3 and runs DROID, run the "TDR File Format Deploy"
Jenkins job.
