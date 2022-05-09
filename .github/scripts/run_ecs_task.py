import boto3
import sys

account_number = sys.argv[1]
stage = sys.argv[2]

client = boto3.client('ecs')
ec2_client = boto3.client("ec2")

filtered_security_groups = list(filter(lambda filtered_sg: filtered_sg['GroupName'] == "allow-outbound-https",
                                       ec2_client.describe_security_groups()['SecurityGroups']))
security_groups = [security_group['GroupId'] for security_group in filtered_security_groups]
subnets = [subnet['SubnetId'] for subnet in ec2_client.describe_subnets(Filters=[
    {
        'Name': 'tag:Name',
        'Values': [
            'tdr-efs-private-subnet-backend-checks-efs-0-' + stage,
            'tdr-efs-private-subnet-backend-checks-efs-1-' + stage,
        ]
    },
])['Subnets']]

response = client.run_task(
    cluster="file_format_build_" + stage,
    taskDefinition="file-format-build-" + stage,
    launchType="FARGATE",
    platformVersion="1.4.0",
    networkConfiguration={
        'awsvpcConfiguration': {
            'subnets': subnets,
            'securityGroups': security_groups
        }
    }
)
