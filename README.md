# File format checks

This is the repository for the backend file format check for the [Transfer Digital Records] project.

The file format checks run in AWS Lambda. They run the [DROID] file format tool to extract [PRONOM] IDs.

The Lambda function connects to an AWS EFS file store which contains both DROID and the files to be scanned.

DROID and its file signatures are deployed to EFS by an AWS ECS task.

[Transfer Digital Records]: https://github.com/nationalarchives/tdr-dev-documentation/
[DROID]: https://www.nationalarchives.gov.uk/information-management/manage-information/preserving-digital-records/droid/
[PRONOM]: http://www.nationalarchives.gov.uk/PRONOM/Default.aspx
[Run ECS Task]: https://github.com/nationalarchives/tdr-file-format/actions/workflows/run.yml
[build GitHub actions job]: https://github.com/nationalarchives/tdr-file-format/actions/workflows/build.yml
[deploy GitHub actions job]: https://github.com/nationalarchives/tdr-file-format/actions/workflows/deploy.yml

## Deployment

### DROID and file format signatures

The [build GitHub actions job] will run when a pull request is merged to master. 
The job will check to see if there are any changes to the Dockerfile. 
If there are, it will build a new version of the docker image and push it to ECS.

To run the new image, run the [Run ECS Task] workflow with the latest docker image version and the environment you are deploying to.

### File format Lambda

To deploy changes to the Lambda which downloads the file from S3 and runs DROID, run the [deploy GitHub actions job].

### Adding new environment variables to the tests
The environment variables in the deployed lambda are encrypted using KMS and then base64 encoded. These are then decoded in the lambda. Because of this, any variables in `src/test/resources/application.conf` which come from environment variables in `src/main/resources/application.conf` need to be stored base64 encoded. There are comments next to each variable to say what the base64 string decodes to. If you want to add a new variable you can run `echo -n "value of variable" | base64 -w 0` and paste the output into the test application.conf