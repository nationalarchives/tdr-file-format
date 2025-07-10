import os
import xml.etree.ElementTree as etree
import requests
from datetime import datetime

cdn_url = "https://cdn.nationalarchives.gov.uk/documents"
main_resources = "src/main/resources"

def get_data():
    envelope = etree.Element('soap:Envelope', attrib={'xmlns:soap': 'http://schemas.xmlsoap.org/soap/envelope/',
                                                      'xmlns:xsi': 'http://www.w3.org/2001/XMLSchema-instance',
                                                      'xmlns:xsd': 'http://www.w3.org/2001/XMLSchema'})
    soap_body = etree.SubElement(envelope, 'soap:Body')
    etree.SubElement(soap_body, 'getSignatureFileVersionV1',
                     attrib={'xmlns': 'http://pronom.nationalarchives.gov.uk'})

    return etree.tostring(envelope, encoding='utf8', method='xml').decode('utf-8')


def get_latest_droid_version():
    headers = {'Content-Type': 'text/xml;charset=UTF-8',
               'SOAPAction': 'http://pronom.nationalarchives.gov.uk:getSignatureFileVersionV1In'}
    url = 'http://www.nationalarchives.gov.uk/pronom/service.asmx'

    response = requests.post(url, headers=headers, data=get_data())
    tree = etree.fromstring(response.text)
    namespaces = {'soap': 'http://schemas.xmlsoap.org/soap/envelope/'}
    body = tree.findall("soap:Body", namespaces)[0]
    version = list(list(body)[0])[0]
    return list(version)[0].text


def replace_line(line, property_type, new_version, suffix=""):
    if line.startswith(f"{property_type}.version"):
        each_part = line.split("=")
        conf_version = each_part[1].strip().replace('"', '')
        if conf_version != new_version:
            return f'{each_part[0]}="{new_version}"{suffix}'
        else:
            return line
    else:
        return line


def get_latest_containers_version():
    res = requests.get("https://www.nationalarchives.gov.uk/pronom/container-signature.xml")
    last_modified_string = res.headers.get("Last-Modified")
    date = datetime.strptime(last_modified_string, '%a, %d %b %Y %H:%M:%S GMT')

    return datetime.strftime(date, '%Y%m%d')


def validate_xml(file_name):
    response = requests.get(f"{cdn_url}/{file_name}")
    valid_xml = True
    try:
        etree.fromstring(response.text)
    except:
        valid_xml = False
    return valid_xml and response.status_code == 200


latest_droid_version = get_latest_droid_version()
latest_containers_version = get_latest_containers_version()


if validate_xml(f"DROID_SignatureFile_V{latest_droid_version}.xml"):
    # Download and save droid signature file
    response = requests.get(f"{cdn_url}/DROID_SignatureFile_V{latest_droid_version}.xml")
    response.raise_for_status()
    file_path = os.path.join(main_resources, f"DROID_SignatureFile_V{latest_droid_version}.xml")
    with open(file_path, "wb") as f:
        f.write(response.content)


if validate_xml(f"container-signature-{latest_containers_version}.xml"):
    # Download and save container signature file
    response = requests.get(f"{cdn_url}/container-signature-{latest_containers_version}.xml")
    response.raise_for_status()
    file_path = os.path.join(main_resources, f"container-signature-{latest_containers_version}.xml")
    with open(file_path, "wb") as f:
        f.write(response.content)

