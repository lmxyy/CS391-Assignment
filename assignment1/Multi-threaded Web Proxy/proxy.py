#!/usr/bin/env python3
from socket import *
import sys
import time
import os

bufferSize = 1024

if len(sys.argv) <= 1:
    print('Usage: "ProxyServer.py server_ip"\n[server_ip: It is the IP address of proxy server.]')
    sys.exit(2)
# Create a server socket, bind it to a port and start listening
if not os.path.exists('ocache'):
    os.mkdir('ocache')
tcpSerSock = socket(AF_INET,SOCK_STREAM)
tcpSerSock.bind(('',1999))
tcpSerSock.listen(5)

while True:
    # Strat receiving data from the client
    print('Ready to serve...')
    time.sleep(1)
    tcpCliSock,addr = tcpSerSock.accept()
    print('Received a connection from:',addr)
    # message = tcpCliSock.recv(bufferSize)
    message = b''
    # while True:
    #     msg = tcpCliSock.recv(bufferSize)
    #     message += msg
    #     if len(msg) < bufferSize:
    #         break
    # print('Message:',message)
    message = tcpCliSock.recv(bufferSize)
    message = message.decode()
    print(message)
    if len(message.split()) < 2:
        continue
    filename = message.split()[1].partition('/')[2]
    # print('filename:',filename)
    fileExist = False
    try:
        # Check whether the file exist in the cache
        f = open('ocache/'+filename,"r")
        outputdata = f.read()
        fileExist = True
        tcpCliSock.send(outputdata.encode())
        print('Read from cache')
    # Error handling for file not found in cache
    except IOError:
        if not fileExist:
            # Create a socket on the proxyserver
            c = socket(AF_INET,SOCK_STREAM)
            hostn = filename.partition('/')[0]
            print('hostn:',hostn)
            try:
                # Connect to socket to port 80
                c.connect((hostn,80))
                c.send(('GET /%s HTTP/1.0\r\n\r\n'%(filename.partition('/')[2])).encode())
                # print('message:',('GET /%s HTTP/1.1\r\n\r\n'%(filename.partition('/')[2])).encode())
                time.sleep(0.1)
                # Read the response into buffer
                outputdata = b''
                while True:
                    msg = c.recv(bufferSize)
                    outputdata += msg
                    if len(msg) < bufferSize:
                        break
                # Also send the response in the buffer to client socket and the corresponding file in the cache
                tcpCliSock.send(outputdata)
                print('outputdata:',outputdata)
                if outputdata.decode().split()[1] == '200':
                    dirname = os.path.dirname('ocache/'+filename)
                    if not os.path.exists(dirname):
                        os.makedirs(dirname)
                    cacheFile = open('ocache/'+filename,"w")
                    cacheFile.write(outputdata.decode())
                    cacheFile.flush()
                
            except Exception as e:
                print(e)
                print('Illegal request')
            c.close()
        else:
            # HTTP response
            tcpCliSock.send(b'HTTP/1.1 404 Not Found\r\n\r\n')
    # Close the client and the server sockets
    tcpCliSock.close()
tcpSerSock.close()
