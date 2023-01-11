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
The binary and container signature files are downloaded when the lambda is first started. To get the signature files, it carries out the following steps. 
* Checks to see if the `/tmp` directory contains container-* or DROID_Signature*
* If they exist, use those to create the Droid instance.
* If they don't exist:
    * Call http://www.nationalarchives.gov.uk/pronom/container-signature.xml and get the last modified date
    * Convert this to YYYYMMDD and use that for the container signature version
    * Call the SOAP endpoint at http://www.nationalarchives.gov.uk/pronom/service.asmx
    * This returns the latest Droid signature file version.
    * Download both these files from the TNA CDN as before.

### File format Lambda

To deploy changes to the Lambda which downloads the file from S3 and runs DROID, run the [deploy GitHub actions job].
