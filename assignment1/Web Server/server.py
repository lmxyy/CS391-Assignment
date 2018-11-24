#!/usr/bin/env python3
from socket import *
import threading
serverSocket = socket(AF_INET,SOCK_STREAM)
serverSocket.bind(('',1208))
serverSocket.listen(5)

while True:
    print('Ready to serve...')
    connectionSocket,addr = serverSocket.accept()
    try:
        message = connectionSocket.recv(4096)
        filename = message.split()[1]
        f = open(filename[1:])
        outputdata = f.readlines()
        connectionSocket.send(b'HTTP/1.1 200 OK\r\n\r\n')
        connectionSocket.send(''.join(outputdata).encode('UTF-8'))
        connectionSocket.close()
    except IOError:
        connectionSocket.send(b'HTTP/1.1 404 Not Found\r\n\r\n')
        connectionSocket.close()
    except Exception:
        connectionSocket.close()

serverSocket.close()
