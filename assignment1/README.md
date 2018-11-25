# Socket Programming Assignments

[TOC]

## Requirement

* Python 3.x
* getch
* tornada

## Web Server

This assignment is basic socket programming of for TCP connections in Python. 

### Usage

#### Server

```server.py``` is a basic implementation of server via TCP connections. The server is bind to ```localhost:1208```   . Run the following code:

```bash
python ./server.py
```

Then open your browser and provide the corresponding URL, such as:

```
http://localhost:1208/scorer.html
```

```score.html``` is the html file in my directory. Then your browser will get the following message:

```http
HTTP/1.1 200 OK
```

and you are supposed to see the result like this:

![200](/Users/limuyang/Nustore Files/First Half of Junior Year/Computer Networking/assignment/assignment 1/Web Server/200.png)

If you attempt to visit a file that doesn't exist, your browser will get the following message:

```http
HTTP/1.1 404 Not Found
```

and you are supposed to see the result like this:

![404](/Users/limuyang/Nustore Files/First Half of Junior Year/Computer Networking/assignment/assignment 1/Web Server/404.png)

#### Multithread Server

```server_multithread.py``` is the multithread version which s capable of serving multiple requests simultaneously of ```server.py``` using the module ```threading```. The usage is similar to ```server.py```.

#### Client

```client.py``` is a little HTTP client. The client can connect to my server using TCP connection, send an HTTP **GET** request to the server, and display the server response as an output.

The client take command line arguments specifying the server IP address or host name, the port at which the server is listening, and the path at which the requested object is stored at the server. The following is an input command format to run the client:

```bash
python client.py server_host server_port filename
```

For example, if you run my server, then you can run the following code:

```bash
python client.py localhost 1208 adder.js
```

You are supposed to see the following result:

![image-20181011175106422](/Users/limuyang/Library/Application Support/typora-user-images/image-20181011175106422.png)

## UDP Pinger

This assignment is basic socket programming of for UDP in Python. 

### Usage

#### UDP Pinger Server

```UDPPingerServer.py``` is a multithread ping server, which is bind to ```localhost:12000```. It can simulates the packet loss randomly with probability $30\%$. It can also check if an application is up and running and to report one-way packet loss by UDP Heartbeaten packet(it assumes that the application will send a packet every $5$ seconds). Upon receiving the packets, the server calculates the time difference and reports any lost packets(we assume that ). If the server could not receive packets from a specific application for some specific period of time(in my code, it's $30$ seconds), it assumes that the client application has stoped. To run the server, use the following command: 

```bash
python UDPPingerServer.py
```

#### UDP Pinger Client

```UDPPingerClient.py``` is a little application which can send $10$ UDP Heartbeaten packets(with verlocity $5$ seconds a packet). The client waits up to $1$ second for the server to reply, and prints the response message from the server, if any, then calculates the round trip time. If no reply is received within $1$ second, the client assumes that the packet was lost during the transmission across network and prints "Request timed out".  After sending all the $10$ packets,  the client will report the minimum, maximun and average RTTs and calculates the packet loss rate. To run the client,  

 ```bash
python UDPPingerClient.py
 ```

then the client will connect to ```localhost:12000``` or

```bash
python UDPPingerClient.py <server_host>
```

then it will connect to ```server_host:12000```.

### Demo

First, run the server

```
python UDPPingerServer.py
```

Then run the client

```
python UDPPingerClient.py
```

You are supposed to see the result of the server like this:

![heartbeat](/Users/limuyang/Nustore Files/First Half of Junior Year/Computer Networking/assignment/assignment 1/UDP Pinger/heartbeat.png)

and the result of the client like this:

![ping](/Users/limuyang/Nustore Files/First Half of Junior Year/Computer Networking/assignment/assignment 1/UDP Pinger/ping.png)

## Mail Client

This assignment is aimed at implementing a standard protocol using Python.

### Usage

#### Client

```client.py``` is a simple mail client that sends email to any recipient(in my code, the recipient is fixed to limuyang123@sjtu.edu.cn) and the sender is fixed to Muyang Li. The mail server is ```smtp.qq.com:587```. The client add a Secure Sockets Layer (SSL) for authentication and security issues, and can hanle both text(I love computer networks!) and image messages(test.jpg). The client also supports inputing your username and your password with the mask of '*'.

Run the client with

```
python client.py
```

and input your username and password, then you'll see the result like this:

![image-20181011201002722](/Users/limuyang/Library/Application Support/typora-user-images/image-20181011201002722.png)

And I'll receive a mail like this:

![result](/Users/limuyang/Nustore Files/First Half of Junior Year/Computer Networking/assignment/assignment 1/Mail Client/result.png)

## Multi-threaded Web Proxy

This assignment is aimed to implement a simple web proxy server with cache.

![image-20181011203150698](/Users/limuyang/Library/Application Support/typora-user-images/image-20181011203150698.png)

### Usage

#### Basic Proxy

```proxy.py``` is a basic implement of proxy with cache. It functions like the picture above. Every time the browser visits a new web page, the proxy will save the web page in the directory ```ocache```. Next time the browser visits the same web page, the proxy will read from cache instead of request from the website. However, the proxy only sends HTTP 1.0 request messages to the website sever. And the link isn't keep-alive, which means every time the proxy requests a filename, it will build a new socket. After it receives the response, the socket will be closed. Also, the proxy cannot handle **POST** request.

Run the proxy with

```bash
python proxy.py <host>
```

thus the proxy is bind to ```host:1999```.

For example:

```
python proxy.py localhost
```

Then open your browser, and input the following url

```
http://localhost:1999/www.baidu.com
```

then you will see the result like this:

![image-20181011210606146](/Users/limuyang/Library/Application Support/typora-user-images/image-20181011210606146.png)

In the ```ocache``` directory, you'll find a file named ```www.baidu.com```. Then input the url again, the proxy will send the file to the browser, and you'll see the following sentence:

```
Read from cache
```

#### Proxy with Multithread and Asynchrony IO

```proxy2.py``` is a proxy with multithread and asynchrony IO using ```tonado``` api. It can also handle **POST** request. 

The usage is similar to ```proxy.py```:

Run the proxy with

```bash
python proxy2.py <host>
```

thus the proxy is bind to ```host:1999```.

For example:

```
python proxy2.py localhost
```

Then open your browser, and input the following url

```
http://localhost:1999/www.cs.sjtu.edu.cn/~yzhu/
```

then you will see the result like this:

![image-20181011211627479](/Users/limuyang/Library/Application Support/typora-user-images/image-20181011211627479.png)

In the ```cache``` directory, you'll find a directory named ```www.cs.sjtu.edu.cn```. Then input the url again, the proxy will send the directory to the browser, and you'll see the following sentence:

```
read from cache
```

However, if you input 

```
http://localhost:1999/www.baidu.com
```

input your browser, you'll only see 

![image-20181011211917934](/Users/limuyang/Library/Application Support/typora-user-images/image-20181011211917934.png)

I guess why this page is different to the previous one is that ```proxy2.py``` sends HTTP 1.1 request messages to Baidu while ```proxy.py``` sends HTTP 1.0.

