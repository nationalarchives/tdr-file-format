# File format checks

This is the repository for the backend file format check for the [Transfer Digital Records] project.

The file format checks run in AWS Lambda. They run the [DROID] file format tool to extract [PRONOM] IDs.

The Lambda function downloads the file passed as input to the lambda local file system. 

The function then calls the Droid API against this file to extract the [PRONOM] ids 

[Transfer Digital Records]: https://github.com/nationalarchives/tdr-dev-documentation/
[DROID]: https://www.nationalarchives.gov.uk/information-management/manage-information/preserving-digital-records/droid/
[PRONOM]: http://www.nationalarchives.gov.uk/PRONOM/Default.aspx
[deploy GitHub actions job]: https://github.com/nationalarchives/tdr-file-format/actions/workflows/deploy.yml

## Deployment

### DROID and file format signatures

Droid is contained within the `"uk.gov.nationalarchives" % "droid-api"` dependency. 
The binary and container signature files are downloaded when the lambda is first started. 
The versions of the signature files are stored in the `DROID_SIGNATURE_VERSION` and `CONTAINER_SIGNATURE_VERSION` environment variables. 
To update to a new version of these signature files, we need to update the values in the environment variables.

### File format Lambda

To deploy changes to the Lambda which downloads the file from S3 and runs DROID, run the [deploy GitHub actions job].
