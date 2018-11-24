#!/usr/bin/env python3
import random
import threading
import time
from socket import *

eps = 1e-3
CHECK_PERIOD = 5
CHECK_TIMEOUT = 30
client = {}
messagePool = {}

def hand(address):
    while True:
        lastTime = client.get(address,-1)
        if lastTime != -1 and time.time()-lastTime >= CHECK_TIMEOUT+eps:
            client[address] = -1
            print('The application shuts down.')
            break
        message = messagePool.get(address,-1)
        # print(message)
        if message == -1 or len(message) == 0:
            continue
        message = message.pop(0)
        # print(message)
        serverSocket.sendto(message,address)
        message = message.decode()
        sendTime = float(message.split()[-1])
        if lastTime != -1:
            print('time interval: %.2f\tlost packets: %d'%(sendTime-lastTime,int((sendTime-lastTime+eps)/CHECK_PERIOD)-1))
            client[address] = sendTime
        else:
            print('An application starts.')
            client[address] = sendTime

def work():
    lastTime = -1
    while True:
        rand = random.randint(0,10)
        message,address = serverSocket.recvfrom(1024)
        if rand < 4:
            continue
        if client.get(address,-1) == -1:
            if messagePool.get(address) == None:
                messagePool[address] = [message]
            else:
                messagePool[address].append(message)
            t = threading.Thread(target = hand,args = (address,))
            t.start()
        else:
            messagePool[address].append(message)
            

if __name__ == '__main__':
    serverSocket = socket(AF_INET,SOCK_DGRAM)
    serverSocket.bind(('',12000))
    work()
    serverSocket.close()
    
