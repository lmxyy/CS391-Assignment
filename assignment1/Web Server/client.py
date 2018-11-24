#!/usr/bin/env python3
from socket import *
import sys
import threading
import time

def request(serverHost,serverPort,fileName):
    ipPort=(serverHost,serverPort)
    clientSocket = socket(AF_INET,SOCK_STREAM)
    clientSocket.connect(ipPort)

    clientSocket.send(('GET /'+fileName+' HTTP/1.1\r\n\r\n').encode('UTF-8'))
    time.sleep(0.1)
    outputdata = ''
    while True:
        message = clientSocket.recv(1024)
        outputdata += message.decode()
        if len(message) < 1024:
            break
    print(outputdata)
    clientSocket.close()    

if __name__ == '__main__':
    if len(sys.argv) != 4:
        raise ValueError("The format must be 'client.py server_host server_port filename'")
    # thread1 = threading.Thread(target = request,args=(sys.argv[1],int(sys.argv[2]),sys.argv[3]),)
    # thread2 = threading.Thread(target = request,args=(sys.argv[1],int(sys.argv[2]),sys.argv[3]),)
    # thread1.start()
    # thread2.start()
    
    request(sys.argv[1],int(sys.argv[2]),sys.argv[3])

