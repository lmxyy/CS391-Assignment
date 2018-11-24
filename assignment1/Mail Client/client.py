#!/usr/bin/env python3
import base64
import getch
import sys
import ssl
from socket import *

def dataInput(msg=None,mask = None):
    if msg != None:
        sys.stdout.write(msg)
        sys.stdout.flush()
    chars = []
    while True:
        newChar = getch.getch()
        if newChar in '\3\r\n':
            print('')
            if newChar in '\3':
                chars = []
            break
        elif newChar.encode() == b'\x7f':
            if len(chars) > 0:
                chars.pop(-1)
            sys.stdout.write('\b \b')
            sys.stdout.flush()
        else:
            chars.append(newChar)
            if mask != None:
                sys.stdout.write(mask)
            else:
                sys.stdout.write(newChar)
            sys.stdout.flush()
    return ''.join(chars)

msg = "\r\n I love computer networks!"
endmsg = "\r\n.\r\n"
mailserver = ('smtp.qq.com',587)
authMsg = 'AUTH LOGIN\r\n'
username = dataInput('username: ')
password = dataInput('password: ','*')

header = 'From: "Muyang Li" <%s>\r\nTo: "Muyang Li" <%s>\r\nSubject: Test\r\n'%('lmxyy1999@foxmail.com', 'limuyang123@sjtu.edu.cn')
mimeHeader = '\r\n'.join(['MIME-Version: 1.0',
                          'Content-Type: multipart/mixed; boundary="BOUNDARY"',
                          '\r\n'])
textHeader = '\r\n'.join(['\r\n',
                          '--BOUNDARY',
                          'Content-Type: text/plain; charset="UTF-8"',
                          'Content-transfer-encoding: 7bit',
                          '\r\n'])
imgHeader = '\r\n'.join(['\r\n',
                         '--BOUNDARY',
                         'Content-Type: image/jpeg; name=test.jpg',
                         'Content-Transfer-Encoding: base64',
                         '\r\n'])

clientSocket = socket(AF_INET,SOCK_STREAM)
clientSocket.connect(mailserver)

def sendAndRecv(msg = None,returnCode=None,needPrint = True):
    if msg != None:
        clientSocket.send(msg)
    if needPrint:
        recv = clientSocket.recv(1024)
        print(recv)
    if returnCode != None:
        if recv[:3].decode() != str(returnCode):
            print('%d reply not received from server.'%(returnCode))

sendAndRecv(None,220)
sendAndRecv(b'HELO Alice\r\n',250)
sendAndRecv(b'STARTTLS\r\n',220)
clientSocket = ssl.wrap_socket(clientSocket)
sendAndRecv(b'HELO Alice\r\n',250)

sendAndRecv(b'AUTH LOGIN\r\n',334)
sendAndRecv(base64.b64encode(username.encode())+b'\r\n',334)
sendAndRecv(base64.b64encode(password.encode())+b'\r\n',235)

sendAndRecv(('MAIL FROM: <%s>\r\n'%('lmxyy1999@foxmail.com')).encode(),250)
sendAndRecv(('RCPT TO: <%s>\r\n'%('limuyang123@sjtu.edu.cn')).encode(),250)

sendAndRecv('DATA\r\n'.encode(),354)
sendAndRecv(header.encode(),None,False)
sendAndRecv(mimeHeader.encode(),None,False)

sendAndRecv(textHeader.encode(),None,False)
sendAndRecv(msg.encode(),None,False)

sendAndRecv(imgHeader.encode(),None,False)
with open('test.jpg','rb') as f:
    data = base64.b64encode(f.read())
sendAndRecv(data,None,False)

mimeEnd = '\r\n\r\n--BOUNDARY--\r\n\r\n'
sendAndRecv(mimeEnd.encode(),None,False)

sendAndRecv(endmsg.encode(),250)
sendAndRecv('QUIT\r\n'.encode(),221)

clientSocket.close()
