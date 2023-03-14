import xml.etree.ElementTree as etree
import requests


def get_data():
    envelope = etree.Element('soap:Envelope', attrib={'xmlns:soap': 'http://schemas.xmlsoap.org/soap/envelope/',
                                                      'xmlns:xsi': 'http://www.w3.org/2001/XMLSchema-instance',
                                                      'xmlns:xsd': 'http://www.w3.org/2001/XMLSchema'})
    soap_body = etree.SubElement(envelope, 'soap:Body')
    etree.SubElement(soap_body, 'getSignatureFileVersionV1',
                     attrib={'xmlns': 'http://pronom.nationalarchives.gov.uk'})

    return etree.tostring(envelope, encoding='utf8', method='xml').decode('utf-8')


headers = {'Content-Type': 'text/xml;charset=UTF-8',
           'SOAPAction': 'http://pronom.nationalarchives.gov.uk:getSignatureFileVersionV1In'}
url = 'http://www.nationalarchives.gov.uk/pronom/service.asmx'

response = requests.post(url, headers=headers, data=get_data())
tree = etree.fromstring(response.text)
namespaces = {'soap': 'http://schemas.xmlsoap.org/soap/envelope/'}
body = tree.findall("soap:Body", namespaces)[0]
version = list(list(body)[0])[0]
latest_version = list(version)[0].text


def replace_line(line):
    if line.startswith("droid.version"):
        each_part = line.split("=")
        conf_version = each_part[1].strip()
        if conf_version != latest_version:
            return f"{each_part[0]}={latest_version}"
        else:
            return line
    else:
        return line


with open("src/main/resources/application.conf", "r+") as conf:
    lines = conf.readlines()
    new_conf = (replace_line(line) for line in lines)
    conf.seek(0)
    conf.write(''.join(list(new_conf)))
