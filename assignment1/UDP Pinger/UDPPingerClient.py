#!/usr/bin/env python3
import socket
import sys
import time

TIME_PERIOD = 5

def ping(host):
    clientSocket = socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
    clientSocket.settimeout(1)
    rttSum,rttMax,rttMin = 0,0,1
    recNum = 0
    for i in range(10):
        try:
            startTime = time.time()
            message = 'Ping %d %f'%(i+1,startTime)
            clientSocket.sendto(message.encode(),(host,12000))
            message,address = clientSocket.recvfrom(1024)
            rtt = time.time()-startTime
            print('Sequence %d %.2f'%(i+1,rtt))
            rttSum += rtt
            rttMax = max(rttMax,rtt)
            rttMin = min(rttMin,rtt)
            recNum += 1
            time.sleep(TIME_PERIOD-rtt)
        except:
            print('Sequence %d Request timed out'%(i+1))
            time.sleep(TIME_PERIOD-1)
    clientSocket.close()
    if recNum == 0:
        print('All timed out.')
    else:
        print('min RTT: %.2f\tmax RTT: %.2f\taverage RTT: %.2f'%(rttMin,rttMax,rttSum/recNum))
    print('package loss rate: %d%%'%((10-recNum)*10))
        

if __name__ == '__main__':
    if len(sys.argv) == 1:
        host = ''
    elif len(sys.argv) == 2:
        host = sys.argv(1)
    else:
        raise ValueError('The format must be "UDPPingerClient.py" or "UDPPingerClient.py <host>"')
    ping(host)
