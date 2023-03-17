import xml.etree.ElementTree as etree
import requests
from datetime import datetime


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
    response = requests.get(f"https://cdn.nationalarchives.gov.uk/documents/{file_name}")
    valid_xml = True
    try:
        etree.fromstring(response.text)
    except:
        valid_xml = False
    return valid_xml and response.status_code == 200


latest_droid_version = get_latest_droid_version()
latest_containers_version = get_latest_containers_version()

with open("src/main/resources/application.conf", "r+") as conf:
    lines = conf.readlines()
    conf_replaced = ''
    if validate_xml(f"DROID_SignatureFile_V{latest_droid_version}.xml"):
        conf_replaced = (replace_line(line, 'droid', latest_droid_version, "\n") for line in lines)
    if validate_xml(f"container-signature-{latest_containers_version}.xml"):
        conf_replaced = (replace_line(line, 'containers', latest_containers_version) for line in droid_replaced)
    if conf_replaced != '':
        conf.seek(0)
        conf.write(''.join(list(conf_replaced)))
