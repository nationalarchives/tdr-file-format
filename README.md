# File format checks

This is the repository for the backend file format check for the [Transfer Digital Records] project.

The file format checks run in AWS Lambda. They run the [DROID] file format tool to extract [PRONOM] IDs.

The Lambda function downloads the file passed as input to the lambda local file system. 

The function then calls the Droid API against this file to extract the [PRONOM] ids. 

[Transfer Digital Records]: https://github.com/nationalarchives/tdr-dev-documentation/
[DROID]: https://www.nationalarchives.gov.uk/information-management/manage-information/preserving-digital-records/droid/
[PRONOM]: http://www.nationalarchives.gov.uk/PRONOM/Default.aspx
[deploy GitHub actions job]: https://github.com/nationalarchives/tdr-file-format/actions/workflows/deploy.yml

## Deployment

### DROID and file format signatures

Droid is contained within the `"uk.gov.nationalarchives" % "droid-api"` dependency. 

The binary signature file is downloaded during the build process ([build.yml](.github%2Fworkflows%2Fbuild.yml)). 

The container signature file is included as part of the DROID API.

To build the lambda function with the binary signature file, it carries out the following steps:
* Pick the latest version of the DROID binary file from the [application.conf](src%2Fmain%2Fresources%2Fapplication.conf)
* Download the file and put it in the `src/main/resources` folder.
  ```
  curl -L "https://cdn.nationalarchives.gov.uk/documents/DROID_SignatureFile_V${DROID_VERSION}.xml" -o "src/main/resources/DROID_SignatureFile_V${DROID_VERSION}.xml"
  ```
* Build the artifact and deploy it to AWS.
The lambda function will pick up the `DROID_Signature*` file from the resources folder when it starts up. If the file is not present then it will throw an error.

### File format Lambda

To deploy changes to the Lambda which downloads the file from S3 and runs DROID, run the [deploy GitHub actions job].
